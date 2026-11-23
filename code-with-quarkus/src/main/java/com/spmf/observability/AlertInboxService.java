package com.spmf.observability;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class AlertInboxService {

    public record AlertRecord(
            String fingerprint,
            String name,
            String status,
            String severity,
            String source,
            String summary,
            String description,
            Instant startsAt,
            Instant endsAt,
            String labels
    ) {
    }

    @Transactional
    public void record(AlertRecord record) {

        String fingerprint =
                record.fingerprint() == null || record.fingerprint().isBlank()
                        ? fingerprint(record.name() + record.labels())
                        : record.fingerprint();

        SystemAlert alert =
                SystemAlert.find("fingerprint", fingerprint).firstResult();

        if (alert == null) {
            alert = new SystemAlert();
            alert.fingerprint = fingerprint;
        }

        alert.name = truncate(record.name(), 255);
        alert.status = truncate(
                record.status() == null ? "firing" : record.status(),
                32
        );
        alert.severity = truncate(
                record.severity() == null ? "warning" : record.severity(),
                32
        );
        alert.source = truncate(
                record.source() == null ? "unknown" : record.source(),
                32
        );
        alert.summary = truncate(record.summary(), 1024);
        alert.description = truncate(record.description(), 2000);
        alert.startsAt = record.startsAt();
        alert.endsAt = record.endsAt();
        alert.receivedAt = Instant.now();
        alert.labels = truncate(record.labels(), 2000);

        if (alert.id == null) {
            alert.persist();
        }
    }

    @Transactional
    public List<SystemAlert> recent(int limit) {

        return SystemAlert
                .find("order by receivedAt desc")
                .page(0, Math.max(1, limit))
                .list();
    }

    public static String fingerprint(String value) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    );

            StringBuilder builder =
                    new StringBuilder(hash.length * 2);

            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }

            return builder.toString();

        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String truncate(String value, int max) {

        if (value == null) {
            return null;
        }

        return value.length() > max
                ? value.substring(0, max)
                : value;
    }
}
