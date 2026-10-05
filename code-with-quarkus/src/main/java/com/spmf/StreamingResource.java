package com.spmf;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.io.File;

@Path("/stream")
public class StreamingResource {

    @GET
    @Path("/{filename}")
    @Produces("video/mp4")
    public Response stream(@PathParam("filename") String filename) {

        File video = new File("/mnt/bluestreams/SPMF/videos/" + filename);

        if (!video.exists()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(video)
                .header("Accept-Ranges", "bytes")
                .build();
    }
}