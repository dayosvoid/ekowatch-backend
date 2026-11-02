package com.tesa.ekowatch.confirmation.web;

import com.tesa.ekowatch.confirmation.api.ConfirmationApi;
import com.tesa.ekowatch.report.api.ReportView;
import com.tesa.ekowatch.user.api.ActorAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Confirmations", description = "Community confirmation ('same here') for resident reports")
@SecurityRequirement(name = "bearerAuth")
public class ConfirmationController {

    private final ConfirmationApi confirmations;
    private final ActorAccess actors;

    public ConfirmationController(
            ConfirmationApi confirmations,
            ActorAccess actors) {
        this.confirmations = confirmations;
        this.actors = actors;
    }

    @PutMapping("/{reportId}/confirmations/me")
    @Operation(
            operationId = "confirmReport",
            summary = "Confirm an existing report idempotently ('same here')",
            description = "Requires Role: `RESIDENT`. Returns `200 OK` with the updated `ReportView`. Returns `409 SELF_CONFIRMATION_NOT_ALLOWED` if the resident is the original reporter."
    )
    public ReportView confirmReport(
            Authentication authentication,
            @PathVariable UUID reportId) {

        UUID actorId = actors.id(authentication);
        return confirmations.confirmReport(actorId, reportId);
    }
}
