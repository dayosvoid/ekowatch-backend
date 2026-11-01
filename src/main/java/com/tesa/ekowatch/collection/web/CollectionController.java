package com.tesa.ekowatch.collection.web;

import com.tesa.ekowatch.collection.api.CollectionApi;
import com.tesa.ekowatch.collection.api.CollectionScheduleView;
import com.tesa.ekowatch.collection.api.CollectionView;
import com.tesa.ekowatch.shared.Page;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.ward.api.StreetView;
import com.tesa.ekowatch.ward.api.WardApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1")
public class CollectionController {

    private final CollectionApi collections;
    private final ActorAccess actors;
    private final WardApi wards;
    private final Clock clock;
    private final Map<String, UUID> recordedCollections = new ConcurrentHashMap<>();

    public CollectionController(CollectionApi collections, ActorAccess actors) {
        this(collections, actors, null, Clock.systemUTC());
    }

    @Autowired
    public CollectionController(
            CollectionApi collections,
            ActorAccess actors,
            WardApi wards,
            Clock clock) {
        this.collections = collections;
        this.actors = actors;
        this.wards = wards;
        this.clock = clock;
    }

    @GetMapping("/collection-schedules")
    public Page<CollectionScheduleView> listCollectionSchedules(
            Authentication authentication,
            @RequestParam UUID wardId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID streetId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        UUID actorId = actors.id(authentication);
        if (page == null || page < 0 || size == null || size < 1 || size > 100) {
            throw new RuleViolation("VALIDATION_FAILED");
        }

        List<CollectionScheduleView> items = collections.listCollectionSchedules(
                actorId,
                wardId,
                fromDate,
                toDate
        );

        if (streetId != null) {
            if (wards != null) {
                List<StreetView> wardStreets = wards.listStreets(actorId, wardId);
                boolean inWard = wardStreets.stream().anyMatch(s -> streetId.equals(s.streetId()));
                if (!inWard) {
                    throw new RuleViolation("VALIDATION_FAILED");
                }
            }
            items = items.stream()
                    .filter(s -> streetId.equals(s.streetId()))
                    .toList();
        }

        long totalItems = items.size();
        int totalPages = totalItems == 0 ? 0 : (int) ((totalItems + size - 1) / size);
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        List<CollectionScheduleView> pageItems = items.subList(fromIndex, toIndex);

        return new Page<>(pageItems, page, size, totalItems, totalPages);
    }

    @PutMapping("/streets/{streetId}/collections/{collectionDate}")
    public ResponseEntity<CollectionView> recordCollection(
            Authentication authentication,
            @PathVariable UUID streetId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate collectionDate) {

        UUID actorId = actors.id(authentication);
        Instant callStart = clock == null ? null : clock.instant().truncatedTo(ChronoUnit.MICROS);
        CollectionView view = collections.recordCollection(actorId, streetId, collectionDate);

        String key = streetId + "|" + collectionDate;
        boolean alreadyRecorded = false;
        if (view != null && view.collectionId() != null) {
            UUID previous = recordedCollections.putIfAbsent(key, view.collectionId());
            if (previous != null) {
                alreadyRecorded = true;
            } else if (callStart != null && view.completedAt() != null && view.completedAt().isBefore(callStart)) {
                alreadyRecorded = true;
            }
        }

        HttpStatus status = alreadyRecorded ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(view);
    }
}
