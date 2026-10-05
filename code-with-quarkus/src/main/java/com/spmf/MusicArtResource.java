package com.spmf;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.io.File;

@Path("/music-art")
public class MusicArtResource {

    @GET
    @Path("/{file}")
    public Response image(
            @PathParam("file") String file
    ) {

        String fullPath =
                "/mnt/bluestreams/SPMF/media/music/music-art/" + file;

        System.out.println("Looking for: " + fullPath);

        File image = new File(fullPath);

        System.out.println(
                "Exists: " + image.exists()
        );

        if (!image.exists()) {

            return Response.status(404)
                    .entity(fullPath)
                    .build();
        }

        return Response.ok(image)
                .header("Content-Type", "image/jpeg")
                .build();
    }
}