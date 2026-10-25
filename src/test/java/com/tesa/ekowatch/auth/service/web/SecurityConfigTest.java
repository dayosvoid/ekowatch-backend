package com.tesa.ekowatch.auth.service.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void residentRoleIsRecognized() {
        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_RESIDENT");

        assertTrue(
                authority.getAuthority().equals("ROLE_RESIDENT")
        );
    }

    @Test
    void authorityRoleIsRecognized() {
        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_AUTHORITY");

        assertTrue(
                authority.getAuthority().equals("ROLE_AUTHORITY")
        );
    }

    @Test
    void operatorRoleIsRecognized() {
        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_OPERATOR");

        assertTrue(
                authority.getAuthority().equals("ROLE_OPERATOR")
        );
    }
}