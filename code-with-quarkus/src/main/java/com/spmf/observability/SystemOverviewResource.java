package com.spmf.observability;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/admin/system")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
public class SystemOverviewResource {

    @Inject
    SystemOverviewService service;

    @GET
    @Path("/overview")
    public SystemOverview overview() {
        return service.overview();
    }
}
