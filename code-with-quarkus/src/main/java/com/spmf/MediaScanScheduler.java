package com.spmf;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MediaScanScheduler {

    @Inject
    MediaScannerService scanner;

    @Scheduled(every = "30m")
    void scanLibrary() {

        System.out.println(
                "RUNNING SCHEDULED MEDIA SCAN"
        );

        scanner.scanVideos();
    }
}