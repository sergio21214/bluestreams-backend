package com.spmf.iptv.config;

import com.spmf.IPTVProvider;
import com.spmf.iptv.provider.SessionManager;
import com.spmf.tv.IPTVChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URL;


import jakarta.enterprise.context.ApplicationScoped;

import java.net.URL;


@ApplicationScoped
public class XtreamResolver
        implements IPTVStreamResolver {

    @Override
    public String resolve(
            IPTVProvider provider,
            IPTVChannel channel
    ) {

        return provider.url
                + "/live/"
                + provider.username
                + "/"
                + provider.password
                + "/"
                + channel.providerChannelId
                + ".ts";

    }

}