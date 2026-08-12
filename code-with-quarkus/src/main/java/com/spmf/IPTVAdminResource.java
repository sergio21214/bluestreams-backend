package com.spmf;

import com.spmf.dto.IPTVImportRequest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/admin/iptv")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IPTVAdminResource {

    @Inject
    IPTVImportService importer;

    @POST
    @Path("/import-file")
    @Transactional
    public Response importFile(
            IPTVImportRequest request
    ) throws Exception {

        importer.importFromText(

                request.name,

                request.content

        );

        return Response.ok().build();

    }

    @POST
    @Path("/import-url")
    @Transactional
    public Response importUrl(String url)
            throws Exception {

        importer.importFromUrl("Uploaded Playlist",url, "","");

        return Response.ok().build();

    }

}