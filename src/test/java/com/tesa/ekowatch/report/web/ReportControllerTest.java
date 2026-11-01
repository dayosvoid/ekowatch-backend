package com.tesa.ekowatch.report.web;

import com.tesa.ekowatch.report.api.CreateReportRequest;
import com.tesa.ekowatch.report.api.ReportApi;
import com.tesa.ekowatch.report.api.ReportSort;
import com.tesa.ekowatch.report.api.ReportType;
import com.tesa.ekowatch.report.api.ReportView;
import com.tesa.ekowatch.report.api.WardHazardsView;
import com.tesa.ekowatch.shared.ApiExceptionHandler;
import com.tesa.ekowatch.shared.Page;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {
    private final ReportApi reports = mock(ReportApi.class);
    private final ActorAccess actors = mock(ActorAccess.class);
    private final Authentication authentication = mock(Authentication.class);
    private final ReportController controller = new ReportController(reports, actors);

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WARD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STREET_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REPORT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final String NOTE = "Blocked drain on Fictional Palm Street";

    private CreateReportRequest request(String note) {
        return new CreateReportRequest(ReportType.BLOCKED_DRAIN, STREET_ID, note, null, null, null);
    }

    private ReportView view() {
        Instant at = Instant.parse("2026-10-31T12:00:00Z");
        return new ReportView(REPORT_ID, ReportType.BLOCKED_DRAIN, WARD_ID, STREET_ID, "Fictional Palm Street",
                NOTE, null, at, null, 0, false, at, null, null, null, "STREET");
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void residentCanCreateReportWithIdempotencyKey() {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        var request = request(NOTE);
        var expected = view();
        when(reports.createReport(ACTOR_ID, IDEMPOTENCY_KEY, request)).thenReturn(expected);

        var response = controller.createReport(authentication, IDEMPOTENCY_KEY.toString(), request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("/api/v1/reports/" + REPORT_ID, response.getHeaders().getLocation().toString());
        assertSame(expected, response.getBody());
        verify(actors).id(authentication);
        verify(reports).createReport(ACTOR_ID, IDEMPOTENCY_KEY, request);
    }

    @Test
    void createReportRejectsMissingIdempotencyKeyWithoutInventingOne() {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        var request = request(NOTE);
        assertEquals(
                "IDEMPOTENCY_KEY_REQUIRED",
                assertThrows(RuleViolation.class, () -> controller.createReport(authentication, null, request)).code()
        );
        verifyNoInteractions(reports);
    }

    @Test
    void createReportRejectsMalformedIdempotencyKey() {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        var request = request(NOTE);
        assertEquals(
                "VALIDATION_FAILED",
                assertThrows(RuleViolation.class, () -> controller.createReport(authentication, "not-a-uuid", request)).code()
        );
        verifyNoInteractions(reports);
    }

    @Test
    void getReportUsesPrincipalAndCanonicalService() {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        when(reports.getReport(ACTOR_ID, REPORT_ID)).thenReturn(view());
        assertEquals(view(), controller.getReport(authentication, REPORT_ID));
        verify(actors).id(authentication);
        verify(reports).getReport(ACTOR_ID, REPORT_ID);
    }

    @Test
    void listReportsForwardsWardFiltersAndCanonicalPagination() {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        var expected = new Page<>(List.of(view()), 0, 20, 1, 1);
        when(reports.listReports(ACTOR_ID, WARD_ID, STREET_ID, ReportType.BLOCKED_DRAIN, null, 0, 20, ReportSort.CONFIRMATIONS))
                .thenReturn(expected);
        assertSame(
                expected,
                controller.listReports(authentication, WARD_ID, STREET_ID, ReportType.BLOCKED_DRAIN, null, 0, 20, ReportSort.CONFIRMATIONS)
        );
        verify(actors).id(authentication);
        verify(reports).listReports(ACTOR_ID, WARD_ID, STREET_ID, ReportType.BLOCKED_DRAIN, null, 0, 20, ReportSort.CONFIRMATIONS);
    }

    @Test
    void listWardHazardsDelegatesToReportApi() {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        Instant now = Instant.parse("2026-10-31T12:00:00Z");
        var hazards = new WardHazardsView(WARD_ID, now, now, List.of(view()));
        when(reports.listWardHazards(ACTOR_ID, WARD_ID)).thenReturn(hazards);
        assertSame(hazards, controller.listWardHazards(authentication, WARD_ID));
        verify(reports).listWardHazards(ACTOR_ID, WARD_ID);
    }

    @Test
    void httpUsesNoteAndDoesNotAcceptActorFromJson() throws Exception {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        when(reports.createReport(ACTOR_ID, IDEMPOTENCY_KEY, request(NOTE))).thenReturn(view());
        mvc().perform(post("/api/v1/reports")
                        .principal(authentication)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"BLOCKED_DRAIN","streetId":"%s","note":"%s","actorId":"%s"}
                                """.formatted(STREET_ID, NOTE, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/reports/" + REPORT_ID))
                .andExpect(jsonPath("$.note").value(NOTE))
                .andExpect(jsonPath("$.reportId").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.reporterId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
        verify(reports).createReport(ACTOR_ID, IDEMPOTENCY_KEY, request(NOTE));
    }

    @Test
    void httpMapsProblemJsonForValidationIdempotencyAndNotFoundCodes() throws Exception {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);

        mvc().perform(post("/api/v1/reports")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"BLOCKED_DRAIN","streetId":"%s","note":"%s"}
                                """.formatted(STREET_ID, NOTE)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.status").value(400));

        when(reports.createReport(ACTOR_ID, IDEMPOTENCY_KEY, request("")))
                .thenThrow(new RuleViolation("VALIDATION_FAILED"));
        mvc().perform(post("/api/v1/reports")
                        .principal(authentication)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"BLOCKED_DRAIN","streetId":"%s","note":""}
                                """.formatted(STREET_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        when(reports.createReport(ACTOR_ID, IDEMPOTENCY_KEY, request(NOTE)))
                .thenThrow(new RuleViolation("IDEMPOTENCY_KEY_REUSED"));
        mvc().perform(post("/api/v1/reports")
                        .principal(authentication)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"BLOCKED_DRAIN","streetId":"%s","note":"%s"}
                                """.formatted(STREET_ID, NOTE)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        when(reports.getReport(ACTOR_ID, REPORT_ID)).thenThrow(new RuleViolation("RESOURCE_NOT_FOUND"));
        mvc().perform(get("/api/v1/reports/{reportId}", REPORT_ID).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void httpListUsesWardAndPageDefaults() throws Exception {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        when(reports.listReports(ACTOR_ID, WARD_ID, null, null, null, 0, 20, ReportSort.CONFIRMATIONS))
                .thenReturn(new Page<>(List.of(view()), 0, 20, 1, 1));
        mvc().perform(get("/api/v1/reports").principal(authentication).param("wardId", WARD_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].note").value(NOTE))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        verify(reports).listReports(ACTOR_ID, WARD_ID, null, null, null, 0, 20, ReportSort.CONFIRMATIONS);
    }
}
