package com.spmf.auth;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class RefreshTokenRepository
        implements PanacheRepository<RefreshToken> {

    public Optional<RefreshToken> findByToken(String token) {

        return find("token", token)
                .firstResultOptional();

    }

}