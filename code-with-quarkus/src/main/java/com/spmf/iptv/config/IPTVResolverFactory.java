package com.spmf.iptv.config;

import com.spmf.IPTVProvider;
import com.spmf.iptv.provider.M3UResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class IPTVResolverFactory {

    @Inject
    M3UResolver m3u;

    @Inject
    XtreamResolver xtream;

    public IPTVStreamResolver get(

            IPTVProvider provider

    ){

        return (IPTVStreamResolver) switch(provider.type){

            case "m3u" -> m3u;

            case "xtream" -> xtream;

            default ->
                    throw new RuntimeException();

        };

    }

}