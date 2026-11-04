package com.spmf.iptv.account;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActiveIptvAccount(
        Long id,
        String label,
        String type,
        String url,
        LocalDate expiresAt,
        LocalDateTime lastCheckedAt,
        Long latencyMs,
        @JsonProperty("isDefault") boolean isDefault
) {
}
