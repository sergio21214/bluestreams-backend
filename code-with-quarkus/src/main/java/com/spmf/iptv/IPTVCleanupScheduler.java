package com.spmf.iptv;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class IPTVCleanupScheduler {

    @jakarta.inject.Inject
    IPTVHLSSessionManager manager;

    @Scheduled(every = "30s")
    void cleanup() {

        Instant now = Instant.now();

        manager.getAll().forEach(session -> {

            long seconds =
                    Duration.between(
                            session.lastAccess,
                            now
                    ).toSeconds();

            if (seconds > 60) {

                System.out.println(
                        "Stopping channel "
                                + session.channelId
                );

                if (session.process != null) {

                    session.process.destroyForcibly();

                }

                delete(session.folder);

                manager.remove(session.channelId);

            }

        });

    }

    private void delete(File file) {

        if (file == null || !file.exists())
            return;

        if (file.isDirectory()) {

            File[] files = file.listFiles();

            if (files != null) {

                for (File child : files) {

                    delete(child);

                }

            }

        }

        file.delete();

    }

}