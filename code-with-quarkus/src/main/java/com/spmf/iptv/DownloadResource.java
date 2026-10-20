package com.spmf.iptv;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Server-side downloads for Xtream series episodes and movies.
 *
 * The Android app asks for a download; the transfer runs on this machine and the file
 * lands in {@code /mnt/bluestreams/descargas/iptvdownloads}. The app never carries the
 * bytes - it only gets a job id and a filename to poll.
 *
 * <pre>
 * POST   /iptv/downloads              start a download        -> 202 { jobId, filename, ... }
 * GET    /iptv/downloads/{jobId}      poll job status         -> { status, bytesWritten, ... }
 * GET    /iptv/downloads              list downloaded files   -> { files: [...] }
 * </pre>
 */
@Path("/iptv/downloads")
public class DownloadResource {

    @Inject
    DownloadService service;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response start(DownloadRequest request) {

        if (request == null || request.url == null || request.url.isBlank()) {
            return Response.status(400)
                    .entity(Map.of("error", "Url is required"))
                    .build();
        }

        try {
            DownloadJob job = service.start(request);
            return Response.accepted(job).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            Log.error("Could not queue download", e);
            return Response.status(500)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{jobId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status(@PathParam("jobId") String jobId) {

        DownloadJob job = service.status(jobId);

        if (job == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Unknown job: " + jobId))
                    .build();
        }

        return Response.ok(job).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list() {
        return Response.ok(Map.of("files", service.list())).build();
    }
}
