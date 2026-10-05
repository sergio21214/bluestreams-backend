package com.spmf;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.io.File;

@Path("/audio")
public class AudioResource {

    @GET
    @Path("/{id}")
    public Response stream(
            @PathParam("id")
            Long id
    ) {

        MediaItem item =
                MediaItem.findById(id);

        if (item == null) {

            return Response
                    .status(404)
                    .build();
        }

        File audio =
                new File(
                        "/mnt/bluestreams/SPMF/media/music/"
                                + item.folderName
                                + "/"
                                + item.filename
                );

        if (!audio.exists()) {

            return Response
                    .status(404)
                    .build();
        }

        return Response.ok(audio)
                .header(
                        "Accept-Ranges",
                        "bytes"
                )
                .build();
    }
}