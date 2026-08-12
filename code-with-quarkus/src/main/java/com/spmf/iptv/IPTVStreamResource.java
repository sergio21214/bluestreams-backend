package com.spmf.iptv;

import com.spmf.IPTVProvider;
import com.spmf.iptv.config.IPTVResolverFactory;
import com.spmf.iptv.config.IPTVStreamResolver;
import com.spmf.tv.IPTVChannel;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.net.HttpURLConnection;
import java.net.URL;

@Path("/iptv/stream")
public class IPTVStreamResource {

    @Inject
    IPTVResolverFactory resolverFactory;

    @GET
    @Path("/{id}")
    public Response stream(
            @PathParam("id") Long id
    ) throws Exception {

        IPTVChannel channel =
                IPTVChannel.findById(id);

        IPTVProvider provider =
                IPTVProvider.findById(channel.providerId);

        IPTVStreamResolver resolver =
                resolverFactory.get(provider);

        String realUrl =
                resolver.resolve(
                        provider, channel
                );

        HttpURLConnection conn =
                (HttpURLConnection)
                        new URL(realUrl).openConnection();

        conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0"
        );

        return Response.ok(
                        conn.getInputStream()
                )
                .type(conn.getContentType())
                .build();

    }

}