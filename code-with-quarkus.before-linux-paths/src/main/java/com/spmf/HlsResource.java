package com.spmf;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.io.File;

@Path("/hls")
public class HlsResource {

    @GET
    @Path("/{movie}/{fileName}")
    @Produces({
            "application/vnd.apple.mpegurl",
            "video/mp2t"
    })
    public Response stream(
            @PathParam("movie") String movie,
            @PathParam("fileName") String fileName) {

        File file =
                new File("G:/SPMF/hls/" + movie + "/" + fileName);

        if (!file.exists()) {
            return Response.status(404).build();
        }

        return Response.ok(file)
                .header("Accept-Ranges", "bytes")
                .build();
    }
}