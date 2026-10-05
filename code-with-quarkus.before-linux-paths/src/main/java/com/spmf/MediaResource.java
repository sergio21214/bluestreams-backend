package com.spmf;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

import com.spmf.dto.HomeResponse;
import com.spmf.HomeRootResponse;
import com.spmf.dto.SeasonDto;
import com.spmf.dto.SeriesDto;

import jakarta.ws.rs.core.MediaType;

import java.util.*;
import java.util.stream.Collectors;


@Path("/media")
public class MediaResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<MediaItem> getMedia() {

        return MediaItem.listAll();
    }

//    @GET
//    @Path("/home")
//    @Produces(MediaType.APPLICATION_JSON)
//    public HomeRootResponse home() {
//
//        List<MediaItem> all =
//                MediaItem.listAll();
//
//        HomeRootResponse response =
//                new HomeRootResponse();
//
//        // HERO
//
//        response.hero =
//                all.stream()
//                        .filter(m -> m.poster != null)
//                        .findFirst()
//                        .orElse(null);
//
//        // MOVIES
//
//        response.movies =
//                all.stream()
//                        .filter(m ->
//                                "movie".equals(
//                                        m.mediaType
//                                )
//                        )
//                        .limit(20)
//                        .toList();
//
//        // SERIES
//
//        response.series =
//                all.stream()
//                        .filter(m ->
//                                "episode".equals(
//                                        m.mediaType
//                                )
//                        )
//                        .distinct()
//                        .limit(20)
//                        .toList();
//
//        // RECENT
//
//        response.recent =
//                all.stream()
//                        .sorted((a, b) ->
//                                b.id.compareTo(a.id)
//                        )
//                        .limit(20)
//                        .toList();
//
//        // CONTINUE WATCHING
//        // frontend handles this for now
//
//        response.continueWatching =
//                List.of();
//
//        return response;
//    }

    @GET
    @Path("/series/{title}")
    @Produces(MediaType.APPLICATION_JSON)
    public SeriesDto getSeries(
            @PathParam("title")
            String title
    ) {

        String decodedTitle =
                java.net.URLDecoder.decode(
                        title,
                        java.nio.charset.StandardCharsets.UTF_8
                );

        List<MediaItem> episodes =
                MediaItem.list(
                        "seriesTitle = ?1 order by seasonNumber asc, episodeNumber asc",
                        decodedTitle
                );

        Map<Integer, List<MediaItem>> groupedSeasons =
                episodes.stream()
                        .collect(
                                Collectors.groupingBy(
                                        item ->
                                                item.seasonNumber != null
                                                        ? item.seasonNumber
                                                        : 0,
                                        TreeMap::new,
                                        Collectors.toList()
                                )
                        );

        SeriesDto series =
                new SeriesDto();

        series.title =
                decodedTitle;

        List<SeasonDto> seasons =
                groupedSeasons.entrySet()
                        .stream()
                        .map(entry -> {

                            SeasonDto season =
                                    new SeasonDto();

                            season.seasonNumber =
                                    entry.getKey();

                            season.episodes =
                                    entry.getValue()
                                            .stream()
                                            .sorted(
                                                    Comparator
                                                            .comparing(
                                                                    (MediaItem item) ->
                                                                            item.episodeNumber != null
                                                                                    ? item.episodeNumber
                                                                                    : 9999
                                                            )
                                            )
                                            .toList();

                            return season;
                        })
                        .toList();

        series.seasons =
                seasons;

        return series;
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public MediaItem byId(
            @jakarta.ws.rs.PathParam("id")
            Long id
    ) {

        return MediaItem.findById(id);
    }

    @GET
    @Path("/{id}/episodes")
    @Produces(MediaType.APPLICATION_JSON)
    public List<MediaItem> getEpisodes(
            @PathParam("id") Long id
    ) {

        MediaItem media =
                MediaItem.findById(id);

        if (media == null) {
            return List.of();
        }

        // If not a series episode,
        // return only itself

        if (
                media.seriesTitle == null
                        || media.seriesTitle.isBlank()
        ) {

            return List.of(media);
        }

        return MediaItem.list(
                        "seriesTitle",
                        media.seriesTitle
                ).stream()

                .map(item -> (MediaItem) item)

                .sorted(
                        Comparator
                                .comparing(
                                        (MediaItem m) ->
                                                m.seasonNumber != null
                                                        ? m.seasonNumber
                                                        : 0
                                )
                                .thenComparing(
                                        m ->
                                                m.episodeNumber != null
                                                        ? m.episodeNumber
                                                        : 0
                                )
                )

                .toList();
    }

    @GET
    @Path("/home")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> home() {

        List<MediaItem> all =
                MediaItem.listAll();

        Map<String, Object> response =
                new HashMap<>();

        List<Map<String, Object>> rows =
                new ArrayList<>();

        // FEATURED

        MediaItem featured =
                all.stream()
                        .filter(m ->
                                m.poster != null
                        )
                        .findFirst()
                        .orElse(
                                all.isEmpty()
                                        ? null
                                        : all.get(0)
                        );

        response.put(
                "featured",
                featured
        );

        // MOVIES

        List<MediaItem> movies =
                all.stream()
                        .filter(m ->
                                "movie".equals(
                                        m.mediaType
                                )
                        )
                        .toList();

        Map<String, Object> moviesRow =
                new HashMap<>();

        moviesRow.put(
                "title",
                "Movies"
        );

        moviesRow.put(
                "items",
                movies
        );

        rows.add(moviesRow);

        // TV SHOWS

        List<MediaItem> series =
                all.stream()
                        .filter(m ->
                                "episode".equals(
                                        m.mediaType
                                )
                        )
                        .toList();

        Map<String, Object> seriesRow =
                new HashMap<>();

        seriesRow.put(
                "title",
                "TV Shows"
        );

        seriesRow.put(
                "items",
                series
        );

        rows.add(seriesRow);

        // RECENT

        List<MediaItem> recent =
                all.stream()
                        .sorted((a, b) ->
                                b.id.compareTo(a.id)
                        )
                        .limit(20)
                        .toList();

        Map<String, Object> recentRow =
                new HashMap<>();

        recentRow.put(
                "title",
                "Recently Added"
        );

        recentRow.put(
                "items",
                recent
        );

        rows.add(recentRow);

        response.put(
                "rows",
                rows
        );

        return response;
    }

    @GET
    @Path("/search")
    public List<MediaItem> search(
            @QueryParam("q") String query
    ) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        return MediaItem.list(
                "lower(title) like ?1 " +
                        "or lower(seriesTitle) like ?1",
                "%" + query.toLowerCase() + "%"
        );
    }

    @GET
    @Path("/movies")
    public List<MediaItem> searchMovies() {
        return MediaItem.list(
                "mediaType",
                "movie"
        );
    }

    @GET
    @Path("/series")
    public List<MediaItem> searchSeries() {
        return MediaItem.list(
                "mediaType",
                "episode"
        );
    }

    @GET
    @Path("/music")
    public List<MediaItem> searchMusic() {
        return MediaItem.list(
                "mediaType",
                "music"
        );
    }
//    @GET
//    @Path("/series/{title}")
//    public List<MediaItem> seriesEpisodes(
//            @PathParam("title")
//            String title
//    ) {
//
//        return MediaItem.list(
//                "seriesTitle",
//                title
//        );
//    }
}
