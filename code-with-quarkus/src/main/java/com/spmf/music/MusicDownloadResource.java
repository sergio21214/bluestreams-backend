package com.spmf.music;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/music/download")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
public class MusicDownloadResource {

    @Inject
    MusicDownloadService downloadService;

    @POST
    public Response download(
            MusicDownloadRequest request
    ) {

        System.out.println(
                "DOWNLOAD HTTP POST RECEIVED: " +
                        System.currentTimeMillis()
        );

        if (
                request == null ||
                        request.url == null ||
                        request.url.isBlank()
        ) {

            return Response.status(
                            Response.Status.BAD_REQUEST
                    )
                    .entity(
                            """
                            {"error":"url is required"}
                            """
                    )
                    .build();
        }

        try {

            MusicDownloadBatchResponse response =
                    downloadService.createDownload(
                            request.url
                    );

            return Response.status(
                            Response.Status.ACCEPTED
                    )
                    .entity(response)
                    .build();

        } catch (IllegalArgumentException e) {

            return Response.status(
                            Response.Status.BAD_REQUEST
                    )
                    .entity(
                            """
                            {"error":"%s"}
                            """.formatted(
                                    escape(e.getMessage())
                            )
                    )
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    public Response status(
            @PathParam("id") Long id
    ) {

        MusicDownloadJob job =
                downloadService.getJob(
                        id
                );

        if (job == null) {

            return Response.status(
                    Response.Status.NOT_FOUND
            ).build();
        }

        return Response.ok(
                new MusicDownloadResponse(
                        job
                )
        ).build();
    }

    private String escape(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        "\\r"
                );
    }

    @GET
    @Path("/jobs")
    @RolesAllowed({"ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    public List<MusicDownloadJob> getDownloadJobs() {
        return MusicDownloadJob.find(
                "order by createdAt desc"
        ).page(0, 20).list();
    }
}