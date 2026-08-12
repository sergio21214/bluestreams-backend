package com.spmf.iptv.config;


import com.spmf.IPTVProvider;
import com.spmf.tv.IPTVChannel;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class M3UResolver implements IPTVStreamResolver{

    @Override
    public String resolve(
            IPTVProvider provider,
            IPTVChannel channel
    ) throws Exception {

        return new String(
                channel.streamUrl
        );

    }


}