package com.spmf;


import com.spmf.dto.CreateUserRequest;
import com.spmf.dto.Profile;
import com.spmf.dto.UpdatePasswordRequest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;

@Path("/admin/users")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminUserResource {

    @GET
    public List<User> list() {

        return User.listAll();
    }

    @POST
    @Transactional
    public Response create(
            CreateUserRequest request
    ) {

        User user =
                new User();

        user.username =
                request.username;

        user.email =
                request.email;

        user.passwordHash =
                BCrypt.hashpw(
                        request.password,
                        BCrypt.gensalt()
                );

        user.role =
                request.role == null
                        ? "USER"
                        : request.role;

        user.enabled =
                true;

        // SAVE USER FIRST
        user.persistAndFlush();

        System.out.println(
                "Created user id = "
                        + user.id
        );

        // CREATE DEFAULT PROFILE

        Profile profile =
                new Profile();

        profile.userId =
                user.id;

        profile.profileName =
                user.username;

        profile.isKidsProfile =
                false;

        profile.persist();

        return Response.ok(user)
                .build();
    }

    @PUT
    @Path("/{id}/password")
    @Transactional
    public Response updatePassword(
            @PathParam("id")
            Long id,
            UpdatePasswordRequest request
    ) {

        User user =
                User.findById(id);

        if (user == null) {

            return Response
                    .status(404)
                    .build();
        }

        user.passwordHash =
                request.password;

        return Response.ok()
                .build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(
            @PathParam("id")
            Long id
    ) {

        User.deleteById(id);

        return Response.ok()
                .build();
    }
}