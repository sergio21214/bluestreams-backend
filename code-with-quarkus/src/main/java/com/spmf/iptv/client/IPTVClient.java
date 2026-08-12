package com.spmf.iptv.client;

import com.spmf.iptv.config.IPTVConfig;

import java.io.InputStream;

public interface IPTVClient {

    InputStream downloadPlaylist(
            IPTVConfig.Provider provider
    ) throws Exception;

}