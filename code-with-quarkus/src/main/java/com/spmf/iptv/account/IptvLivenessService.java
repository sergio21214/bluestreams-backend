package com.spmf.iptv.account;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class IptvLivenessService {

    @Inject
    IptvSafeHttp safeHttp;

    @Inject
    IptvAccountStore store;

    @ConfigProperty(name = "iptv.liveness.concurrency", defaultValue = "8")
    int concurrency;

    @ConfigProperty(name = "iptv.liveness.failure-threshold", defaultValue = "2")
    int failureThreshold;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private ExecutorService background;
    private ExecutorService workers;

    @PostConstruct
    void init() {

        int size = Math.max(1, concurrency);

        this.background = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "iptv-liveness-trigger");
            thread.setDaemon(true);
            return thread;
        });

        this.workers = Executors.newFixedThreadPool(size, r -> {
            Thread thread = new Thread(r, "iptv-liveness-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {

        if (background != null) {
            background.shutdownNow();
        }

        if (workers != null) {
            workers.shutdownNow();
        }
    }

    public boolean triggerCheck() {

        if (!running.compareAndSet(false, true)) {
            return false;
        }

        background.submit(() -> {
            try {
                checkAll();
            } catch (Exception e) {
                Log.error("IPTV liveness check failed", e);
            } finally {
                running.set(false);
            }
        });

        return true;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void checkAll() {

        List<IptvAccountStore.Snapshot> snapshots = store.enabledSnapshots();

        AtomicInteger active = new AtomicInteger();
        AtomicInteger dead = new AtomicInteger();
        AtomicInteger unknown = new AtomicInteger();
        AtomicInteger expired = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>(snapshots.size());

        for (IptvAccountStore.Snapshot snapshot : snapshots) {

            tasks.add(() -> {

                IptvCheckResult result = check(snapshot);

                store.applyResult(
                        snapshot.id(),
                        result,
                        failureThreshold
                );

                switch (result.outcome()) {
                    case ACTIVE -> active.incrementAndGet();
                    case DEAD -> dead.incrementAndGet();
                    case EXPIRED -> expired.incrementAndGet();
                    default -> unknown.incrementAndGet();
                }

                return null;
            });
        }

        if (tasks.isEmpty()) {
            Log.info("IPTV liveness check: no enabled accounts");
            return;
        }

        try {

            List<Future<Void>> futures = workers.invokeAll(tasks);

            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    Log.warnf(
                            "IPTV liveness account update failed: %s",
                            e.getCause() == null
                                    ? e.getClass().getSimpleName()
                                    : e.getCause().getClass().getSimpleName()
                    );
                }
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            Log.warn("IPTV liveness check interrupted");
        }

        Log.infof(
                "IPTV liveness check finished: total=%d active=%d dead=%d unknown=%d expired=%d",
                snapshots.size(),
                active.get(),
                dead.get(),
                unknown.get(),
                expired.get()
        );
    }

    public IptvAccount checkOne(Long id) {

        IptvAccountStore.Snapshot snapshot = store.snapshot(id);

        if (snapshot == null) {
            return null;
        }

        IptvCheckResult result = check(snapshot);

        store.applyResult(id, result, failureThreshold);

        return store.find(id);
    }

    private IptvCheckResult check(IptvAccountStore.Snapshot snapshot) {

        if (snapshot.expiresAt() != null
                && snapshot.expiresAt().isBefore(LocalDate.now())) {

            return new IptvCheckResult(
                    IptvCheckResult.IptvCheckOutcome.EXPIRED,
                    null,
                    0L,
                    "expired"
            );
        }

        try {

            IptvSafeHttp.FetchResult response = safeHttp.fetch(snapshot.url());

            if (response.error() != null) {
                return new IptvCheckResult(
                        IptvCheckResult.IptvCheckOutcome.INCONCLUSIVE,
                        response.statusCode() == -1 ? null : response.statusCode(),
                        response.latencyMs(),
                        response.error()
                );
            }

            int code = response.statusCode();

            if (code == 401 || code == 403 || code == 404 || code == 410) {
                return new IptvCheckResult(
                        IptvCheckResult.IptvCheckOutcome.DEAD,
                        code,
                        response.latencyMs(),
                        "HTTP " + code
                );
            }

            if (code == 429 || code >= 500) {
                return new IptvCheckResult(
                        IptvCheckResult.IptvCheckOutcome.INCONCLUSIVE,
                        code,
                        response.latencyMs(),
                        "HTTP " + code
                );
            }

            if (code < 200 || code >= 300) {
                return new IptvCheckResult(
                        IptvCheckResult.IptvCheckOutcome.DEAD,
                        code,
                        response.latencyMs(),
                        "HTTP " + code
                );
            }

            if (!contentLooksValid(response, snapshot.type())) {
                return new IptvCheckResult(
                        IptvCheckResult.IptvCheckOutcome.DEAD,
                        code,
                        response.latencyMs(),
                        "unexpected response content"
                );
            }

            return new IptvCheckResult(
                    IptvCheckResult.IptvCheckOutcome.ACTIVE,
                    code,
                    response.latencyMs(),
                    null
            );

        } catch (IptvUrlGuard.BlockedUrlException e) {

            return new IptvCheckResult(
                    IptvCheckResult.IptvCheckOutcome.DEAD,
                    null,
                    0L,
                    "blocked: " + IptvUrlGuard.sanitizeError(e.getMessage())
            );

        } catch (Exception e) {

            return new IptvCheckResult(
                    IptvCheckResult.IptvCheckOutcome.INCONCLUSIVE,
                    null,
                    0L,
                    IptvUrlGuard.sanitizeError(
                            e.getClass().getSimpleName() + ": " + e.getMessage()
                    )
            );
        }
    }

    private boolean contentLooksValid(
            IptvSafeHttp.FetchResult response,
            String type
    ) {

        byte[] bytes = response.prefix();

        if (bytes == null || bytes.length == 0) {
            return false;
        }

        String contentType = IptvSafeHttp.normalizeContentType(
                response.contentType()
        );

        String text = new String(bytes, StandardCharsets.UTF_8);

        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }

        String trimmed = text.stripLeading();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        String normalizedType = type == null
                ? "UNKNOWN"
                : type.toUpperCase(Locale.ROOT);

        boolean textLike = normalizedType.equals("XTREAM")
                || normalizedType.equals("M3U")
                || normalizedType.equals("M3U8")
                || normalizedType.equals("XMLTV")
                || normalizedType.equals("UNKNOWN");

        if (textLike) {

            if (lower.startsWith("<html")
                    || lower.startsWith("<!doctype html")
                    || lower.contains("invalid username")
                    || lower.contains("user does not exist")
                    || lower.contains("access denied")
                    || lower.contains("not found")) {
                return false;
            }
        }

        if (trimmed.startsWith("#EXTM3U")) {
            return true;
        }

        return switch (normalizedType) {

            case "XTREAM", "M3U", "M3U8" ->
                    contentType.contains("mpegurl")
                            || contentType.contains("m3u");

            case "XMLTV" ->
                    lower.startsWith("<?xml")
                            || lower.contains("<tv")
                            || contentType.contains("xml");

            case "TS" ->
                    (bytes[0] & 0xff) == 0x47
                            || contentType.contains("mp2t")
                            || contentType.contains("mpeg");

            default ->
                    contentType.contains("mpegurl")
                            || contentType.contains("m3u")
                            || contentType.contains("xml")
                            || contentType.contains("mp2t")
                            || (trimmed.length() > 0
                            && !contentType.contains("html"));
        };
    }
}
