package com.tesa.ekowatch.report.web;

import com.tesa.ekowatch.report.api.ReportApi;
import com.tesa.ekowatch.report.api.ReportView;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

class ReportControllerTest {

    private final ReportApi reports = mock(ReportApi.class);
    private final ActorAccess actors = mock(ActorAccess.class);
    private final Authentication authentication = mock(Authentication.class);

    private final ReportController controller =
            new ReportController(reports, actors);

    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID WARD_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID STREET_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID REPORT_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Test
    void residentCanCreateReport() {

        when(actors.id(authentication)).thenReturn(ACTOR_ID);

        ReportView expected = new ReportView(
                REPORT_ID,
                ACTOR_ID,
                WARD_ID,
                STREET_ID,
                "BLOCKED_DRAIN",
                "Blocked drain on Fictional Palm Street",
                "OPEN",
                Instant.now(),
                Instant.now(),
                0L
        );

        when(reports.create(
                ACTOR_ID,
                WARD_ID,
                STREET_ID,
                "Blocked drain on Fictional Palm Street"
        )).thenReturn(expected);

        ReportController.CreateReportRequest request =
                new ReportController.CreateReportRequest(
                        WARD_ID,
                        STREET_ID,
                        "Blocked drain on Fictional Palm Street"
                );

        ReportView result =
                controller.create(authentication, request);

        verify(actors).id(authentication);

        verify(reports).create(
                ACTOR_ID,
                WARD_ID,
                STREET_ID,
                "Blocked drain on Fictional Palm Street"
        );

        assert result == expected;
    }

    @Test
    void rejectsReportWithBlankDescription() {

        when(actors.id(authentication)).thenReturn(ACTOR_ID);

        ReportController.CreateReportRequest request =
                new ReportController.CreateReportRequest(
                        WARD_ID,
                        STREET_ID,
                        ""
                );

        controller.create(authentication, request);

        verify(actors).id(authentication);

        verify(reports).create(
                ACTOR_ID,
                WARD_ID,
                STREET_ID,
                ""
        );
    }
}