package com.spmf.iptv;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URLEncoder;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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

    private static final long STALL_TIMEOUT_MS = 60_000L;

    private static final int MAX_DOWNLOAD_ATTEMPTS = 5;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final Map<String, DownloadJob> jobs = new ConcurrentHashMap<>();

    private final Map<String, DownloadBatch> batches = new ConcurrentHashMap<>();

    @ConfigProperty(
            name = "iptv.download.concurrency",
            defaultValue = "1"
    )
    int downloadConcurrency;

    private ExecutorService pool;

    private ScheduledExecutorService watchdog;

    @PostConstruct
    void initPools() {

        int threads = Math.max(1, downloadConcurrency);

        pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "iptv-download");
            thread.setDaemon(true);
            return thread;
        });

        watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "iptv-download-watchdog");
            thread.setDaemon(true);
            return thread;
        });

        Log.infof(
                "IPTV download concurrency set to %d",
                threads
        );
    }

    @PreDestroy
    void shutdownPools() {

        if (pool != null) {
            pool.shutdownNow();
        }

        if (watchdog != null) {
            watchdog.shutdownNow();
        }
    }

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

    /**
     * Queue several downloads and return a batch the caller can poll.
     *
     * <p>All URLs are validated before any job is queued, so an invalid item
     * does not leave a half-created batch behind.</p>
     */
    public DownloadBatch startBatch(
            String folder,
            List<DownloadRequest> items
    ) throws IOException {

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items are required");
        }

        String batchFolder =
                (folder == null || folder.isBlank())
                        ? null
                        : folder;

        for (int index = 0; index < items.size(); index++) {

            DownloadRequest item = items.get(index);

            if (item == null || item.url == null || item.url.isBlank()) {
                throw new IllegalArgumentException(
                        "Item " + index + " is missing Url"
                );
            }

            URI uri = URI.create(item.url.trim());

            if (!uri.isAbsolute() || uri.getScheme() == null) {
                throw new IllegalArgumentException(
                        "Item " + index + " has an invalid Url"
                );
            }

            if ((item.folder == null || item.folder.isBlank())
                    && batchFolder != null) {
                item.folder = batchFolder;
            }
        }

        String batchId = UUID.randomUUID().toString();

        DownloadBatch batch =
                new DownloadBatch(batchId, batchFolder);

        batches.put(batchId, batch);

        for (DownloadRequest item : items) {

            DownloadJob job = start(item);
            job.batchId = batchId;
            batch.jobs.add(job);
        }

        batch.total = batch.jobs.size();

        Log.info(
                "Batch queued id=" + batchId
                        + " jobs=" + batch.total
                        + " folder=" + batchFolder
        );

        return batch;
    }

    public BatchStatus batchStatus(String batchId) {

        DownloadBatch batch = batches.get(batchId);

        if (batch == null) {
            return null;
        }

        BatchStatus status = new BatchStatus();
        status.batchId = batch.batchId;
        status.folder = batch.folder;
        status.total = batch.jobs.size();
        status.createdAt = batch.createdAt;

        int queued = 0;
        int running = 0;
        int completed = 0;
        int failed = 0;
        long finishedAt = 0;

        for (DownloadJob job : batch.jobs) {

            BatchStatus.JobStatus item = new BatchStatus.JobStatus();
            item.jobId = job.jobId;
            item.filename = job.filename;
            item.status = job.status;
            item.bytesWritten = job.bytesWritten;
            item.totalBytes = job.totalBytes;
            item.error = job.error;
            item.finishedAt = job.finishedAt;
            status.jobs.add(item);

            switch (job.status == null ? "" : job.status) {
                case "completed" -> completed++;
                case "failed" -> failed++;
                case "running" -> running++;
                default -> queued++;
            }

            finishedAt = Math.max(finishedAt, job.finishedAt);
        }

        status.queued = queued;
        status.running = running;
        status.completed = completed;
        status.failed = failed;

        if (queued + running > 0) {
            status.status = "running";
        } else if (failed == 0) {
            status.status = "completed";
            status.finishedAt = finishedAt;
        } else if (completed == 0) {
            status.status = "failed";
            status.finishedAt = finishedAt;
        } else {
            status.status = "completed_with_errors";
            status.finishedAt = finishedAt;
        }

        return status;
    }

    public List<BatchStatus> listBatches() {

        return batches.keySet()
                .stream()
                .map(this::batchStatus)
                .filter(status -> status != null)
                .sorted(
                        Comparator.comparingLong(
                                (BatchStatus status) -> status.createdAt
                        ).reversed()
                )
                .toList();
    }

    public DownloadJob status(String jobId) {
        return jobs.get(jobId);
    }

    public List<Map<String, Object>> list() {
        return list(null, false);
    }

    public List<Map<String, Object>> list(
            String folder,
            boolean recursive
    ) {

        Path dir = resolveDownloadDir(sanitizeFolder(folder));
        Path base = resolveDownloadDir(null);

        List<Map<String, Object>> files = new ArrayList<>();

        if (recursive) {

            try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {

                stream.filter(Files::isRegularFile)
                        .forEach(entry -> addFile(files, base, entry));

            } catch (IOException e) {

                Log.warn(
                        "Could not walk download directory "
                                + dir + ": " + e.getMessage()
                );
            }

        } else {

            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(dir)) {

                for (Path entry : stream) {

                    if (Files.isRegularFile(entry)) {
                        addFile(files, base, entry);
                    }
                }

            } catch (IOException e) {

                Log.warn(
                        "Could not list download directory "
                                + dir + ": " + e.getMessage()
                );
            }
        }

        files.sort((a, b) -> Long.compare(
                (long) b.get("modified"),
                (long) a.get("modified")
        ));

        return files;
    }

    private void addFile(
            List<Map<String, Object>> files,
            Path base,
            Path entry
    ) {

        try {

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getFileName().toString());
            item.put("relativePath", base.relativize(entry).toString());
            item.put("size", Files.size(entry));
            item.put("modified", Files.getLastModifiedTime(entry).toMillis());
            files.add(item);

        } catch (IOException ignored) {
            // Skip files that disappear or cannot be read.
        }
    }

    // ------------------------------------------------------ downloaded library

    /**
     * Resolve a client-supplied relative path against the download root.
     * Returns null when the path is invalid, escapes the root, or is not a file.
     */
    public Path resolveDownloadedFile(String relativePath) {

        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }

        Path root = resolveDownloadDir(null).toAbsolutePath().normalize();
        Path candidate = root.resolve(relativePath).normalize();

        if (!candidate.startsWith(root)) {
            return null;
        }

        try {

            Path realRoot = root.toRealPath();
            Path realCandidate = candidate.toRealPath();

            if (!realCandidate.startsWith(realRoot)
                    || !Files.isRegularFile(realCandidate)) {
                return null;
            }

            return realCandidate;

        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Recursively list downloaded files. By default only video files are
     * returned; pass {@code includeAll} to include subtitles and anything else.
     */
    public List<DownloadedFile> listLibrary(
            String folder,
            boolean includeAll
    ) {

        Path root = resolveDownloadDir(null).toAbsolutePath().normalize();
        Path start = resolveDownloadDir(sanitizeFolder(folder))
                .toAbsolutePath()
                .normalize();

        List<DownloadedFile> files = new ArrayList<>();

        try (java.util.stream.Stream<Path> stream = Files.walk(start)) {

            stream.filter(Files::isRegularFile).forEach(entry -> {

                if (!entry.startsWith(root)) {
                    return;
                }

                if (!includeAll && !isVideoFile(entry)) {
                    return;
                }

                try {

                    String relativePath = root.relativize(entry)
                            .toString()
                            .replace('\\', '/');

                    DownloadedFile file = new DownloadedFile();
                    file.name = entry.getFileName().toString();
                    file.relativePath = relativePath;
                    file.size = Files.size(entry);
                    file.modified = Files.getLastModifiedTime(entry).toMillis();
                    file.contentType = contentTypeFor(entry);
                    file.streamUrl =
                            "/iptv/downloads/file?path="
                                    + encodePath(relativePath);

                    files.add(file);

                } catch (IOException ignored) {
                    // Skip files that vanish during the walk.
                }
            });

        } catch (IOException e) {

            Log.warn(
                    "Could not list downloaded library "
                            + start + ": " + e.getMessage()
            );
        }

        files.sort(Comparator.comparing(file -> file.relativePath));

        return files;
    }

    private static boolean isVideoFile(Path file) {

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);

        return Set.of(
                ".mp4", ".m4v", ".mkv", ".avi", ".webm", ".mov",
                ".ts", ".mpg", ".mpeg", ".flv", ".wmv", ".3gp", ".ogv"
        ).stream().anyMatch(name::endsWith);
    }

    public static String contentTypeFor(Path file) {

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".mp4") || name.endsWith(".m4v")) return "video/mp4";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".avi")) return "video/x-msvideo";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".ts")) return "video/mp2t";
        if (name.endsWith(".mpg") || name.endsWith(".mpeg")) return "video/mpeg";
        if (name.endsWith(".flv")) return "video/x-flv";
        if (name.endsWith(".wmv")) return "video/x-ms-wmv";
        if (name.endsWith(".3gp")) return "video/3gpp";
        if (name.endsWith(".ogv")) return "video/ogg";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".m4a")) return "audio/mp4";
        if (name.endsWith(".aac")) return "audio/aac";
        if (name.endsWith(".flac")) return "audio/flac";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".opus")) return "audio/opus";
        if (name.endsWith(".vtt")) return "text/vtt; charset=utf-8";
        if (name.endsWith(".srt")) return "application/x-subrip";

        return "application/octet-stream";
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    // -------------------------------------------------------------- internals

    private void run(DownloadJob job, URI uri, Path target, DownloadRequest request) {
        try {
            downloadWithRetry(job, uri, target);
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

    /**
     * Download the main media file with retries and Range resume.
     *
     * <p>If the network drops mid-transfer, the next attempt continues at the
     * current byte count instead of restarting from zero. This is what keeps a
     * season download recoverable after a Wi-Fi/ISP interruption.</p>
     */
    private void downloadWithRetry(
            DownloadJob job,
            URI uri,
            Path target
    ) throws Exception {

        Exception lastError = null;

        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {

            try {
                downloadAttempt(job, uri, target);
                return;
            } catch (Exception e) {

                lastError = e;

                if (attempt == MAX_DOWNLOAD_ATTEMPTS) {
                    break;
                }

                long delay = Math.min(
                        30_000L,
                        2_000L * attempt * attempt
                );

                Log.warnf(
                        "Download attempt %d/%d failed for %s: %s (retrying in %d ms)",
                        attempt,
                        MAX_DOWNLOAD_ATTEMPTS,
                        target.getFileName(),
                        e.getMessage(),
                        delay
                );

                job.status = "running";

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }

        throw lastError == null
                ? new IOException("Download failed")
                : lastError;
    }

    private void downloadAttempt(
            DownloadJob job,
            URI uri,
            Path target
    ) throws Exception {

        long existing = Files.exists(target)
                ? Files.size(target)
                : 0L;

        job.bytesWritten = existing;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .timeout(Duration.ofMinutes(30))
                .GET();

        if (existing > 0) {
            builder.header("Range", "bytes=" + existing + "-");
        }

        HttpResponse<InputStream> response = client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofInputStream()
        );

        int code = response.statusCode();

        if (code == 416 && existing > 0) {

            long total = rangeTotal(
                    response.headers()
                            .firstValue("Content-Range")
                            .orElse(null)
            );

            response.body().close();

            job.bytesWritten = existing;
            job.totalBytes = total > 0 ? total : existing;
            job.status = "completed";

            Log.infof(
                    "File already complete, skipping: %s (%d bytes)",
                    target.getFileName(),
                    existing
            );

            return;
        }

        if (code != 200 && code != 206) {
            response.body().close();
            throw new IOException("HTTP " + code + " for " + uri);
        }

        boolean append = code == 206 && existing > 0;

        if (code == 206 && existing > 0) {

            long start = rangeStart(
                    response.headers()
                            .firstValue("Content-Range")
                            .orElse(null)
            );

            if (start >= 0 && start != existing) {

                response.body().close();

                try (OutputStream ignored =
                             Files.newOutputStream(
                                     target,
                                     StandardOpenOption.CREATE,
                                     StandardOpenOption.TRUNCATE_EXISTING
                             )) {
                    // intentionally empty
                }

                job.bytesWritten = 0;
                throw new IOException(
                        "Range mismatch (wanted " + existing
                                + ", got " + start + "); restarting"
                );
            }
        }

        if (code == 200) {
            existing = 0;
            job.bytesWritten = 0;
        }

        long contentLength = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(-1);

        if (code == 206) {

            long total = rangeTotal(
                    response.headers()
                            .firstValue("Content-Range")
                            .orElse(null)
            );

            job.totalBytes = total > 0
                    ? total
                    : (existing + contentLength);

        } else {

            job.totalBytes = contentLength;
        }

        job.status = "running";

        InputStream body = response.body();
        AtomicLong lastProgress = new AtomicLong(
                System.currentTimeMillis()
        );

        ScheduledFuture<?> stallWatch = watchdog.scheduleAtFixedRate(
                () -> {
                    if (System.currentTimeMillis()
                            - lastProgress.get() > STALL_TIMEOUT_MS) {

                        Log.warn(
                                "Download stalled; closing stream: "
                                        + target.getFileName()
                        );

                        try {
                            body.close();
                        } catch (IOException ignored) {
                            // The retry loop will reopen the stream.
                        }
                    }
                },
                5,
                5,
                TimeUnit.SECONDS
        );

        try (InputStream in = body) {

            try (OutputStream out = append
                    ? Files.newOutputStream(
                            target,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    )
                    : Files.newOutputStream(
                            target,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    )) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int read;

                while ((read = in.read(buffer)) != -1) {

                    out.write(buffer, 0, read);
                    job.bytesWritten += read;
                    lastProgress.set(System.currentTimeMillis());
                }
            }

        } finally {
            stallWatch.cancel(false);
        }
    }

    private long rangeStart(String contentRange) {

        if (contentRange == null || !contentRange.startsWith("bytes ")) {
            return -1;
        }

        String spec = contentRange.substring("bytes ".length());
        int dash = spec.indexOf('-');

        if (dash <= 0) {
            return -1;
        }

        try {
            return Long.parseLong(spec.substring(0, dash));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private long rangeTotal(String contentRange) {

        if (contentRange == null) {
            return -1;
        }

        int slash = contentRange.lastIndexOf('/');

        if (slash < 0 || slash == contentRange.length() - 1) {
            return -1;
        }

        String value = contentRange.substring(slash + 1);

        if ("*".equals(value)) {
            return -1;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
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

    static String sanitize(String name) {
        if (name == null) {
            return null;
        }

        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
    }

    static String sanitizeFolder(String folder) {
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
