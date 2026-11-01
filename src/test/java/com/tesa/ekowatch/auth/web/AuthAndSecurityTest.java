package com.tesa.ekowatch.auth.web;

import com.tesa.ekowatch.auth.api.CreateSessionRequest;
import com.tesa.ekowatch.auth.api.SessionView;
import com.tesa.ekowatch.auth.service.AuthService;
import com.tesa.ekowatch.auth.service.BearerTokenService;
import com.tesa.ekowatch.shared.ApiExceptionHandler;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.user.api.CurrentUserView;
import com.tesa.ekowatch.user.api.Role;
import com.tesa.ekowatch.user.api.UserApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAndSecurityTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WARD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STREET_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void bearerTokenServiceIssuesAndVerifiesSignedTokenAndRejectsTamperedOrExpiredTokens() {
        Instant now = Instant.parse("2026-10-31T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        BearerTokenService service = new BearerTokenService(
                "test-secret-key-for-ekowatch-token-signing-32b",
                8,
                clock
        );

        BearerTokenService.IssuedToken issued = service.issueToken(
                USER_ID,
                "resident.one@demo.ekowatch.invalid",
                "RESIDENT"
        );
        assertEquals(now.plusSeconds(8 * 3600), issued.expiresAt());

        var verified = service.verify(issued.accessToken());
        assertTrue(verified.isPresent());
        assertEquals(USER_ID, verified.get().userId());
        assertEquals("resident.one@demo.ekowatch.invalid", verified.get().email());
        assertEquals("RESIDENT", verified.get().role());

        assertFalse(service.verify(issued.accessToken() + "tampered").isPresent());

        Clock expiredClock = Clock.fixed(now.plusSeconds(8 * 3600 + 1), ZoneOffset.UTC);
        BearerTokenService expiredVerifier = new BearerTokenService(
                "test-secret-key-for-ekowatch-token-signing-32b",
                8,
                expiredClock
        );
        assertFalse(expiredVerifier.verify(issued.accessToken()).isPresent());
    }

    @Test
    void authServiceCreatesSessionOnValidPasswordAndRejectsInvalidOrDisabledCredentials() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        Instant now = Instant.parse("2026-10-31T10:00:00Z");
        BearerTokenService tokenService = new BearerTokenService(
                "test-secret-key-for-ekowatch-token-signing-32b",
                8,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        UserApi userApi = mock(UserApi.class);
        CurrentUserView userView = new CurrentUserView(
                USER_ID,
                "resident.one@demo.ekowatch.invalid",
                "Fictional Resident One",
                Role.RESIDENT,
                WARD_ID,
                STREET_ID
        );
        when(userApi.getCurrentUser(USER_ID)).thenReturn(userView);

        String encodedHash = encoder.encode("CorrectHorseBatteryStaple!");
        when(db.query(anyString(), any(RowMapper.class), eq("resident.one@demo.ekowatch.invalid")))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(USER_ID);
                    when(rs.getString("email")).thenReturn("resident.one@demo.ekowatch.invalid");
                    when(rs.getString("password_hash")).thenReturn(encodedHash);
                    when(rs.getString("role")).thenReturn("RESIDENT");
                    return List.of(mapper.mapRow(rs, 0));
                });

        AuthService authService = new AuthService(db, encoder, tokenService, userApi);
        SessionView session = authService.createSession(new CreateSessionRequest(
                "Resident.One@demo.ekowatch.invalid",
                "CorrectHorseBatteryStaple!"
        ));
        assertEquals("Bearer", session.tokenType());
        assertEquals(userView, session.user());
        assertTrue(tokenService.verify(session.accessToken()).isPresent());

        RuleViolation wrongPassword = assertThrows(
                RuleViolation.class,
                () -> authService.createSession(new CreateSessionRequest(
                        "resident.one@demo.ekowatch.invalid",
                        "wrong-password"
                ))
        );
        assertEquals("UNAUTHENTICATED", wrongPassword.code());
    }

    @Test
    void authControllerReturnsSessionViewAndMapsUnauthenticatedTo401ProblemJson() throws Exception {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        CurrentUserView userView = new CurrentUserView(
                USER_ID,
                "resident.one@demo.ekowatch.invalid",
                "Fictional Resident One",
                Role.RESIDENT,
                WARD_ID,
                STREET_ID
        );
        SessionView sessionView = new SessionView(
                "signed.token",
                "Bearer",
                Instant.parse("2026-10-31T18:00:00Z"),
                userView
        );

        when(authService.createSession(new CreateSessionRequest("resident.one@demo.ekowatch.invalid", "secret")))
                .thenReturn(sessionView);
        when(authService.createSession(new CreateSessionRequest("resident.one@demo.ekowatch.invalid", "bad")))
                .thenThrow(new RuleViolation("UNAUTHENTICATED"));

        mvc.perform(post("/api/v1/auth/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"resident.one@demo.ekowatch.invalid","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("RESIDENT"));

        mvc.perform(post("/api/v1/auth/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"resident.one@demo.ekowatch.invalid","password":"bad"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }
}
