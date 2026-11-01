package com.tesa.ekowatch.confirmation.web;

import com.tesa.ekowatch.confirmation.api.ConfirmationApi;
import com.tesa.ekowatch.report.api.FloodStatus;
import com.tesa.ekowatch.report.api.ReportType;
import com.tesa.ekowatch.report.api.ReportView;
import com.tesa.ekowatch.shared.ApiExceptionHandler;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfirmationControllerTest {
    final ConfirmationApi confirmations = mock(ConfirmationApi.class);
    final ActorAccess actors = mock(ActorAccess.class);
    final Authentication authentication = mock(Authentication.class);
    final ConfirmationController controller = new ConfirmationController(confirmations, actors);
    final UUID actor = UUID.randomUUID();
    final UUID report = UUID.randomUUID();
    final UUID ward = UUID.randomUUID();
    final UUID street = UUID.randomUUID();

    ReportView view() {
        Instant now = Instant.parse("2026-10-31T10:00:00Z");
        return new ReportView(report, ReportType.FLOODED_ROAD, ward, street, "Fictional", "Evidence", null, now, null,
                1, true, now, FloodStatus.CURRENT, now.plusSeconds(21600), null, "STREET");
    }

    @Test
    void principalAndAuthoritativeViewUseOnlyCanonicalApi() {
        when(actors.id(authentication)).thenReturn(actor);
        when(confirmations.confirmReport(actor, report)).thenReturn(view());
        assertEquals(view(), controller.confirmReport(authentication, report));
        verify(actors).id(authentication);
        verify(confirmations).confirmReport(actor, report);
        assertTrue(Arrays.stream(ConfirmationApi.class.getMethods()).noneMatch(m -> m.getName().equals("confirm")));
    }

    @Test
    void canonicalPutPathReturns200AndPrivateReportView() throws Exception {
        when(actors.id(authentication)).thenReturn(actor);
        when(confirmations.confirmReport(actor, report)).thenReturn(view());
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(put("/api/v1/reports/{reportId}/confirmations/me", report).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(report.toString()))
                .andExpect(jsonPath("$.confirmationCount").value(1))
                .andExpect(jsonPath("$.confirmedByMe").value(true))
                .andExpect(jsonPath("$.reporterId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
        verify(confirmations).confirmReport(actor, report);
    }

    @Test
    void canonicalSelfConfirmationErrorMapsTo409ProblemJson() throws Exception {
        when(actors.id(authentication)).thenReturn(actor);
        when(confirmations.confirmReport(actor, report)).thenThrow(new RuleViolation("SELF_CONFIRMATION_NOT_ALLOWED"));
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(put("/api/v1/reports/{reportId}/confirmations/me", report).principal(authentication))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SELF_CONFIRMATION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
