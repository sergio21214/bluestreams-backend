package com.spmf.iptv.config;

import com.spmf.IPTVProvider;
import com.spmf.tv.IPTVChannel;

import java.net.URL;


public interface IPTVStreamResolver {

    String resolve(
            IPTVProvider provider,
            IPTVChannel channel
    ) throws Exception;
}