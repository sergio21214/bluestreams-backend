package com.spmf.iptv.account;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class IptvAccountCheckScheduler {

    @Inject
    IptvLivenessService livenessService;

    @ConfigProperty(name = "iptv.liveness.enabled", defaultValue = "true")
    boolean enabled;

    @Scheduled(
            every = "${iptv.liveness.interval:6h}",
            identity = "iptv-account-liveness"
    )
    void checkAccounts() {

        if (!enabled) {
            return;
        }

        if (!livenessService.triggerCheck()) {
            Log.debug("IPTV liveness check already running, skipping");
        }
    }
}
