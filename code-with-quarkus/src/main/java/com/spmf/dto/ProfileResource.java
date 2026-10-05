package com.spmf.dto;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/profiles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileResource {
    @Inject
    JsonWebToken jwt;

    @GET
    public List<Profile> list() {

        Long userId =
                ((jakarta.json.JsonNumber)
                        jwt.getClaim("userId"))
                        .longValue();

        return Profile.list(
                "userId",
                userId
        );
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Profile create(
            CreateProfileRequest req
    ) {
        System.out.println("CreateProfilerequest log");
        System.out.println(req);
        Long userId =
                ((jakarta.json.JsonNumber)
                        jwt.getClaim("userId"))
                        .longValue();

        Profile profile =
                new Profile();

        profile.userId =
                userId;

        profile.profileName =
                req.profileName;

        profile.isKidsProfile =
                req.isKidsProfile;

        profile.persist();

        return profile;
    }
}