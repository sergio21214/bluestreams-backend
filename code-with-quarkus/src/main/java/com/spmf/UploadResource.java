package com.spmf;

import jakarta.inject.Inject;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestForm;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Path("/upload")
public class UploadResource {

    @Inject
    ProcessingQueueService queueService;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(

            @RestForm("file")
            File file,

            @RestForm("fileName")
            String fileName) {

        try {

            File target =
                    new File("/mnt/bluestreams/SPMF/videos/" + fileName);

            Files.copy(
                    file.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            // BACKGROUND PROCESSING

            queueService.processVideo(fileName);

            return Response.ok(
                    "Upload successful"
            ).build();

        } catch (IOException e) {

            e.printStackTrace();

            return Response.serverError()
                    .build();
        }
    }
}