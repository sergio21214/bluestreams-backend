package com.spmf;

import com.spmf.dto.Profile;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminResource {

    @Inject
    ProcessingStore store;

    @Inject
    MediaScannerService scanner;

    @Inject
    MusicScannerService musicScanner;

    @Inject
    ProcessingQueueService queue;

    @GET
    @Path("/queue")
    public QueueStatus queue() {

        QueueStatus status =
                new QueueStatus();

        status.processing.addAll(
                store.processing
        );

        status.waiting.addAll(
                store.waiting
        );

        status.failed.addAll(
                store.failed
        );

        return status;
    }

    @POST
    @Path("/scan")
    public Response scan() {

        scanner.scanVideos();

        return Response.ok().build();
    }

    @POST
    @Path("/retry")
    public Response retry() {

        for (String file : store.failed) {

            queue.processVideo(file);
        }

        store.failed.clear();

        return Response.ok().build();
    }

    @POST
    @Path("/scan-music")
    public Response scanMusic() {

        musicScanner.scanMusic();

        return Response.ok(
                "Music scan complete"
        ).build();
    }

}