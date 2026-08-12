package com.spmf.iptv.client;

import com.spmf.iptv.config.IPTVConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class IPTVClientFactory {

    @Inject
    M3UClient m3uClient;

    @Inject
    XtreamClient xtreamClient;

    public IPTVClient get(
            IPTVConfig.Provider provider
    ) {

        switch (
                provider.type().orElse("m3u").toLowerCase()
        ) {

            case "m3u":
                return m3uClient;

            case "xtream":
                return xtreamClient;

            default:

                throw new RuntimeException(
                        "Unknown IPTV provider type: "
                                + provider.type()
                );

        }

    }

}