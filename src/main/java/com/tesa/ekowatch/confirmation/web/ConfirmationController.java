package com.tesa.ekowatch.confirmation.web;

import com.tesa.ekowatch.confirmation.api.ConfirmationApi;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ConfirmationController {

    private final ConfirmationApi confirmations;
    private final ActorAccess actors;

    public ConfirmationController(
            ConfirmationApi confirmations,
            ActorAccess actors) {
        this.confirmations = confirmations;
        this.actors = actors;
    }

    @PostMapping("/reports/{reportId}/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public void confirm(
            Authentication authentication,
            @PathVariable UUID reportId) {

        UUID actorId = actors.id(authentication);

        confirmations.confirm(
                actorId,
                reportId
        );
    }
}