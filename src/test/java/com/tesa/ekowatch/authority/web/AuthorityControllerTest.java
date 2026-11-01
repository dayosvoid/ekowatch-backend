package com.tesa.ekowatch.authority.web;

import com.tesa.ekowatch.authority.api.AuthorityApi;
import com.tesa.ekowatch.authority.api.OperatorPerformanceView;
import com.tesa.ekowatch.authority.api.WardDashboardView;
import com.tesa.ekowatch.shared.ApiExceptionHandler;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorityControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WARD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void authorityDashboardReturns200ForAuthorityAnd403ForbiddenForNonAuthority() throws Exception {
        AuthorityApi authorities = mock(AuthorityApi.class);
        ActorAccess actors = mock(ActorAccess.class);
        Authentication auth = mock(Authentication.class);
        when(actors.id(auth)).thenReturn(ACTOR_ID);

        AuthorityController controller = new AuthorityController(authorities, actors);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 30);
        Instant generatedAt = Instant.parse("2026-10-31T12:00:00Z");
        WardDashboardView dashboard = new WardDashboardView(
                WARD_ID,
                from,
                to,
                generatedAt,
                2,
                1,
                3,
                2,
                generatedAt,
                List.of(new OperatorPerformanceView(UUID.randomUUID(), "Fictional Operator One", 10, 7, 2, 1))
        );
        when(authorities.getWardDashboard(ACTOR_ID, WARD_ID, from, to)).thenReturn(dashboard);

        mvc.perform(get("/api/v1/wards/{wardId}/dashboard", WARD_ID)
                        .principal(auth)
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wardId").value(WARD_ID.toString()))
                .andExpect(jsonPath("$.missedPickupCount").value(2))
                .andExpect(jsonPath("$.disputedPickupCount").value(1));

        when(authorities.getWardDashboard(ACTOR_ID, WARD_ID, from, to))
                .thenThrow(new RuleViolation("FORBIDDEN"));
        mvc.perform(get("/api/v1/wards/{wardId}/dashboard", WARD_ID)
                        .principal(auth)
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-30"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void openApiSpecificationDefinesAllCanonicalV1EndpointsAndProblemJson() throws Exception {
        String contractsYaml = Files.readString(Path.of("contracts/openapi.yaml"));
        String rootYaml = Files.readString(Path.of("openapi.yaml"));
        assertEquals(contractsYaml, rootYaml);

        List<String> expectedPaths = List.of(
                "/api/v1/auth/sessions:",
                "/api/v1/users/me:",
                "/api/v1/users/me/preferences:",
                "/api/v1/wards:",
                "/api/v1/wards/{wardId}/streets:",
                "/api/v1/collection-schedules:",
                "/api/v1/operators/me/streets:",
                "/api/v1/streets/{streetId}/collections/{collectionDate}:",
                "/api/v1/photos:",
                "/api/v1/photos/{photoId}:",
                "/api/v1/reports:",
                "/api/v1/reports/{reportId}:",
                "/api/v1/reports/{reportId}/confirmations/me:",
                "/api/v1/wards/{wardId}/hazards:",
                "/api/v1/wards/{wardId}/dashboard:"
        );
        for (String path : expectedPaths) {
            assertTrue(contractsYaml.contains(path), "Missing path in openapi.yaml: " + path);
        }
        assertTrue(contractsYaml.contains("application/problem+json"));
        assertTrue(contractsYaml.contains("Idempotency-Key"));
    }
}
