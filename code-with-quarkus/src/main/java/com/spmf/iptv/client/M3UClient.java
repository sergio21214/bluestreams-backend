package com.spmf.iptv.client;

import com.spmf.iptv.config.IPTVConfig;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;



import com.spmf.iptv.config.IPTVConfig;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@ApplicationScoped
public class M3UClient implements IPTVClient {

    @Override
    public InputStream downloadPlaylist(
            IPTVConfig.Provider provider
    ) throws Exception {

        String url = provider.url()
                .orElseThrow(() ->
                        new RuntimeException("Provider has no URL"));

        HttpURLConnection conn =
                (HttpURLConnection)
                        new URL(url).openConnection();

        conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0"
        );

        conn.setRequestProperty(
                "Accept",
                "*/*"
        );

        if(provider.server().isPresent()) {

            conn.setRequestProperty(
                    "Referer",
                    provider.server().get()
            );

            conn.setRequestProperty(
                    "Origin",
                    provider.server().get()
            );

        }

        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        return conn.getInputStream();

    }

}