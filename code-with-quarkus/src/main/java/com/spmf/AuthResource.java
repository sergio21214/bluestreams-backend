package com.spmf;

import com.spmf.auth.RefreshToken;
import com.spmf.auth.RefreshTokenRepository;
import com.spmf.auth.RefreshTokenService;
import com.spmf.dto.LoginRequest;
import com.spmf.dto.RegisterRequest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.mindrot.jbcrypt.BCrypt;
import jakarta.ws.rs.core.NewCookie;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    RefreshTokenService refreshTokenService;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @POST
    @Path("/register")
    @Transactional
    public Response register(
            RegisterRequest request
    ) {

        User existingUser =
                User.find(
                        "username",
                        request.username
                ).firstResult();

        if (existingUser != null) {

            return Response.status(
                    Response.Status.CONFLICT
            ).entity(
                    "Username already exists"
            ).build();
        }

        User existingEmail =
                User.find(
                        "email",
                        request.email
                ).firstResult();

        if (existingEmail != null) {

            return Response.status(
                    Response.Status.CONFLICT
            ).entity(
                    "Email already exists"
            ).build();
        }

        User user = new User();

        user.username =
                request.username;

        user.email =
                request.email;

        user.passwordHash =
                BCrypt.hashpw(
                        request.password,
                        BCrypt.gensalt()
                );

        user.role = "USER";

        user.enabled = true;

        user.persist();

        return Response.ok(
                "User created"
        ).build();
    }

    @POST
    @Path("/login")
    public Response login(
            LoginRequest request
    ) {

        User user =
                User.find(
                        "username",
                        request.username
                ).firstResult();

        if (user == null) {

            return Response.status(
                    Response.Status.UNAUTHORIZED
            ).build();
        }

        boolean valid =
                BCrypt.checkpw(
                        request.password,
                        user.passwordHash
                );

        if (!valid) {

            return Response.status(
                    Response.Status.UNAUTHORIZED
            ).build();
        }

        RefreshToken refreshToken =
                refreshTokenService.create(user.id);

        NewCookie refreshCookie =
                new NewCookie.Builder("refreshToken")
                        .value(refreshToken.token)
                        .path("/")
                        .httpOnly(true)
                        .secure(true) // localhost change to false
                        .sameSite(NewCookie.SameSite.NONE)
                        .maxAge(60 * 60 * 24 * 30)
                        .build();

        String token =
                JwtUtil.generateToken(
                        user.id,
                        user.username,
                        user.role
                );

        return Response.ok(
                        Map.of(
                                "token", token
                        )
                )
                .cookie(refreshCookie)
                .build();
    }

    @GET
    @Path("/check")
    @Authenticated
    public Response check() {
        return Response.ok().build();
    }

    @POST
    @Path("/refresh")
    public Response refresh(
            @CookieParam("refreshToken")
            Cookie refreshCookie
    ) {

        if (refreshCookie == null) {
            return Response.status(401).build();
        }

        Optional<RefreshToken> optional =
                refreshTokenRepository.findByToken(
                        refreshCookie.getValue()
                );

        if (optional.isEmpty()) {

            return Response
                    .status(401)
                    .build();

        }

        RefreshToken refresh =
                optional.get();

        if (refresh.revoked) {

            return Response.status(401).build();

        }

        if (
                refresh.expiresAt.isBefore(
                        LocalDateTime.now()
                )
        ) {

            return Response.status(401).build();

        }

        User user =
                User.findById(
                        refresh.userId
                );

        if (user == null) {

            return Response.status(401).build();

        }

        String jwt =
                JwtUtil.generateToken(

                        user.id,

                        user.username,

                        user.role

                );
        return Response.ok(

                Map.of(
                        "token",
                        jwt
                )

        ).build();
    }

}