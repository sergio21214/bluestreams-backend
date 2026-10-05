package com.spmf;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class StartupService {

    void onStart(
            @Observes StartupEvent ev,
            MediaScannerService scanner
    ) {

        System.out.println(
                "STARTUP SCAN"
        );

        scanner.scanVideos();
    }
}