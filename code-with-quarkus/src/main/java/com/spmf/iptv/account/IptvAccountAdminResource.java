package com.spmf.iptv.account;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/admin/iptv/accounts")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
public class IptvAccountAdminResource {

    @Inject
    IptvAccountImportService importService;

    @Inject
    IptvLivenessService livenessService;

    @Inject
    IptvAccountStore store;

    @POST
    @Path("/import")
    @Consumes(MediaType.TEXT_PLAIN)
    public IptvAccountStore.ImportResult importText(String content) {

        if (content == null || content.isBlank()) {
            throw new BadRequestException("empty playlist text");
        }

        return importService.importText(content);
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.APPLICATION_JSON)
    public IptvAccountStore.ImportResult importJson(
            IptvImportTextRequest request
    ) {

        if (request == null || request.content == null || request.content.isBlank()) {
            throw new BadRequestException("empty playlist text");
        }

        return importService.importText(request.content);
    }

    @POST
    @Path("/check")
    public Response checkAll() {

        boolean started = livenessService.triggerCheck();

        if (!started) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("started", false, "reason", "already running"))
                    .build();
        }

        return Response.status(Response.Status.ACCEPTED)
                .entity(Map.of("started", true))
                .build();
    }

    @POST
    @Path("/{id}/check")
    public Response checkOne(@PathParam("id") Long id) {

        IptvAccount account = livenessService.checkOne(id);

        if (account == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(account).build();
    }

    @PUT
    @Path("/{id}/default")
    public Response setDefault(@PathParam("id") Long id) {

        IptvAccount account = store.setDefault(id);

        if (account == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(account).build();
    }

    @GET
    public List<IptvAccount> list(@QueryParam("status") String status) {

        try {
            return store.list(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("invalid status: " + status);
        }
    }

    @GET
    @Path("/stats")
    public Map<String, Long> stats() {
        return store.stats();
    }
}
