package com.spmf.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class RefreshTokenService {

    @Inject
    RefreshTokenRepository repository;

    @Transactional
    public RefreshToken create(Long userId) {

        // Revoke every previous refresh token for this user
        System.out.println("Incoming userId = " + userId);

        repository.update(
                "revoked = true where userId = ?1",
                userId
        );

        RefreshToken token = new RefreshToken();

        token.userId = userId;

        System.out.println("Entity userId = " + token.userId);
        token.token =
                RefreshTokenUtil.generate();

        token.createdAt =
                LocalDateTime.now();

        token.expiresAt =
                LocalDateTime.now()
                        .plusDays(30);

        repository.persist(token);

        return token;

    }

}