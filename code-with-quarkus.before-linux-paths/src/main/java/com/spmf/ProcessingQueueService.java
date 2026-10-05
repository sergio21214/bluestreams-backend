package com.spmf;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class ProcessingQueueService {

    @Inject
    ProcessingStore store;

    private final ExecutorService executor =
            Executors.newFixedThreadPool(2);

    public void processVideo(
            String inputPath
    ) {

        store.processing.add(inputPath);
        

        executor.submit(() -> {

            try {

                File videoFile =
                        new File(inputPath);

                String videoFilename =
                        videoFile.getName();

                String folderName =
                        FilenameUtils.cleanName(
                                videoFilename
                        );

                System.out.println(
                        "START THUMBNAIL"
                );

                ThumbnailService.generateThumbnail(
                        inputPath
                );

                System.out.println(
                        "START SUBTITLES"
                );

                SubtitleService.extractSubtitles(
                        inputPath,
                        folderName
                );

                System.out.println(
                        "START DASH"
                );

                HlsService.generateHls(
                        inputPath,
                        folderName
                );

                System.out.println(
                        "FINISHED PROCESSING"
                );

            } catch (Exception e) {

                System.out.println(
                        "QUEUE ERROR"
                );

                e.printStackTrace();

            } finally {

                store.processing.remove(
                        inputPath
                );
            }
        });
    }
}