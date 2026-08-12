package com.spmf.iptv.client;

import com.spmf.iptv.config.IPTVConfig;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@ApplicationScoped
public class XtreamClient implements IPTVClient {

    @Override
    public InputStream downloadPlaylist(IPTVConfig.Provider provider) throws Exception {

        String url =
                provider.server().orElseThrow()
                        + "/get.php"
                        + "?username=" + provider.user().orElseThrow()
                        + "&password=" + provider.pass().orElseThrow()
                        + "&type=m3u_plus"
                        + "&output=ts";

        System.out.println(url);

        HttpURLConnection conn =
                (HttpURLConnection) new URL(url).openConnection();

        conn.setRequestProperty(
                "User-Agent",
                "VLC/3.0"
        );

        System.out.println("HTTP = " + conn.getResponseCode());
        System.out.println("CONTENT TYPE = " + conn.getContentType());

        return conn.getInputStream();
    }
}