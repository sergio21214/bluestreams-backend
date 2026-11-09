package com.spmf.iptv;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Server-side downloads for Xtream series episodes and movies.
 *
 * The transfer runs on this machine and lands in the configured download
 * directory (default {@code /mnt/bluestreams/descargas/iptvdownloads}).
 *
 * <pre>
 * POST   /iptv/downloads                  start one download
 * POST   /iptv/downloads/batch            start several downloads
 * POST   /iptv/downloads/series           resolve &amp; queue an Xtream season
 * GET    /iptv/downloads/{jobId}          poll one job
 * GET    /iptv/downloads/batches          list batches
 * GET    /iptv/downloads/batches/{batchId} poll one batch
 * GET    /iptv/downloads                  list downloaded files
 * GET    /iptv/downloads?folder=...&amp;recursive=true
 * </pre>
 */
@Path("/iptv/downloads")
public class DownloadResource {

    @Inject
    DownloadService service;

    @Inject
    SeriesDownloadService seriesDownloadService;

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

    @POST
    @Path("/batch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startBatch(BatchDownloadRequest request) {

        if (request == null
                || request.items == null
                || request.items.isEmpty()) {
            return Response.status(400)
                    .entity(Map.of("error", "Items are required"))
                    .build();
        }

        try {
            DownloadBatch batch = service.startBatch(
                    request.folder,
                    request.items
            );

            return Response.accepted(batch).build();

        } catch (IllegalArgumentException e) {

            return Response.status(400)
                    .entity(Map.of("error", e.getMessage()))
                    .build();

        } catch (Exception e) {

            Log.error("Could not queue batch", e);

            return Response.status(500)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/series")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startSeries(SeriesDownloadRequest request) {

        if (request == null
                || request.accountId == null
                || request.seriesId == null) {
            return Response.status(400)
                    .entity(Map.of(
                            "error",
                            "AccountId and SeriesId are required"
                    ))
                    .build();
        }

        try {
            DownloadBatch batch = seriesDownloadService.start(request);
            return Response.accepted(batch).build();

        } catch (IllegalArgumentException e) {

            return Response.status(400)
                    .entity(Map.of("error", e.getMessage()))
                    .build();

        } catch (Exception e) {

            Log.error("Could not queue Xtream season", e);

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
    @Path("/batches")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listBatches() {
        return Response.ok(
                Map.of("batches", service.listBatches())
        ).build();
    }

    @GET
    @Path("/batches/{batchId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response batchStatus(@PathParam("batchId") String batchId) {

        BatchStatus status = service.batchStatus(batchId);

        if (status == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Unknown batch: " + batchId))
                    .build();
        }

        return Response.ok(status).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(
            @QueryParam("folder") String folder,
            @QueryParam("recursive")
            @DefaultValue("false") boolean recursive
    ) {
        return Response.ok(
                Map.of("files", service.list(folder, recursive))
        ).build();
    }
}
