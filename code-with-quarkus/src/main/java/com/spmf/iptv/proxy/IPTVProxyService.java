
package com.spmf.iptv.proxy;

import com.spmf.tv.IPTVChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

import java.net.HttpURLConnection;
import java.net.URL;

@ApplicationScoped
public class IPTVProxyService {

    public Response stream(

            Long channelId

    ) throws Exception {

        IPTVChannel channel =
                IPTVChannel.findById(channelId);

        if (channel == null) {

            return Response
                    .status(404)
                    .build();

        }

        URL url =
                new URL(
                        channel.streamUrl
                );

        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        connection.setRequestMethod("GET");

        connection.setConnectTimeout(10000);

        connection.setReadTimeout(120000);

        return Response.ok(

                        connection.getInputStream()

                )

                .header(
                        "Content-Type",
                        connection.getContentType()
                )

                .build();

    }

}