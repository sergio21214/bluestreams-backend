package com.spmf.iptv.config;

import io.smallrye.config.ConfigMapping;

import java.util.List;
import java.util.Optional;

//@ApplicationScoped
@ConfigMapping(prefix = "iptv")
public interface IPTVConfig {

    List<Provider> providers();

    interface Provider {

        String name();

        Optional<String> url();

        Optional<String> server();

        Optional<String> user();

        Optional<String> pass();

        Optional<String> type();

    }

}