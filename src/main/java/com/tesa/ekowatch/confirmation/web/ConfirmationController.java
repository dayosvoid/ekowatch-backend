package com.tesa.ekowatch.confirmation.web;

import com.tesa.ekowatch.confirmation.api.ConfirmationApi;
import com.tesa.ekowatch.report.api.ReportView;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
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
    public ReportView confirmReport(
            Authentication authentication,
            @PathVariable UUID reportId) {

        UUID actorId = actors.id(authentication);
        return confirmations.confirmReport(actorId, reportId);
    }
}
