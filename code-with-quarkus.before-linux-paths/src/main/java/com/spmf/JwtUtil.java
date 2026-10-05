package com.spmf;

import io.smallrye.jwt.build.Jwt;

import java.time.Duration;

public class JwtUtil {

    public static String generateToken(
            Long userId,
            String username,
            String role
    ) {

        return Jwt.issuer("myflix")
                .subject(username)
                .claim("userId", userId)
                .groups(role)
                //.expiresIn(Duration.ofDays(30))
                .expiresIn(Duration.ofHours(1))
                .sign();
    }
}