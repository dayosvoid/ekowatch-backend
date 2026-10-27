package com.tesa.ekowatch.auth.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedActor {

    private final JdbcTemplate db;

    public AuthenticatedActor(JdbcTemplate db) {
        this.db = db;
    }

    public UUID id(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated user");
        }

        return db.queryForObject(
                """
                SELECT id
                FROM users
                WHERE email = ?
                """,
                UUID.class,
                authentication.getName()
        );
    }
}