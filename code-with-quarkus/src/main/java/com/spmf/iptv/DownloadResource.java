package com.spmf.iptv;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.List;
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

    @GET
    @Path("/library")
    @Produces(MediaType.APPLICATION_JSON)
    public Response library(
            @QueryParam("folder") String folder,
            @QueryParam("includeAll")
            @DefaultValue("false") boolean includeAll
    ) {

        List<DownloadedFile> files =
                service.listLibrary(folder, includeAll);

        return Response.ok(
                Map.of(
                        "count", files.size(),
                        "files", files
                )
        ).build();
    }

    /**
     * Streams a downloaded file directly, with Range support for seeking.
     * This is for clients that can play the raw file (MP4/MKV/etc.) without HLS.
     */
    @GET
    @Path("/file")
    public Response file(
            @QueryParam("path") String path,
            @HeaderParam("Range") String range
    ) {

        java.nio.file.Path file = service.resolveDownloadedFile(path);

        if (file == null) {
            return Response.status(404).build();
        }

        long length;

        try {
            length = Files.size(file);
        } catch (IOException e) {
            return Response.status(404).build();
        }

        if (length <= 0) {
            return Response.status(404).build();
        }

        ByteRange requested = parseRange(range, length);

        if (range != null && requested == null) {
            return Response.status(416)
                    .header("Content-Range", "bytes */" + length)
                    .build();
        }

        long start = requested == null ? 0 : requested.start();
        long end = requested == null ? length - 1 : requested.end();
        long contentLength = end - start + 1;

        StreamingOutput output = stream -> {

            try (RandomAccessFile raf =
                         new RandomAccessFile(file.toFile(), "r")) {

                raf.seek(start);

                byte[] buffer = new byte[64 * 1024];
                long remaining = contentLength;

                while (remaining > 0) {

                    int read = raf.read(
                            buffer,
                            0,
                            (int) Math.min(buffer.length, remaining)
                    );

                    if (read < 0) {
                        break;
                    }

                    stream.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        };

        String filename = DownloadService.sanitize(
                file.getFileName().toString()
        );

        Response.ResponseBuilder builder = Response
                .status(requested == null ? 200 : 206)
                .entity(output)
                .type(DownloadService.contentTypeFor(file))
                .header("Accept-Ranges", "bytes")
                .header("Content-Length", contentLength)
                .header(
                        "Content-Disposition",
                        "inline; filename=\"" + filename + "\""
                );

        if (requested != null) {
            builder.header(
                    "Content-Range",
                    "bytes " + start + "-" + end + "/" + length
            );
        }

        return builder.build();
    }

    @HEAD
    @Path("/file")
    public Response fileHead(@QueryParam("path") String path) {

        java.nio.file.Path file = service.resolveDownloadedFile(path);

        if (file == null) {
            return Response.status(404).build();
        }

        try {

            long length = Files.size(file);

            return Response.ok()
                    .type(DownloadService.contentTypeFor(file))
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Length", length)
                    .build();

        } catch (IOException e) {
            return Response.status(404).build();
        }
    }

    private ByteRange parseRange(String header, long length) {

        if (header == null || header.isBlank()) {
            return null;
        }

        if (!header.startsWith("bytes=")) {
            return null;
        }

        String spec = header.substring("bytes=".length()).trim();

        if (spec.isEmpty() || spec.contains(",")) {
            return null;
        }

        String[] parts = spec.split("-", -1);

        if (parts.length != 2) {
            return null;
        }

        try {

            long start;
            long end;

            if (parts[0].isEmpty()) {

                long suffix = Long.parseLong(parts[1]);

                if (suffix <= 0) {
                    return null;
                }

                start = Math.max(0, length - suffix);
                end = length - 1;

            } else {

                start = Long.parseLong(parts[0]);
                end = parts[1].isEmpty()
                        ? length - 1
                        : Long.parseLong(parts[1]);
            }

            if (start < 0 || start >= length || end < start) {
                return null;
            }

            end = Math.min(end, length - 1);

            return new ByteRange(start, end);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ByteRange(long start, long end) {
    }
}
