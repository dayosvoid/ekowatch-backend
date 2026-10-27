package com.tesa.ekowatch.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AuthenticatedActorTest {

    @Test
    void resolvesAuthenticatedUserIdFromEmail() {
        JdbcTemplate db = mock(JdbcTemplate.class);

        UUID expectedId = UUID.randomUUID();

        when(db.queryForObject(
                """
                SELECT id
                FROM users
                WHERE email = ?
                """,
                UUID.class,
                "resident@ekowatch.com"
        )).thenReturn(expectedId);

        AuthenticatedActor actor = new AuthenticatedActor(db);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "resident@ekowatch.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_RESIDENT"))
                );

        UUID actualId = actor.id(authentication);

        assertEquals(expectedId, actualId);
    }
}