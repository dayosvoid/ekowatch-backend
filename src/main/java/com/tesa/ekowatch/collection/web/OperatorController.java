package com.tesa.ekowatch.collection.web;

import com.tesa.ekowatch.collection.api.CollectionApi;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.ward.api.StreetView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/operators")
public class OperatorController {

    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private final CollectionApi collections;
    private final ActorAccess actors;
    private final Clock clock;

    public OperatorController(CollectionApi collections, ActorAccess actors) {
        this(collections, actors, Clock.systemUTC());
    }

    @Autowired
    public OperatorController(CollectionApi collections, ActorAccess actors, Clock clock) {
        this.collections = collections;
        this.actors = actors;
        this.clock = clock;
    }

    @GetMapping("/me/streets")
    public List<StreetView> listMyAssignedStreets(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDate today = LocalDate.ofInstant(clock.instant(), LAGOS);
        LocalDate effectiveFrom = fromDate != null ? fromDate : today;
        LocalDate effectiveTo = toDate != null ? toDate : effectiveFrom.plusDays(29);
        return collections.listMyAssignedStreets(
                actors.id(authentication),
                effectiveFrom,
                effectiveTo
        );
    }
}
