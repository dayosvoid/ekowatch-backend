package com.tesa.ekowatch.confirmation.web;

import com.tesa.ekowatch.confirmation.api.ConfirmationApi;
import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.shared.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConfirmationControllerTest {
    final ConfirmationApi confirmations=mock(ConfirmationApi.class);
    final ActorAccess actors=mock(ActorAccess.class);
    final Authentication authentication=mock(Authentication.class);
    final ConfirmationController controller=new ConfirmationController(confirmations,actors);
    final UUID actor=UUID.randomUUID(),report=UUID.randomUUID(),ward=UUID.randomUUID(),street=UUID.randomUUID();
    ReportView view() {
        Instant now=Instant.parse("2026-10-31T10:00:00Z");
        return new ReportView(report,ReportType.FLOODED_ROAD,ward,street,"Fictional","Evidence",null,now,null,
                1,true,now,FloodStatus.CURRENT,now.plusSeconds(21600),null,"STREET");
    }
    @Test void principalAndAuthoritativeViewUseOnlyCanonicalApi() {
        when(actors.id(authentication)).thenReturn(actor);
        when(confirmations.confirmReport(actor,report)).thenReturn(view());
        assertEquals(view(),controller.confirmReport(authentication,report));
        verify(actors).id(authentication); verify(confirmations).confirmReport(actor,report);
        assertTrue(Arrays.stream(ConfirmationApi.class.getMethods()).noneMatch(m->m.getName().equals("confirm")));
    }
    @Test void existingHttpPathReturnsTheCanonicalPrivateView() throws Exception {
        when(actors.id(authentication)).thenReturn(actor);
        when(confirmations.confirmReport(actor,report)).thenReturn(view());
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build()
            .perform(post("/api/reports/{reportId}/confirm",report).principal(authentication))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.reportId").value(report.toString()))
            .andExpect(jsonPath("$.confirmationCount").value(1)).andExpect(jsonPath("$.confirmedByMe").value(true))
            .andExpect(jsonPath("$.reporterId").doesNotExist()).andExpect(jsonPath("$.status").doesNotExist());
        verify(confirmations).confirmReport(actor,report);
    }
    @Test void canonicalSelfErrorIsMappedWithoutChangingTheRoute() throws Exception {
        when(actors.id(authentication)).thenReturn(actor);
        when(confirmations.confirmReport(actor,report)).thenThrow(new RuleViolation("SELF_CONFIRMATION_NOT_ALLOWED"));
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build()
            .perform(post("/api/reports/{reportId}/confirm",report).principal(authentication))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_CONFIRMATION_NOT_ALLOWED"));
    }
}