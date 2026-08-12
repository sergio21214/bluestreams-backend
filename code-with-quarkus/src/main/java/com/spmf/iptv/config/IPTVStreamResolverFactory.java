package com.spmf.iptv.config;

import com.spmf.IPTVProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class IPTVStreamResolverFactory {

    @Inject
    M3UResolver m3u;

    @Inject
    XtreamResolver xtream;

    public IPTVStreamResolver get(IPTVProvider provider) {

        if ("xtream".equalsIgnoreCase(provider.type))
            return xtream;

        return m3u;
    }

}