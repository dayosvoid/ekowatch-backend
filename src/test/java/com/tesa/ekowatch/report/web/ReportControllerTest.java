package com.tesa.ekowatch.report.web;

import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.shared.*;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportControllerTest {
    private final ReportApi reports = mock(ReportApi.class);
    private final ActorAccess actors = mock(ActorAccess.class);
    private final Authentication authentication = mock(Authentication.class);
    private final ReportController controller = new ReportController(reports, actors);
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WARD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STREET_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REPORT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
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
    void residentCanCreateReport() {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        var request = request(NOTE);
        var expected = view();
        when(reports.createReport(ACTOR_ID, request)).thenReturn(expected);
        assertSame(expected, controller.create(authentication, request));
        verify(actors).id(authentication);
        verify(reports).createReport(ACTOR_ID, request);
    }

    @Test
    void rejectsReportWithBlankNote() {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        var request = request("");
        when(reports.createReport(ACTOR_ID, request)).thenThrow(new RuleViolation("VALIDATION_FAILED"));
        assertEquals("VALIDATION_FAILED", assertThrows(RuleViolation.class, () -> controller.create(authentication, request)).code());
        verify(actors).id(authentication);
        verify(reports).createReport(ACTOR_ID, request);
    }

    @Test
    void getUsesPrincipalAndCanonicalService() {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        when(reports.getReport(ACTOR_ID, REPORT_ID)).thenReturn(view());
        assertEquals(view(), controller.get(authentication, REPORT_ID));
        verify(actors).id(authentication);
        verify(reports).getReport(ACTOR_ID, REPORT_ID);
    }

    @Test
    void listForwardsWardFiltersAndCanonicalPagination() {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        var expected = new Page<>(List.of(view()), 0, 20, 1, 1);
        when(reports.listReports(ACTOR_ID, WARD_ID, STREET_ID, ReportType.BLOCKED_DRAIN, null, 0, 20, ReportSort.CONFIRMATIONS)).thenReturn(expected);
        assertSame(expected, controller.list(authentication, WARD_ID, STREET_ID, ReportType.BLOCKED_DRAIN, null, 0, 20, ReportSort.CONFIRMATIONS));
        verify(actors).id(authentication);
        verify(reports).listReports(ACTOR_ID, WARD_ID, STREET_ID, ReportType.BLOCKED_DRAIN, null, 0, 20, ReportSort.CONFIRMATIONS);
    }

    @Test
    void httpUsesNoteAndDoesNotAcceptActorFromJson() throws Exception {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        when(reports.createReport(ACTOR_ID, request(NOTE))).thenReturn(view());
        mvc().perform(post("/api/reports").principal(authentication).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reportType":"BLOCKED_DRAIN","streetId":"%s","note":"%s","actorId":"%s"}
                        """.formatted(STREET_ID, NOTE, UUID.randomUUID())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.note").value(NOTE))
                .andExpect(jsonPath("$.reportId").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.description").doesNotExist()).andExpect(jsonPath("$.reporterId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
        verify(reports).createReport(ACTOR_ID, request(NOTE));
    }

    @Test
    void httpMapsCanonicalValidationAndNotFoundCodes() throws Exception {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        when(reports.createReport(ACTOR_ID, request(""))).thenThrow(new RuleViolation("VALIDATION_FAILED"));
        mvc().perform(post("/api/reports").principal(authentication).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reportType":"BLOCKED_DRAIN","streetId":"%s","note":""}
                        """.formatted(STREET_ID))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        when(reports.getReport(ACTOR_ID, REPORT_ID)).thenThrow(new RuleViolation("RESOURCE_NOT_FOUND"));
        mvc().perform(get("/api/reports/{reportId}", REPORT_ID).principal(authentication)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void httpListUsesWardAndPageDefaults() throws Exception {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        when(reports.listReports(ACTOR_ID, WARD_ID, null, null, null, 0, 20, ReportSort.CONFIRMATIONS))
                .thenReturn(new Page<>(List.of(view()), 0, 20, 1, 1));
        mvc().perform(get("/api/reports").principal(authentication).param("wardId", WARD_ID.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].note").value(NOTE))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        verify(reports).listReports(ACTOR_ID, WARD_ID, null, null, null, 0, 20, ReportSort.CONFIRMATIONS);
    }
}
