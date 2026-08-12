package com.spmf.iptv.config;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ResolverFactory {

    @Inject
    M3UResolver m3uResolver;

    @Inject
    XtreamResolver xtreamResolver;

    public IPTVStreamResolver get(
            IPTVConfig.Provider provider
    ) {

        if (provider.user() == null ||
                provider.user().isEmpty()) {

            return m3uResolver;

        }

        return xtreamResolver;

    }

}