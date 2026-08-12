package com.spmf.iptv.config;


import com.spmf.tv.IPTVChannel;

import java.net.URL;

public interface IPTVResolver {

    URL resolve(
            IPTVChannel channel,
            IPTVConfig.Provider provider
    ) throws Exception;

}