package com.spmf.iptv;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.io.File;

@Path("/iptv/hlsfiles")
public class IPTVHLSFiles {

    @GET
    @Path("{channel}/{file}")
    public Response file(

            @PathParam("channel")
            String channel,

            @PathParam("file")
            String file

    ) {

        File f =
                new File("iptv-hls/" + channel + "/" + file);

        if (!f.exists())
            return Response.status(404).build();

        if (file.endsWith(".m3u8"))

            return Response.ok(f)
                    .type("application/vnd.apple.mpegurl")
                    .build();

        return Response.ok(f)
                .type("video/mp2t")
                .build();

    }

}