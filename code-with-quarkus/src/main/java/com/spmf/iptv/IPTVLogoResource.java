package com.spmf.iptv;

import com.spmf.tv.IPTVChannel;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Path("/iptv/logo")
public class IPTVLogoResource {

    @GET
    @Path("/{id}")
    public Response logo(
            @PathParam("id") Long id
    ) throws Exception {

        IPTVChannel channel =
                IPTVChannel.findById(id);

        if(channel == null)
            return Response.status(404).build();

        URL url =
                new URL(channel.logo);

        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();

        conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0"
        );

        InputStream in =
                conn.getInputStream();

        return Response.ok(in)
                .type(conn.getContentType())
                .build();

    }

}