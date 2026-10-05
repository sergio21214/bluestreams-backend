package com.spmf;

import com.spmf.dto.Profile;

import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;

import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/admin/profiles")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminProfileResource {

    @GET
    public List<Profile> list() {

        return Profile.listAll();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(
            @PathParam("id")
            Long id
    ) {

        Profile.deleteById(id);
    }
}