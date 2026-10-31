package com.tesa.ekowatch.collection.web;

import com.tesa.ekowatch.collection.api.CollectionApi;
import com.tesa.ekowatch.user.api.ActorAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CollectionController {

    private final CollectionApi collections;
    private final ActorAccess actors;

    public CollectionController(
            CollectionApi collections,
            ActorAccess actors) {

        this.collections = collections;
        this.actors = actors;
    }

    @PostMapping("/pickups")
    @ResponseStatus(HttpStatus.CREATED)
    public void recordPickup(
            Authentication authentication,
            @Valid @RequestBody PickupRequest request) {

        UUID actorId = actors.id(authentication);

        collections.recordPickup(
                actorId,
                request.streetId(),
                request.date(),
                request.status()
        );
    }

    @GetMapping("/authority/missed-pickups")
    public List<CollectionApi.MissedPickup> missedPickups(
            Authentication authentication,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate through) {

        UUID actorId = actors.id(authentication);

        return collections.missed(
                actorId,
                from,
                through
        );
    }

    public record PickupRequest(

            @NotNull
            UUID streetId,

            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,

            @NotNull
            @Pattern(regexp = "COMPLETED|FAILED")
            String status
    ) {
    }
}