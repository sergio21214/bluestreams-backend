package com.spmf.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class MusicIptvMetrics {

    @Inject
    MeterRegistry registry;

    @Inject
    SystemStatsService statsService;

    private final AtomicLong musicQueued = new AtomicLong();
    private final AtomicLong musicDownloading = new AtomicLong();
    private final AtomicLong musicProcessing = new AtomicLong();
    private final AtomicLong musicCompleted = new AtomicLong();
    private final AtomicLong musicFailed = new AtomicLong();

    private final AtomicLong iptvActive = new AtomicLong();
    private final AtomicLong iptvDead = new AtomicLong();
    private final AtomicLong iptvExpired = new AtomicLong();
    private final AtomicLong iptvUnknown = new AtomicLong();
    private final AtomicLong iptvDefaultActive = new AtomicLong();

    @PostConstruct
    void init() {

        gauge("music.jobs", "QUEUED", musicQueued);
        gauge("music.jobs", "DOWNLOADING", musicDownloading);
        gauge("music.jobs", "PROCESSING", musicProcessing);
        gauge("music.jobs", "COMPLETED", musicCompleted);
        gauge("music.jobs", "FAILED", musicFailed);

        gauge("iptv.accounts", "ACTIVE", iptvActive);
        gauge("iptv.accounts", "DEAD", iptvDead);
        gauge("iptv.accounts", "EXPIRED", iptvExpired);
        gauge("iptv.accounts", "UNKNOWN", iptvUnknown);

        Gauge.builder(
                        "iptv.default_active",
                        iptvDefaultActive,
                        AtomicLong::doubleValue
                )
                .register(registry);
    }

    @Scheduled(every = "30s")
    void refresh() {

        SystemStatsService.DbStats stats = statsService.load();

        musicQueued.set(stats.music().queued());
        musicDownloading.set(stats.music().downloading());
        musicProcessing.set(stats.music().processing());
        musicCompleted.set(stats.music().completed());
        musicFailed.set(stats.music().failed());

        iptvActive.set(stats.iptv().active());
        iptvDead.set(stats.iptv().dead());
        iptvExpired.set(stats.iptv().expired());
        iptvUnknown.set(stats.iptv().unknown());
        iptvDefaultActive.set(stats.iptv().defaultActive() ? 1 : 0);
    }

    private void gauge(
            String name,
            String status,
            AtomicLong value
    ) {

        Gauge.builder(name, value, AtomicLong::doubleValue)
                .tags("status", status)
                .register(registry);
    }
}
