package com.spmf.iptv.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class IptvAccountStore {

    public record Snapshot(
            Long id,
            String url,
            String type,
            LocalDate expiresAt
    ) {
    }

    public record ImportResult(
            int total,
            int inserted,
            int updated
    ) {
    }

    @Transactional
    public ImportResult importParsed(List<IptvAccountParser.ParsedAccount> parsed) {

        int inserted = 0;
        int updated = 0;
        LocalDateTime now = LocalDateTime.now();

        for (IptvAccountParser.ParsedAccount entry : parsed) {

            String hash = sha256(entry.url());

            IptvAccount account = IptvAccount
                    .find("urlHash", hash)
                    .firstResult();

            if (account == null) {

                account = new IptvAccount();
                account.url = entry.url();
                account.urlHash = hash;
                account.createdAt = now;
                inserted++;

            } else {
                updated++;
            }

            account.label = entry.label();
            account.type = entry.type();
            account.expiresAt = entry.expiresAt();
            account.updatedAt = now;

            if (account.expiresAt != null
                    && account.expiresAt.isBefore(LocalDate.now())) {
                account.status = IptvAccountStatus.EXPIRED;
            } else if (account.status == IptvAccountStatus.EXPIRED) {
                account.status = IptvAccountStatus.UNKNOWN;
            }

            if (account.id == null) {
                account.persist();
            }
        }

        return new ImportResult(parsed.size(), inserted, updated);
    }

    @Transactional
    public void applyResult(
            Long id,
            IptvCheckResult result,
            int failureThreshold
    ) {

        IptvAccount account = IptvAccount.findById(id);

        if (account == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        account.lastCheckedAt = now;
        account.lastHttpStatus = result.httpStatus();
        account.latencyMs = result.latencyMs();
        account.lastError = result.error();
        account.updatedAt = now;

        switch (result.outcome()) {

            case ACTIVE -> {
                account.status = IptvAccountStatus.ACTIVE;
                account.consecutiveFailures = 0;
            }

            case DEAD -> {
                account.status = IptvAccountStatus.DEAD;
                account.consecutiveFailures =
                        account.consecutiveFailures + 1;
            }

            case INCONCLUSIVE -> {
                account.consecutiveFailures =
                        account.consecutiveFailures + 1;

                if (account.consecutiveFailures >= failureThreshold) {
                    account.status = IptvAccountStatus.DEAD;
                }
            }

            case EXPIRED -> account.status = IptvAccountStatus.EXPIRED;
        }

        if (account.expiresAt != null
                && account.expiresAt.isBefore(LocalDate.now())) {
            account.status = IptvAccountStatus.EXPIRED;
        }

        if (account.id == null) {
            account.persist();
        }
    }

    @Transactional
    public List<Snapshot> enabledSnapshots() {

        List<IptvAccount> accounts = IptvAccount.list("enabled = true");
        List<Snapshot> snapshots = new ArrayList<>(accounts.size());

        for (IptvAccount account : accounts) {
            snapshots.add(new Snapshot(
                    account.id,
                    account.url,
                    account.type,
                    account.expiresAt
            ));
        }

        return snapshots;
    }

    @Transactional
    public Snapshot snapshot(Long id) {

        IptvAccount account = IptvAccount.findById(id);

        if (account == null) {
            return null;
        }

        return new Snapshot(
                account.id,
                account.url,
                account.type,
                account.expiresAt
        );
    }

    @Transactional
    public IptvAccount find(Long id) {
        return IptvAccount.findById(id);
    }

    @Transactional
    public List<IptvAccount> list(String status) {

        if (status == null || status.isBlank()) {
            return IptvAccount.listAll();
        }

        IptvAccountStatus parsed = IptvAccountStatus.valueOf(
                status.trim().toUpperCase(Locale.ROOT)
        );

        return IptvAccount.find("status", parsed).list();
    }

    @Transactional
    public List<IptvAccount> listActive() {
        return IptvAccount.find(
                "status = ?1 and enabled = true",
                IptvAccountStatus.ACTIVE
        ).list();
    }

    @Transactional
    public List<IptvAccount> activeForApp() {
        return IptvAccount.find(
                "status = ?1 and enabled = true and isDefault = true",
                IptvAccountStatus.ACTIVE
        ).list();
    }

    @Transactional
    public IptvAccount setDefault(Long id) {

        IptvAccount account = IptvAccount.findById(id);

        if (account == null) {
            return null;
        }

        IptvAccount.update("isDefault = false where isDefault = true");

        account.isDefault = true;

        return account;
    }

    @Transactional
    public Map<String, Long> stats() {

        Map<String, Long> result = new LinkedHashMap<>();

        result.put("total", IptvAccount.count());
        result.put("active", IptvAccount.count("status", IptvAccountStatus.ACTIVE));
        result.put("dead", IptvAccount.count("status", IptvAccountStatus.DEAD));
        result.put("unknown", IptvAccount.count("status", IptvAccountStatus.UNKNOWN));
        result.put("expired", IptvAccount.count("status", IptvAccountStatus.EXPIRED));

        return result;
    }

    public static String sha256(String value) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder builder = new StringBuilder(hash.length * 2);

            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }

            return builder.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
