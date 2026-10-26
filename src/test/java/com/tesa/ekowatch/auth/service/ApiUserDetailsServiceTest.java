package com.tesa.ekowatch.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiUserDetailsServiceTest {

    @Test
    void loadsUserAndAssignsRole() {
        JdbcTemplate db = mock(JdbcTemplate.class);

        when(db.queryForMap(
                """
                SELECT email, password_hash, role
                FROM users
                WHERE email = ?
                """,
                "resident@ekowatch.com"
        )).thenReturn(Map.of(
                "email", "resident@ekowatch.com",
                "password_hash", "$2a$10$example-hash",
                "role", "RESIDENT"
        ));

        ApiUserDetailsService service =
                new ApiUserDetailsService(db);

        UserDetails user =
                service.loadUserByUsername("resident@ekowatch.com");

        assertEquals(
                "resident@ekowatch.com",
                user.getUsername()
        );

        assertEquals(
                "$2a$10$example-hash",
                user.getPassword()
        );

        assertTrue(
                user.getAuthorities()
                        .stream()
                        .anyMatch(a ->
                                a.getAuthority().equals("ROLE_RESIDENT"))
        );
    }

    @Test
    void throwsExceptionWhenUserDoesNotExist() {
        JdbcTemplate db = mock(JdbcTemplate.class);

        when(db.queryForMap(
                """
                SELECT email, password_hash, role
                FROM users
                WHERE email = ?
                """,
                "unknown@ekowatch.com"
        )).thenThrow(new RuntimeException("No user"));

        ApiUserDetailsService service =
                new ApiUserDetailsService(db);

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername(
                        "unknown@ekowatch.com"
                )
        );
    }
}