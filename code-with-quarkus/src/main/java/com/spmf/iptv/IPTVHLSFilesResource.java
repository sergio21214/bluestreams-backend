package com.spmf.iptv;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.io.File;

@Path("/iptv/hls")
public class IPTVHLSFilesResource {

    @Inject
    IPTVHLSSessionManager manager;

    @GET
    @Path("/{channelId}/{fileName}")
    public Response file(

            @PathParam("channelId")
            Long channelId,

            @PathParam("fileName")
            String fileName

    ) {
        IPTVHLSSession session =
                manager.get(channelId);

        if (session != null) {
            session.lastAccess = java.time.Instant.now();
        }

        File file =
                new File(
                        "iptv-hls/"
                                + channelId
                                + "/"
                                + fileName
                );

        if (!file.exists()) {

            return Response.status(404).build();

        }

        String type;

        if (fileName.endsWith(".m3u8")) {

            type =
                    "application/vnd.apple.mpegurl";

        } else {

            type =
                    "video/mp2t";

        }

        return Response.ok(file)
                .type(type)
                .header(
                        "Cache-Control",
                        "no-cache"
                )
                .build();

    }

}