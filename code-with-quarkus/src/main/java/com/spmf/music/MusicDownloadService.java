package com.spmf.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spmf.MusicScannerService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.TransactionPhase;

@ApplicationScoped
public class MusicDownloadService {

    @Inject
    MusicDownloadWorker musicDownloadWorker;

    @Inject
    Event<MusicDownloadJobCreated> jobCreatedEvent;

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

    private ExecutorService executor;

    @PostConstruct
    void init() {

        executor =
                Executors.newFixedThreadPool(2);

        try {

            Files.createDirectories(
                    Path.of(DOWNLOAD_FOLDER)
            );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Could not create music download directory",
                    e
            );
        }
    }

    @PreDestroy
    void shutdown() {

        if (executor != null) {
            executor.shutdown();
        }
    }

    @Transactional
    public MusicDownloadJob createJob(
            String url
    ) {
        System.out.println(
                "========== CREATE MUSIC DOWNLOAD JOB =========="
        );
        System.out.println(
                "createJob() called with URL: " + url
        );
        System.out.println(
                "Thread: " + Thread.currentThread().getName()
        );
        System.out.println(
                "================================================"
        );

        if (
                url == null ||
                        url.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "URL is required"
            );
        }

        String normalizedUrl =
                normalizeUrl(url);

        validateUrl(
                normalizedUrl
        );

        MusicDownloadJob job =
                new MusicDownloadJob();

        job.url =
                normalizedUrl;

        job.status =
                MusicDownloadJob.Status.QUEUED;

        job.createdAt =
                LocalDateTime.now();

        job.persistAndFlush();

        jobCreatedEvent.fire(
                new MusicDownloadJobCreated(job.id)
        );

        return job;
    }

    @Transactional
    public MusicDownloadJob getJob(
            Long id
    ) {

        return MusicDownloadJob.findById(
                id
        );
    }


    private JsonNode extractMetadata(
            Long jobId
    ) throws Exception {

        MusicDownloadJob job =
                findJob(jobId);

        updateStatus(
                jobId,
                MusicDownloadJob.Status.DOWNLOADING,
                null
        );

        List<String> command =
                new ArrayList<>();

        command.add(YTDLP);

        command.add(
                "--dump-single-json"
        );

        command.add(
                "--no-download"
        );

        command.add(
                "--no-playlist"
        );

        command.add(
                "--no-warnings"
        );

        command.addAll(
                ytDlpOptions.commonArgs()
        );

        command.add(
                job.url
        );

        ProcessResult result =
                execute(
                        command
                );

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

        /*
         * Only download one item for now.
         *
         * Playlist support will be added separately.
         */
        command.add(
                "--no-playlist"
        );

        command.addAll(
                ytDlpOptions.commonArgs()
        );

        /*
         * Best available audio-only stream.
         */
        command.add(
                "-f"
        );

        command.add(
                "bestaudio/best"
        );

        /*
         * Embed metadata obtained from yt-dlp.
         */
        command.add(
                "--embed-metadata"
        );

        /*
         * Download and embed the YouTube thumbnail.
         */
        command.add(
                "--embed-thumbnail"
        );

        /*
         * Avoid attaching huge info JSON data.
         */
        command.add(
                "--no-embed-info-json"
        );

        /*
         * Use title as filename.
         */
        command.add(
                "-o"
        );

        command.add(
                DOWNLOAD_FOLDER
                        + "/%(title)s [%(id)s].%(ext)s"
        );

        command.add(
                job.url
        );

        ProcessResult result =
                execute(
                        command
                );

        if (result.exitCode != 0) {

            throw new IllegalStateException(
                    "yt-dlp download failed: "
                            + result.output
            );
        }

        /*
         * Find the file that was actually created.
         */
        File output =
                findDownloadedFile(
                        job
                );

        if (output == null) {

            throw new IllegalStateException(
                    "yt-dlp completed but no audio file was found"
            );
        }

        updateOutputPath(
                jobId,
                output.getAbsolutePath()
        );
    }

    private File findDownloadedFile(
            MusicDownloadJob job
    ) {

        File folder =
                new File(
                        DOWNLOAD_FOLDER
                );

        File[] files =
                folder.listFiles();

        if (files == null) {
            return null;
        }

        String id =
                job.youtubeId;

        File newest =
                null;

        for (File file : files) {

            if (!file.isFile()) {
                continue;
            }

            String name =
                    file.getName()
                            .toLowerCase();

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
                            !file.getName()
                                    .contains(id)
            ) {
                continue;
            }

            if (
                    newest == null ||
                            file.lastModified()
                                    > newest.lastModified()
            ) {

                newest =
                        file;
            }
        }

        return newest;
    }

    private ProcessResult execute(
            List<String> command
    ) throws Exception {

        ProcessBuilder builder =
                new ProcessBuilder(
                        command
                );

        builder.redirectErrorStream(
                true
        );

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
                        "[yt-dlp] "
                                + line
                );

                output.append(line)
                        .append('\n');
            }
        }

        int exitCode =
                process.waitFor();

        return new ProcessResult(
                exitCode,
                output.toString()
        );
    }

    private String normalizeUrl(String url) {

        String normalized = url.trim();

        if (!normalized.matches("(?i)^https?://.*")) {
            normalized = "https://" + normalized;
        }

        return normalized;
    }

    private void validateUrl(String url) {

        String lower = url.toLowerCase();

        if (
                !lower.startsWith("https://www.youtube.com/") &&
                        !lower.startsWith("https://youtube.com/") &&
                        !lower.startsWith("https://music.youtube.com/") &&
                        !lower.startsWith("https://m.youtube.com/") &&
                        !lower.startsWith("https://youtu.be/")
        ) {

            throw new IllegalArgumentException(
                    "Only YouTube and YouTube Music URLs are supported"
            );
        }
    }

    private void updateMetadata(
            Long jobId,
            JsonNode metadata
    ) {

        MusicDownloadJob job =
                findJob(jobId);

        job.youtubeId =
                text(
                        metadata,
                        "id"
                );

        job.title =
                text(
                        metadata,
                        "title"
                );

        job.artist =
                text(
                        metadata,
                        "artist"
                );

        job.album =
                text(
                        metadata,
                        "album"
                );

        job.thumbnailUrl =
                text(
                        metadata,
                        "thumbnail"
                );

        if (
                metadata.has(
                        "duration"
                ) &&
                        !metadata.get(
                                "duration"
                        ).isNull()
        ) {

            job.durationSeconds =
                    metadata.get(
                            "duration"
                    ).asInt();
        }

        if (job.status ==
                MusicDownloadJob.Status.DOWNLOADING) {

            job.startedAt =
                    LocalDateTime.now();
        }

        job.persistAndFlush();
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

    @Transactional
    protected void updateStatus(
            Long jobId,
            MusicDownloadJob.Status status,
            String error
    ) {

        MusicDownloadJob job =
                findJob(jobId);

        job.status =
                status;

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

        job.errorMessage =
                error;

        job.persistAndFlush();
    }

    @Transactional
    protected void updateOutputPath(
            Long jobId,
            String path
    ) {

        MusicDownloadJob job =
                findJob(jobId);

        job.outputPath =
                path;

        job.persistAndFlush();
    }

    private MusicDownloadJob findJob(
            Long jobId
    ) {

        MusicDownloadJob job =
                MusicDownloadJob.findById(
                        jobId
                );

        if (job == null) {

            throw new IllegalStateException(
                    "Music download job not found: "
                            + jobId
            );
        }

        return job;
    }

    private void triggerScanner() {

        musicScannerService.scanMusic();
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

    void startDownloadAfterCommit(
            @Observes(during = TransactionPhase.AFTER_SUCCESS)
            MusicDownloadJobCreated event
    ) {

        executor.submit(
                () -> musicDownloadWorker.processJob(
                        event.jobId()
                )
        );
    }
    @Transactional
    public MusicDownloadBatchResponse createDownload(String url) {

        System.out.println(
                "========== CREATE MUSIC DOWNLOAD =========="
        );
        System.out.println(
                "createDownload() called with URL: " + url
        );
        System.out.println(
                "Thread: " + Thread.currentThread().getName()
        );
        System.out.println(
                "============================================"
        );

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }

        String normalizedUrl = normalizeUrl(url);

        validateUrl(normalizedUrl);

        /*
         * Playlist URL:
         *
         * https://www.youtube.com/playlist?list=...
         * https://music.youtube.com/playlist?list=...
         *
         * We intentionally detect playlist URLs here rather than
         * removing --no-playlist from the worker.
         */
        if (isPlaylistUrl(normalizedUrl)) {
            return createPlaylistJobs(normalizedUrl);
        }

        /*
         * Normal single-video behavior.
         */
        MusicDownloadJob job = createSingleJob(normalizedUrl);

        MusicDownloadBatchResponse response =
                new MusicDownloadBatchResponse();

        response.playlist = false;
        response.message = "Download queued successfully.";
        response.total = 1;
        response.jobs.add(
                new MusicDownloadResponse(job)
        );

        return response;
    }
    private boolean isPlaylistUrl(String url) {

        String lower = url.toLowerCase();

        return lower.contains("youtube.com/playlist")
                && lower.contains("list=");
    }
    @Transactional
    protected MusicDownloadJob createSingleJob(String url) {

        MusicDownloadJob job = new MusicDownloadJob();

        job.url = url;
        job.status = MusicDownloadJob.Status.QUEUED;
        job.createdAt = LocalDateTime.now();

        job.persistAndFlush();

        jobCreatedEvent.fire(
                new MusicDownloadJobCreated(job.id)
        );

        return job;
    }
    private MusicDownloadBatchResponse createPlaylistJobs(
            String playlistUrl
    ) {

        System.out.println(
                "========== PLAYLIST DETECTED =========="
        );

        JsonNode playlist = extractPlaylistMetadata(playlistUrl);

        JsonNode entries = playlist.get("entries");

        if (entries == null || !entries.isArray()) {
            throw new IllegalStateException(
                    "Could not find playlist entries."
            );
        }

        MusicDownloadBatchResponse response =
                new MusicDownloadBatchResponse();

        response.playlist = true;

        int created = 0;

        for (JsonNode entry : entries) {

            if (entry == null || entry.isNull()) {
                continue;
            }

            /*
             * With --flat-playlist, entries should contain
             * the video ID without downloading the videos.
             */
            String id = text(entry, "id");

            if (id == null || id.isBlank()) {
                System.out.println(
                        "Skipping playlist entry without video ID"
                );
                continue;
            }

            String videoUrl =
                    "https://www.youtube.com/watch?v=" + id;

            MusicDownloadJob job =
                    createSingleJob(videoUrl);

            response.jobs.add(
                    new MusicDownloadResponse(job)
            );

            created++;
        }

        response.total = created;

        response.message =
                "Playlist queued successfully: "
                        + created
                        + " songs.";

        System.out.println(
                "Playlist jobs created: " + created
        );

        return response;
    }
    private JsonNode extractPlaylistMetadata(
            String playlistUrl
    ) {

        try {

            List<String> command =
                    new ArrayList<>();

            command.add(YTDLP);
            command.add("--flat-playlist");
            command.add("--dump-single-json");
            command.add("--no-warnings");
            command.add("--yes-playlist");
            command.addAll(ytDlpOptions.commonArgs());
            command.add(playlistUrl);

            ProcessResult result =
                    execute(command);

            if (result.exitCode != 0) {

                throw new IllegalStateException(
                        "yt-dlp playlist extraction failed: "
                                + result.output
                );
            }

            return objectMapper.readTree(
                    result.output
            );

        } catch (Exception e) {

            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }

            throw new IllegalStateException(
                    "Could not extract playlist.",
                    e
            );
        }
    }
}