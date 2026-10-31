package com.tesa.ekowatch.collection.web;

import com.tesa.ekowatch.collection.api.*;
import com.tesa.ekowatch.user.api.ActorAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class CollectionController {
    private final CollectionApi collections;
    private final ActorAccess actors;
    public CollectionController(CollectionApi collections,ActorAccess actors) { this.collections=collections; this.actors=actors; }

    // Retain Esther's existing transport paths; there is no legacy service alias or FAILED write.
    @PostMapping("/pickups")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionView recordCollection(Authentication authentication,@Valid @RequestBody CollectionRequest request) {
        return collections.recordCollection(actors.id(authentication),request.streetId(),request.collectionDate());
    }
    @GetMapping("/authority/missed-pickups")
    public List<CollectionScheduleView> missedCollections(Authentication authentication,@RequestParam UUID wardId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through) {
        UUID actorId=actors.id(authentication);
        actors.requireRole(actorId,"AUTHORITY");
        return collections.listCollectionSchedules(actorId,wardId,from,through).stream()
                .filter(s->s.collectionStatus()==CollectionStatus.MISSED).toList();
    }
    public record CollectionRequest(@NotNull UUID streetId,@NotNull LocalDate collectionDate) {}
}
