package com.spmf.iptv;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads media to this machine's download directory, in the background.
 *
 * The Android app only ever sees a job id and a filename; the transfer itself runs
 * here so the device never has to carry the file.
 *
 * Xtream notes that matter:
 *  - Every request needs a browser-like User-Agent, otherwise nginx answers 403.
 *  - Stream URLs 302-redirect from https to a plain-http load balancer, so redirects
 *    must be followed across protocols ({@link HttpClient.Redirect#ALWAYS}).
 */
@ApplicationScoped
public class DownloadService {

    /**
     * Where downloads land. Override with the {@code BLUESREAMS_DOWNLOAD_DIR}
     * environment variable if your mount spells the folder differently.
     */
    private static final String DEFAULT_DOWNLOAD_DIR = "/mnt/bluestreams/descargas/iptvdownloads";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36";

    private static final int BUFFER_SIZE = 256 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final Map<String, DownloadJob> jobs = new ConcurrentHashMap<>();

    private final ExecutorService pool = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "iptv-download");
        thread.setDaemon(true);
        return thread;
    });

    // ------------------------------------------------------------------ API

    /**
     * Queue a download. Returns immediately with a job the caller can poll.
     */
    public DownloadJob start(DownloadRequest request) throws IOException {
        URI uri = URI.create(request.url.trim());

        if (!uri.isAbsolute() || uri.getScheme() == null) {
            throw new IllegalArgumentException(
                    "Url must be absolute (http/https), got: " + request.url
            );
        }

        String filename = sanitize(pickFilename(request, uri));
        String folder = sanitizeFolder(request.folder);

        Path target = resolveDownloadDir(folder).resolve(filename);

        DownloadJob job = new DownloadJob(UUID.randomUUID().toString(), request.url, filename);
        job.folder = folder;
        job.path = target.toString();
        jobs.put(job.jobId, job);

        Log.info("Download queued job=" + job.jobId + " -> " + target);

        pool.submit(() -> run(job, uri, target, request));

        return job;
    }

    public DownloadJob status(String jobId) {
        return jobs.get(jobId);
    }

    public List<Map<String, Object>> list() {
        Path dir = resolveDownloadDir(null);

        List<Map<String, Object>> files = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    continue;
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", entry.getFileName().toString());
                item.put("size", Files.size(entry));
                item.put("modified", Files.getLastModifiedTime(entry).toMillis());
                files.add(item);
            }
        } catch (IOException e) {
            Log.warn("Could not list download directory " + dir + ": " + e.getMessage());
        }

        files.sort((a, b) -> Long.compare(
                (long) b.get("modified"),
                (long) a.get("modified")
        ));

        return files;
    }

    // -------------------------------------------------------------- internals

    private void run(DownloadJob job, URI uri, Path target, DownloadRequest request) {
        try {
            fetch(uri, target, job);
            job.status = "completed";

            // Subtitles are best effort: one bad subtitle must not fail the download.
            if (request.subtitles != null) {
                for (DownloadRequest.Subtitle subtitle : request.subtitles) {
                    if (subtitle == null || subtitle.url == null || subtitle.url.isBlank()) {
                        continue;
                    }

                    try {
                        String lang = (subtitle.lang == null || subtitle.lang.isBlank())
                                ? "und"
                                : sanitize(subtitle.lang);

                        String extension = extensionOf(subtitle.url, "vtt");

                        Path subtitleTarget = target.resolveSibling(
                                target.getFileName().toString() + "." + lang + "." + extension
                        );

                        fetch(URI.create(subtitle.url.trim()), subtitleTarget, job);
                        Log.info("Subtitle saved " + subtitleTarget);
                    } catch (Exception e) {
                        Log.warn("Subtitle failed: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Log.error("Download failed job=" + job.jobId, e);
            job.status = "failed";
            job.error = e.getMessage();
        } finally {
            job.finishedAt = System.currentTimeMillis();
        }
    }

    private void fetch(URI uri, Path target, DownloadJob job) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .timeout(Duration.ofMinutes(30))
                .GET()
                .build();

        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        int code = response.statusCode();

        if (code != 200 && code != 206) {
            throw new IOException("HTTP " + code + " for " + uri);
        }

        job.totalBytes = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(-1);

        job.status = "running";

        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(
                     target,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING
             )) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                job.bytesWritten += read;
            }
        }
    }

    private String pickFilename(DownloadRequest request, URI uri) {
        String urlExtension = extensionOf(uri.toString(), "mp4");

        if (request.filename != null && !request.filename.isBlank()) {
            String name = request.filename.trim();

            // "My Movie" (no dot) -> "My Movie.mp4"; "My Movie.mkv" is kept as-is.
            // Any video extension is accepted: mkv, mp4, avi, ts, mov, webm, ...
            if (!hasExtension(name)) {
                return name + "." + urlExtension;
            }

            return name;
        }

        String path = uri.getPath();

        if (path == null) {
            return "download." + urlExtension;
        }

        String last = path.substring(path.lastIndexOf('/') + 1);

        return last.isBlank() ? "download." + urlExtension : last;
    }

    private Path resolveDownloadDir(String folder) {
        String base = System.getenv()
                .getOrDefault("BLUESREAMS_DOWNLOAD_DIR", DEFAULT_DOWNLOAD_DIR);

        Path dir = resolveCaseInsensitive(Paths.get(base));

        if (folder != null && !folder.isBlank()) {
            dir = dir.resolve(folder);
        }

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create download directory " + dir, e);
        }

        return dir;
    }

    /**
     * The bluestreams mount is NTFS reached through fuseblk, which is case-sensitive
     * from Linux - so {@code descargas} and {@code Descargas} are two different
     * directories. Walk the path and use an existing case-insensitive match whenever
     * the exact segment is missing, then let {@code createDirectories} add the rest.
     */
    private static Path resolveCaseInsensitive(Path path) {
        if (Files.exists(path)) {
            return path;
        }

        Path parent = path.getParent();

        if (parent == null) {
            return path;
        }

        Path resolvedParent = resolveCaseInsensitive(parent);
        String name = path.getFileName().toString();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolvedParent)) {
            for (Path candidate : stream) {
                if (candidate.getFileName().toString().equalsIgnoreCase(name)) {
                    return candidate;
                }
            }
        } catch (IOException ignored) {
            // fall through and use the literal name
        }

        return resolvedParent.resolve(name);
    }

    private static String extensionOf(String url, String defaultExtension) {
        String path;

        try {
            path = URI.create(url).getPath();
        } catch (Exception e) {
            return defaultExtension;
        }

        if (path == null) {
            return defaultExtension;
        }

        int dot = path.lastIndexOf('.');

        if (dot == -1 || dot == path.length() - 1) {
            return defaultExtension;
        }

        String extension = path.substring(dot + 1).toLowerCase();

        // Guard against a "dot" that is really part of a longer path segment.
        if (extension.contains("/")) {
            return defaultExtension;
        }

        return extension;
    }

    private static boolean hasExtension(String name) {
        String base = name.substring(name.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');

        return dot > 0 && dot < base.length() - 1;
    }

    private static String sanitize(String name) {
        if (name == null) {
            return null;
        }

        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
    }

    private static String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return null;
        }

        String cleaned = folder.replace("\\", "/").trim();

        // No path traversal out of the download directory.
        if (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_");
        }

        cleaned = cleaned.replaceAll("[\\\\:*?\"<>|\\r\\n\\t]", "_");
        cleaned = cleaned.replaceAll("/+", "/");

        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }

        return cleaned;
    }
}
