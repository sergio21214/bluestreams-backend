package com.spmf.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spmf.MediaItem;
import com.spmf.MusicScannerService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class MusicDownloadWorker {

    private static final String YTDLP =
            "/usr/local/bin/yt-dlp";

    private static final String DOWNLOAD_FOLDER =
            "/mnt/bluestreams/SPMF/media/music/yt-downloads";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MusicScannerService musicScannerService;

    @Inject
    YtDlpOptions ytDlpOptions;

    @Inject
    MeterRegistry registry;

    @ConfigProperty(
            name = "music.download.max-attempts",
            defaultValue = "3"
    )
    int maxAttempts;

    @ConfigProperty(
            name = "music.download.retry-delay-ms",
            defaultValue = "10000"
    )
    long retryDelayMs;

    private static final String[] PERMANENT_FAILURES = {
            "private video",
            "this video is private",
            "members-only",
            "join this channel",
            "age-restricted",
            "sign in to confirm your age",
            "requested format is not available",
            "unsupported url",
            "is not a valid url",
            "http error 404",
            "http error 410",
            "has been removed",
            "does not exist",
            "account has been terminated",
            "channel is not available"
    };

    public void processJob(Long jobId) {

        long started = System.nanoTime();

        try {

            downloadWithRetries(jobId);

            registry.counter(
                    "music.download.jobs",
                    "result",
                    "completed"
            ).increment();

            registry.timer(
                    "music.download.duration",
                    "result",
                    "completed"
            ).record(System.nanoTime() - started, TimeUnit.NANOSECONDS);

            updateStatus(
                    jobId,
                    MusicDownloadJob.Status.PROCESSING,
                    null
            );

            triggerScanner();

            attachArtwork(jobId);

            updateStatus(
                    jobId,
                    MusicDownloadJob.Status.COMPLETED,
                    null
            );

        } catch (Exception e) {

            registry.counter(
                    "music.download.jobs",
                    "result",
                    "failed"
            ).increment();

            registry.timer(
                    "music.download.duration",
                    "result",
                    "failed"
            ).record(System.nanoTime() - started, TimeUnit.NANOSECONDS);

            e.printStackTrace();

            try {

                updateStatus(
                        jobId,
                        MusicDownloadJob.Status.FAILED,
                        getErrorMessage(e)
                );

            } catch (Exception statusException) {

                statusException.printStackTrace();
            }
        }
    }

    private void downloadWithRetries(
            Long jobId
    ) throws Exception {

        int attempts =
                Math.max(1, maxAttempts);

        Exception lastFailure =
                null;

        for (
                int attempt = 1;
                attempt <= attempts;
                attempt++
        ) {

            try {

                registry.counter("music.download.attempts").increment();

                updateStatus(
                        jobId,
                        MusicDownloadJob.Status.DOWNLOADING,
                        null
                );

                JsonNode metadata =
                        extractMetadata(jobId);

                updateMetadata(
                        jobId,
                        metadata
                );

                download(jobId);

                return;

            } catch (Exception e) {

                lastFailure = e;

                if (
                        attempt >= attempts ||
                                !isRetryable(e)
                ) {
                    break;
                }

                long delay =
                        Math.max(0, retryDelayMs) * attempt;

                registry.counter("music.download.retries").increment();

                System.out.println(
                        "Music download attempt "
                                + attempt
                                + "/"
                                + attempts
                                + " failed for job "
                                + jobId
                                + ": "
                                + getErrorMessage(e)
                                + ". Retrying in "
                                + delay
                                + " ms..."
                );

                try {

                    Thread.sleep(delay);

                } catch (InterruptedException interrupted) {

                    Thread.currentThread()
                            .interrupt();

                    throw new IllegalStateException(
                            "Interrupted while waiting to retry music download",
                            interrupted
                    );
                }
            }
        }

        throw lastFailure;
    }

    private boolean isRetryable(
            Exception e
    ) {

        if (
                e instanceof InterruptedException ||
                        Thread.currentThread().isInterrupted()
        ) {
            return false;
        }

        String message =
                e.getMessage();

        if (
                message == null ||
                        message.isBlank()
        ) {
            return true;
        }

        String lower =
                message.toLowerCase(Locale.ROOT);

        for (String marker : PERMANENT_FAILURES) {
            if (lower.contains(marker)) {
                return false;
            }
        }

        return true;
    }

    private JsonNode extractMetadata(
            Long jobId
    ) throws Exception {

        MusicDownloadJob job =
                findJob(jobId);

        List<String> command =
                new ArrayList<>();

        command.add(YTDLP);

        command.add("--dump-single-json");
        command.add("--no-download");
        command.add("--no-playlist");
        command.add("--no-warnings");

        command.addAll(ytDlpOptions.commonArgs());

        command.add(job.url);

        ProcessResult result =
                execute(command);

        if (result.exitCode != 0) {

            throw new IllegalStateException(
                    "yt-dlp metadata extraction failed: "
                            + result.output
            );
        }

        return objectMapper.readTree(
                result.output
        );
    }

    private void download(
            Long jobId
    ) throws Exception {

        MusicDownloadJob job =
                findJob(jobId);

        List<String> command =
                new ArrayList<>();

        command.add(YTDLP);

        command.add("--no-playlist");

        command.addAll(ytDlpOptions.commonArgs());

        command.add("-x");
        command.add("--audio-format");
        command.add("m4a");

        command.add("-f");
        command.add("bestaudio/best");

        command.add("--embed-metadata");

        command.add("--write-thumbnail");
        command.add("--convert-thumbnails");
        command.add("jpg");

        command.add("--no-embed-info-json");

        command.add("-o");

        command.add(
                DOWNLOAD_FOLDER
                        + "/%(title)s [%(id)s].%(ext)s"
        );

        command.add(job.url);

        ProcessResult result =
                execute(command);

        if (result.exitCode != 0) {

            throw new IllegalStateException(
                    "yt-dlp download failed: "
                            + result.output
            );
        }

        File output =
                findDownloadedFile(job);

        if (output == null) {

            throw new IllegalStateException(
                    "yt-dlp completed but no audio file was found"
            );
        }

        updateOutputPath(
                jobId,
                output.getAbsolutePath()
        );

        File thumbnail =
                findDownloadedThumbnail(job);

        if (thumbnail != null) {

            updateThumbnailPath(
                    jobId,
                    thumbnail.getAbsolutePath()
            );

            System.out.println(
                    "Thumbnail found: "
                            + thumbnail.getAbsolutePath()
            );

        } else {

            System.out.println(
                    "No thumbnail found for "
                            + job.youtubeId
            );
        }
    }

    private File findDownloadedThumbnail(
            MusicDownloadJob job
    ) {

        File folder =
                new File(DOWNLOAD_FOLDER);

        File[] files =
                folder.listFiles();

        if (files == null) {
            return null;
        }

        String id =
                job.youtubeId;

        File newest = null;

        for (File file : files) {

            if (!file.isFile()) {
                continue;
            }

            String name =
                    file.getName().toLowerCase();

            if (!name.endsWith(".jpg")) {
                continue;
            }

            if (
                    id != null &&
                            !file.getName().contains(id)
            ) {
                continue;
            }

            if (
                    newest == null ||
                            file.lastModified()
                                    > newest.lastModified()
            ) {

                newest = file;
            }
        }

        return newest;
    }

    @Transactional
    protected void updateThumbnailPath(
            Long jobId,
            String path
    ) {

        MusicDownloadJob job =
                MusicDownloadJob.findById(jobId);

        if (job == null) {

            throw new IllegalStateException(
                    "Music download job not found: "
                            + jobId
            );
        }

        job.thumbnailPath = path;

        job.persistAndFlush();
    }

    private File findDownloadedFile(
            MusicDownloadJob job
    ) {

        File folder =
                new File(DOWNLOAD_FOLDER);

        File[] files =
                folder.listFiles();

        if (files == null) {
            return null;
        }

        String id =
                job.youtubeId;

        File newest = null;

        for (File file : files) {

            if (!file.isFile()) {
                continue;
            }

            String name =
                    file.getName().toLowerCase();

            if (
                    !name.endsWith(".webm") &&
                            !name.endsWith(".m4a") &&
                            !name.endsWith(".mp3") &&
                            !name.endsWith(".opus") &&
                            !name.endsWith(".ogg") &&
                            !name.endsWith(".flac") &&
                            !name.endsWith(".aac")
            ) {
                continue;
            }

            if (
                    id != null &&
                            !file.getName().contains(id)
            ) {
                continue;
            }

            if (
                    newest == null ||
                            file.lastModified()
                                    > newest.lastModified()
            ) {

                newest = file;
            }
        }

        return newest;
    }

    private ProcessResult execute(
            List<String> command
    ) throws Exception {

        System.out.println(
                "Executing command: "
                        + String.join(" ", command)
        );

        ProcessBuilder builder =
                new ProcessBuilder(command);

        builder.redirectErrorStream(true);

        Process process =
                builder.start();

        StringBuilder output =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                System.out.println(
                        "[yt-dlp] " + line
                );

                output.append(line)
                        .append('\n');
            }
        }

        int exitCode =
                process.waitFor();

        System.out.println(
                "yt-dlp exit code: "
                        + exitCode
        );

        return new ProcessResult(
                exitCode,
                output.toString()
        );
    }

    @Transactional
    protected MusicDownloadJob findJob(
            Long jobId
    ) {

        MusicDownloadJob job =
                MusicDownloadJob.findById(jobId);

        if (job == null) {

            throw new IllegalStateException(
                    "Music download job not found: "
                            + jobId
            );
        }

        return job;
    }

    @Transactional
    protected void updateMetadata(
            Long jobId,
            JsonNode metadata
    ) {

        MusicDownloadJob job =
                MusicDownloadJob.findById(jobId);

        if (job == null) {

            throw new IllegalStateException(
                    "Music download job not found: "
                            + jobId
            );
        }

        job.youtubeId =
                text(metadata, "id");

        job.title =
                text(metadata, "title");

        job.artist =
                text(metadata, "artist");

        job.album =
                text(metadata, "album");

        job.thumbnailUrl =
                text(metadata, "thumbnail");

        if (
                metadata.has("duration") &&
                        !metadata.get("duration").isNull()
        ) {

            job.durationSeconds =
                    metadata.get("duration").asInt();
        }

        job.persistAndFlush();
    }

    @Transactional
    protected void updateStatus(
            Long jobId,
            MusicDownloadJob.Status status,
            String error
    ) {

        MusicDownloadJob job =
                MusicDownloadJob.findById(jobId);

        if (job == null) {

            throw new IllegalStateException(
                    "Music download job not found: "
                            + jobId
            );
        }

        job.status = status;

        if (
                status ==
                        MusicDownloadJob.Status.DOWNLOADING &&
                        job.startedAt == null
        ) {

            job.startedAt =
                    LocalDateTime.now();
        }

        if (
                status ==
                        MusicDownloadJob.Status.COMPLETED ||
                        status ==
                                MusicDownloadJob.Status.FAILED
        ) {

            job.completedAt =
                    LocalDateTime.now();
        }

        job.errorMessage = error;

        job.persistAndFlush();
    }

    @Transactional
    protected void updateOutputPath(
            Long jobId,
            String path
    ) {

        MusicDownloadJob job =
                MusicDownloadJob.findById(jobId);

        if (job == null) {

            throw new IllegalStateException(
                    "Music download job not found: "
                            + jobId
            );
        }

        job.outputPath = path;

        job.persistAndFlush();
    }

    private void triggerScanner() {

        System.out.println(
                "Starting music scanner..."
        );

        musicScannerService.scanMusic();

        System.out.println(
                "Music scanner completed."
        );
    }

    private String text(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.get(field);

        if (
                value == null ||
                        value.isNull()
        ) {

            return null;
        }

        return value.asText();
    }

    private String getErrorMessage(
            Exception e
    ) {

        String message =
                e.getMessage();

        if (
                message == null ||
                        message.isBlank()
        ) {

            return e.getClass()
                    .getSimpleName();
        }

        return message.length() > 3900
                ? message.substring(0, 3900)
                : message;
    }

    private record ProcessResult(
            int exitCode,
            String output
    ) {
    }

    private void attachArtwork(
            Long jobId
    ) {

        MusicDownloadJob job =
                findJob(jobId);

        if (
                job.outputPath == null ||
                        job.thumbnailPath == null
        ) {

            System.out.println(
                    "Skipping artwork: missing paths"
            );

            return;
        }

        MediaItem item =
                musicScannerService.findByFilePath(
                        job.outputPath
                );

        if (item == null) {

            System.out.println(
                    "Could not find MediaItem for "
                            + job.outputPath
            );

            return;
        }

        /*
         * Only assign artwork if this newly-created
         * MediaItem doesn't already have one.
         */
        if (
                item.poster != null &&
                        !item.poster.isBlank()
        ) {

            System.out.println(
                    "MediaItem already has artwork: "
                            + item.id
            );

            return;
        }

        String poster =
                copyArtwork(
                        job.thumbnailPath,
                        item.id
                );

        if (poster == null) {
            return;
        }

        updatePoster(
                item.id,
                poster
        );
    }

    private String copyArtwork(
            String sourcePath,
            Long mediaId
    ) {

        try {

            Path source =
                    Path.of(sourcePath);

            Path artworkDirectory =
                    Path.of(
                            "/mnt/bluestreams/SPMF/media/music/music-art"
                    );

            Files.createDirectories(
                    artworkDirectory
            );

            Path destination =
                    artworkDirectory.resolve(
                            mediaId + ".jpg"
                    );

            Files.copy(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );

            System.out.println(
                    "Artwork copied to: "
                            + destination
            );

            return
                    "https://api.bluestreams.uk/music-art/"
                            + mediaId
                            + ".jpg";

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }
    }

    @Transactional
    protected void updatePoster(
            Long mediaId,
            String poster
    ) {

        MediaItem item =
                MediaItem.findById(mediaId);

        if (item == null) {
            return;
        }

        item.poster = poster;

        item.persistAndFlush();

        System.out.println(
                "Poster assigned to MediaItem "
                        + mediaId
        );
    }
}