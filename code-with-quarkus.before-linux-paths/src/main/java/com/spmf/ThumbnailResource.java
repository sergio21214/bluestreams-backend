package com.spmf;


import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.io.File;

@Path("/thumbnails")
public class ThumbnailResource {

    @GET
    @Path("/{filename}")
    @Produces("image/jpeg")
    public Response thumbnail(
            @PathParam("filename") String filename) {

        File image =
                new File("G:/SPMF/thumbnails/" + filename);

        if (!image.exists()) {
            return Response.status(404).build();
        }

        return Response.ok(image).build();
    }
}