package com.spmf.observability;

import java.time.Instant;
import java.util.List;

public record SystemOverview(
        Instant generatedAt,
        Health health,
        Metrics metrics,
        List<ServiceStatus> services,
        MusicStats music,
        IptvStats iptv,
        DiskStats disk,
        List<Alert> alerts,
        Links links
) {

    public record Health(
            String status,
            List<HealthCheck> checks
    ) {
    }

    public record HealthCheck(
            String name,
            String status
    ) {
    }

    public record Metrics(
            boolean prometheusAvailable,
            Double requestsPerSecond,
            Double errorsPerSecond,
            Double errorRatePercent,
            Double p95LatencyMs,
            Double p99LatencyMs,
            Double heapUsedBytes,
            Double heapMaxBytes,
            Double processCpu,
            Double systemCpu
    ) {
    }

    public record ServiceStatus(
            String job,
            boolean up
    ) {
    }

    public record MusicStats(
            long queued,
            long downloading,
            long processing,
            long completed,
            long failed
    ) {
    }

    public record IptvStats(
            long total,
            long active,
            long dead,
            long expired,
            long unknown,
            boolean defaultActive
    ) {
    }

    public record DiskStats(
            Long totalBytes,
            Long usableBytes,
            Double usedPercent
    ) {
    }

    public record Alert(
            Long id,
            String name,
            String status,
            String severity,
            String source,
            String summary,
            String description,
            Instant startsAt,
            Instant endsAt,
            Instant receivedAt
    ) {
    }

    public record Links(
            String grafana,
            String uptimeKuma
    ) {
    }
}
