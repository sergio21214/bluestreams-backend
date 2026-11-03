package com.spmf.iptv.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

//@ApplicationScoped
@ConfigMapping(prefix = "iptv")
public interface IPTVConfig {

    List<Provider> providers();

    Liveness liveness();

    Accounts accounts();

    interface Provider {

        String name();

        Optional<String> url();

        Optional<String> server();

        Optional<String> user();

        Optional<String> pass();

        Optional<String> type();

    }

    interface Liveness {

        @WithDefault("true")
        boolean enabled();

        @WithDefault("6h")
        String interval();

        @WithDefault("8")
        int concurrency();

        @WithDefault("2")
        int failureThreshold();

        @WithDefault("5")
        int connectTimeoutSeconds();

        @WithDefault("10")
        int readTimeoutSeconds();

        @WithDefault("20")
        int callTimeoutSeconds();

        @WithDefault("65536")
        int maxBytes();

        @WithDefault("3")
        int maxRedirects();

        @WithDefault("VLC/3.0.20 LibVLC/3.0.20")
        String userAgent();

    }

    interface Accounts {

        Active active();

        interface Active {

            @WithDefault("ADMIN")
            String roles();

        }
    }

}