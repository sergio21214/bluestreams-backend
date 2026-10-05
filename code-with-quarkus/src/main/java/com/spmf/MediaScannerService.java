package com.spmf;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class MediaScannerService {

    @Inject
    ProcessingQueueService processingQueueService;

    public void scanVideos() {

        File root =
                new File("/mnt/bluestreams/SPMF/videos");

        if (!root.exists()) {

            System.out.println(
                    "VIDEOS FOLDER NOT FOUND"
            );

            return;
        }

        scanDirectory(root);
    }

    private void scanDirectory(
            File directory
    ) {

        File[] files =
                directory.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {

            if (file.isDirectory()) {

                scanDirectory(file);

            } else {

                processFile(file);
            }
        }
    }

    private void processFile(
            File file
    ) {

        try {

            String filename =
                    file.getName()
                            .toLowerCase();

            if (
                    !filename.endsWith(".mp4")
                            && !filename.endsWith(".mkv")
                            && !filename.endsWith(".avi")
            ) {

                return;
            }

            String inputPath =
                    file.getPath();

            System.out.println(
                    "SCANNING: "
                            + inputPath
            );

            long exists =
                    MediaItem.count(
                            "filename",
                            file.getName()
                    );

            if (exists > 0) {

                System.out.println(
                        "ALREADY EXISTS"
                );

                return;
            }

            MediaItem item =
                    buildMediaItem(file);

            saveMediaItem(item);

            processingQueueService.processVideo(
                    inputPath
            );

        } catch (Exception e) {

            System.out.println(
                    "FAILED FILE: "
                            + file.getPath()
            );

            e.printStackTrace();
        }
    }

    private MediaItem buildMediaItem(
            File file
    ) {

        MediaItem item =
                new MediaItem();

        String filename =
                file.getName();

        String cleanTitle =
                filename
                        .replace(".mp4", "")
                        .replace(".mkv", "")
                        .replace(".avi", "");

        String folderName =
                FilenameUtils.cleanName(
                        cleanTitle
                );

        item.filename =
                filename;

        item.folderName =
                folderName;

        item.title =
                cleanTitle;

        item.manifestUrl =
                "https://hls.bluestreams.uk/hls/"
                        + folderName
                        + "/manifest.mpd";

        item.subtitlePath =
                "https://api.bluestreams.uk/subtitles/"
                        + folderName;

        item.poster =
                "https://api.bluestreams.uk/thumbnails/"
                        + folderName
                        + ".jpg";

        detectSeriesData(
                item,
                file
        );

        return item;
    }

    private void detectSeriesData(
            MediaItem item,
            File file
    ) {

        try {

            File seasonFolder =
                    file.getParentFile();

            File seriesFolder =
                    seasonFolder.getParentFile();

            boolean validStructure =
                    seasonFolder != null
                            && seriesFolder != null
                            && seasonFolder.getName()
                            .toLowerCase()
                            .contains("season");

            if (!validStructure) {

                item.mediaType =
                        "movie";

                return;
            }

            item.mediaType =
                    "episode";

            item.seriesTitle =
                    seriesFolder.getName();

            Matcher seasonMatcher =
                    Pattern.compile("(\\d+)")
                            .matcher(
                                    seasonFolder.getName()
                            );

            if (seasonMatcher.find()) {

                item.seasonNumber =
                        Integer.parseInt(
                                seasonMatcher.group(1)
                        );
            }

            Matcher episodeMatcher =
                    Pattern.compile(
                            "(?:[eE](\\d+))|(?:episode\\s*(\\d+))"
                    ).matcher(
                            file.getName()
                    );

            if (episodeMatcher.find()) {

                String value =
                        episodeMatcher.group(1);

                if (value == null) {

                    value =
                            episodeMatcher.group(2);
                }

                if (value != null) {

                    item.episodeNumber =
                            Integer.parseInt(value);
                }
            }

        } catch (Exception e) {

            Log.error(
                    "SERIES DETECTION ERROR",
                    e
            );

            item.mediaType =
                    "movie";
        }
    }

    @Transactional
    public void saveMediaItem(
            MediaItem item
    ) {
        System.out.println("========== MEDIA ==========");
        System.out.println("TITLE: " + item.title);
        System.out.println("FOLDER: " + item.folderName);
        System.out.println("TYPE: " + item.mediaType);
        System.out.println("MANIFEST: " + item.manifestUrl);
        System.out.println("POSTER: " + item.poster);

        item.persist();

        System.out.println(
                "SAVED: "
                        + item.title
        );
    }

    @Transactional
    public MediaItem findByFilePath(
            String filePath
    ) {

        return MediaItem.find(
                "filePath",
                filePath
        ).firstResult();
    }

}