package com.spmf.iptv.account;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "iptv_account",
        indexes = {
                @Index(name = "idx_iptv_account_status", columnList = "status"),
                @Index(name = "idx_iptv_account_expires_at", columnList = "expires_at")
        }
)
public class IptvAccount extends PanacheEntity {

    @Column(nullable = false, length = 4096)
    public String url;

    @Column(name = "url_hash", nullable = false, unique = true, length = 64)
    public String urlHash;

    @Column(length = 255)
    public String label;

    @Column(nullable = false, length = 16)
    public String type = "UNKNOWN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public IptvAccountStatus status = IptvAccountStatus.UNKNOWN;

    public boolean enabled = true;

    @Column(name = "expires_at")
    public LocalDate expiresAt;

    @Column(name = "last_checked_at")
    public LocalDateTime lastCheckedAt;

    @Column(name = "last_http_status")
    public Integer lastHttpStatus;

    @Column(name = "latency_ms")
    public Long latencyMs;

    @Column(name = "last_error", length = 512)
    public String lastError;

    @Column(name = "consecutive_failures", nullable = false)
    public int consecutiveFailures = 0;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();
}
