package com.spmf.iptv.provider;

import com.spmf.IPTVProvider;
import com.spmf.iptv.config.IPTVStreamResolver;
import com.spmf.tv.IPTVChannel;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URL;

@ApplicationScoped
public class M3UResolver
        implements IPTVStreamResolver {

    @Override
    public String resolve(
            IPTVProvider provider,
            IPTVChannel channel
    ) {

        return channel.streamUrl;

    }

}