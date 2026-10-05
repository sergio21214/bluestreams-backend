package com.spmf;


import com.spmf.music.MusicMetadata;
import com.spmf.music.MusicMetadataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.File;
import java.util.List;

@ApplicationScoped
public class MusicScannerService {

    @Inject
    MusicMetadataService metadataService;

    private static final String MUSIC_FOLDER =
            "G:/SPMF/media/music";

    @Transactional
    public void scanMusic() {

        File root =
                new File(MUSIC_FOLDER);

        System.out.println(
                "Music folder exists: "
                        + root.exists()
        );

        System.out.println(
                "Music folder path: "
                        + root.getAbsolutePath()
        );

        scanFolder(root);
    }

    private void scanFolder(
            File folder
    ) {

        File[] files =
                folder.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {

            if (file.isDirectory()) {

                scanFolder(file);

                continue;
            }

            String filename =
                    file.getName()
                            .toLowerCase();

            if (
                    !filename.endsWith(".mp3")
                            && !filename.endsWith(".flac")
                            && !filename.endsWith(".m4a")
                            && !filename.endsWith(".aac")
                            && !filename.endsWith(".ogg")
            ) {
                continue;
            }
//            if (!filename.endsWith(".mp3")) {
//                continue;
//            }

            System.out.println(
                    "Scanning file: "
                            + file.getAbsolutePath()
            );

            createMusicEntry(file);
        }
    }

    private void createMusicEntry(
            File file
    ) {

        System.out.println(
                "Reading metadata: "
                        + file.getName()
        );
//        MediaItem existing =
//                MediaItem.find(
//                        "filename",
//                        file.getName()
//                ).firstResult();
        MediaItem existing =
                MediaItem.find(
                        "filePath",
                        file.getAbsolutePath()
                ).firstResult();

        if (existing != null) {
            return;
        }

        MusicMetadata metadata =
                metadataService.read(file);

        MediaItem item =
                new MediaItem();

        item.mediaType =
                "music";

        item.filename =
                file.getName();

        item.folderName =
                file.getParentFile().getName();

        item.title =
                metadata.title != null
                        ? metadata.title
                        : file.getName();

        item.artist =
                metadata.artist != null
                        ? metadata.artist
                        : "Unknown Artist";

        item.album =
                metadata.album != null
                        ? metadata.album
                        : "Unknown Album";

        item.trackNumber =
                metadata.trackNumber;

        item.durationMs =
                metadata.durationMs;
        item.filePath =
                file.getAbsolutePath();

        try {

            item.persistAndFlush();

        } catch (Exception e) {

            System.out.println(
                    "Already exists: "
                            + file.getAbsolutePath()
            );

            return;
        }

        System.out.println(
                "Created music item id = "
                        + item.id
        );

        try {

            String artwork =
                    metadataService.extractArtwork(
                            file,
                            item.id
                    );

            if (artwork != null) {

                item.poster =
                        "https://api.bluestreams.uk/music-art/"
                                + item.id
                                + ".jpg";

                item.persistAndFlush();
            }

        }
        catch (Exception e) {

            e.printStackTrace();

        }
    }
}