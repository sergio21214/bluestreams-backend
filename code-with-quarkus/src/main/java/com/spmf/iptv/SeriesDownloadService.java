package com.spmf.iptv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spmf.iptv.account.IptvAccount;
import com.spmf.iptv.account.IptvAccountStore;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Resolves an Xtream account's series metadata and queues one download job per
 * episode of the requested season (or all seasons when {@code season} is null).
 */
@ApplicationScoped
public class SeriesDownloadService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Inject
    IptvAccountStore accountStore;

    @Inject
    DownloadService downloadService;

    @Inject
    ObjectMapper mapper;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private record Credentials(
            String server,
            String username,
            String password
    ) {
    }

    public DownloadBatch start(SeriesDownloadRequest request) throws Exception {

        if (request == null
                || request.accountId == null
                || request.seriesId == null) {
            throw new IllegalArgumentException(
                    "AccountId and SeriesId are required"
            );
        }

        if (request.season != null && request.season < 0) {
            throw new IllegalArgumentException("Season must be zero or greater");
        }

        IptvAccount account = accountStore.find(request.accountId);

        if (account == null) {
            throw new IllegalArgumentException("IPTV account not found");
        }

        Credentials credentials = parseCredentials(account.url);

        JsonNode root = fetchSeriesInfo(
                credentials,
                request.seriesId
        );

        JsonNode episodesNode = root.path("episodes");

        if (!episodesNode.isObject() || episodesNode.isEmpty()) {
            throw new IllegalArgumentException(
                    "The provider returned no episodes for this series"
            );
        }

        String providerName = root.path("info").path("name").asText("");

        String seriesName =
                request.seriesTitle != null
                        && !request.seriesTitle.isBlank()
                        ? request.seriesTitle.trim()
                        : (providerName.isBlank()
                        ? "Series " + request.seriesId
                        : providerName);

        String safeSeriesName = safeName(seriesName, "Series");

        List<DownloadRequest> items = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> seasons =
                episodesNode.fields();

        while (seasons.hasNext()) {

            Map.Entry<String, JsonNode> seasonEntry = seasons.next();

            Integer seasonNumber = parseInt(seasonEntry.getKey());

            if (seasonNumber == null) {
                continue;
            }

            if (request.season != null
                    && !request.season.equals(seasonNumber)) {
                continue;
            }

            JsonNode episodeArray = seasonEntry.getValue();

            if (!episodeArray.isArray()) {
                continue;
            }

            for (JsonNode episode : episodeArray) {

                long episodeId = episode.path("id").asLong(0);

                if (episodeId <= 0) {
                    continue;
                }

                String extension = cleanExtension(
                        episode.path("container_extension").asText("mp4")
                );

                int episodeNumber =
                        episode.path("episode_num").asInt(0);

                String episodeTitle =
                        episode.path("title").asText("");

                if (episodeTitle.isBlank()) {
                    episodeTitle = "Episode " + episodeNumber;
                }

                String filename = buildFilename(
                        safeSeriesName,
                        seasonNumber,
                        episodeNumber,
                        episodeTitle,
                        extension
                );

                DownloadRequest item = new DownloadRequest();
                item.url = buildEpisodeUrl(
                        credentials,
                        episodeId,
                        extension
                );
                item.filename = filename;
                item.folder = buildFolder(
                        request.folder,
                        safeSeriesName,
                        seasonNumber,
                        request.season
                );
                item.subtitles = readSubtitles(episode);

                items.add(item);
            }
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException(
                    request.season == null
                            ? "No episodes found for this series"
                            : "No episodes found for season " + request.season
            );
        }

        String batchFolder =
                request.folder != null && !request.folder.isBlank()
                        ? request.folder
                        : (request.season != null
                        ? "Series/" + safeSeriesName + "/Season " + request.season
                        : "Series/" + safeSeriesName);

        Log.infof(
                "Resolved Xtream series %d: %d episode(s), folder=%s",
                request.seriesId,
                items.size(),
                batchFolder
        );

        return downloadService.startBatch(batchFolder, items);
    }

    private JsonNode fetchSeriesInfo(
            Credentials credentials,
            Long seriesId
    ) throws Exception {

        String url = credentials.server()
                + "/player_api.php"
                + "?username=" + encode(credentials.username())
                + "&password=" + encode(credentials.password())
                + "&action=get_series_info"
                + "&series_id=" + seriesId;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(45))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Xtream provider returned HTTP " + response.statusCode()
            );
        }

        JsonNode root = mapper.readTree(response.body());

        if (root == null || root.isMissingNode()) {
            throw new IllegalStateException(
                    "Xtream provider returned an empty response"
            );
        }

        return root;
    }

    private Credentials parseCredentials(String rawUrl) {

        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("IPTV account has no URL");
        }

        URI uri;

        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid IPTV account URL");
        }

        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid IPTV account URL");
        }

        String username = null;
        String password = null;
        String query = uri.getRawQuery();

        if (query != null) {
            for (String part : query.split("&")) {
                int separator = part.indexOf('=');

                if (separator <= 0) {
                    continue;
                }

                String key = URLDecoder.decode(
                        part.substring(0, separator),
                        StandardCharsets.UTF_8
                );
                String value = URLDecoder.decode(
                        part.substring(separator + 1),
                        StandardCharsets.UTF_8
                );

                if ("username".equalsIgnoreCase(key)) {
                    username = value;
                } else if ("password".equalsIgnoreCase(key)) {
                    password = value;
                }
            }
        }

        if (username == null || password == null) {
            throw new IllegalArgumentException(
                    "IPTV account is not an Xtream URL"
            );
        }

        String server = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "");

        return new Credentials(server, username, password);
    }

    private String buildEpisodeUrl(
            Credentials credentials,
            long episodeId,
            String extension
    ) {
        return credentials.server()
                + "/series/" + encode(credentials.username())
                + "/" + encode(credentials.password())
                + "/" + episodeId
                + "." + extension;
    }

    private String buildFolder(
            String requestedFolder,
            String safeSeriesName,
            int seasonNumber,
            Integer requestedSeason
    ) {
        if (requestedFolder != null && !requestedFolder.isBlank()) {
            return requestedFolder;
        }

        if (requestedSeason != null) {
            return "Series/" + safeSeriesName + "/Season " + seasonNumber;
        }

        return "Series/" + safeSeriesName;
    }

    private String buildFilename(
            String seriesName,
            int seasonNumber,
            int episodeNumber,
            String episodeTitle,
            String extension
    ) {
        String title = DownloadService.sanitize(episodeTitle);

        String filename = String.format(
                "%s - S%02dE%02d - %s.%s",
                seriesName,
                seasonNumber,
                episodeNumber,
                title,
                extension
        );

        return truncate(filename, 180);
    }

    private List<DownloadRequest.Subtitle> readSubtitles(JsonNode episode) {

        JsonNode array = episode.path("subtitles");

        if (!array.isArray()) {
            array = episode.path("info").path("subtitles");
        }

        if (!array.isArray()) {
            return null;
        }

        List<DownloadRequest.Subtitle> subtitles = new ArrayList<>();

        for (JsonNode node : array) {

            String url = node.path("url").asText("");

            if (url.isBlank()) {
                continue;
            }

            DownloadRequest.Subtitle subtitle =
                    new DownloadRequest.Subtitle();
            subtitle.lang = node.path("language").asText(
                    node.path("lang").asText("und")
            );
            subtitle.url = url;
            subtitles.add(subtitle);
        }

        return subtitles.isEmpty() ? null : subtitles;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String cleanExtension(String value) {
        String cleaned = value == null
                ? ""
                : value.replaceAll("[^A-Za-z0-9]", "");
        return cleaned.isBlank() ? "mp4" : cleaned;
    }

    private String safeName(String value, String fallback) {
        String cleaned = DownloadService.sanitize(value);
        return cleaned == null || cleaned.isBlank() ? fallback : cleaned;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
