package com.spmf.iptv.account;

public record IptvCheckResult(
        IptvCheckOutcome outcome,
        Integer httpStatus,
        long latencyMs,
        String error
) {

    public enum IptvCheckOutcome {
        ACTIVE,
        DEAD,
        INCONCLUSIVE,
        EXPIRED
    }
}
