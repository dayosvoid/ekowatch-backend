package com.tesa.ekowatch.collection.web;

import com.tesa.ekowatch.collection.api.CollectionApi;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.ward.api.StreetView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Operators", description = "Waste operator assigned streets")
@SecurityRequirement(name = "bearerAuth")
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
    @Operation(
            operationId = "listMyAssignedStreets",
            summary = "List distinct streets scheduled for the authenticated operator in the date range",
            description = """
                    Requires Role: `OPERATOR` (`Authorization: Bearer <token>`).
                    - **How `fromDate` and `toDate` filter**: Returns only the distinct streets in the operator's assigned ward(s) (`operator_wards`) that have a scheduled pickup assigned to this authenticated operator in `collection_schedules` between `fromDate` and `toDate` (inclusive, max 31-day window).
                    - If `fromDate` and `toDate` are omitted, defaults to today (in `Africa/Lagos`) through `today + 29 days`.
                    """
    )
    public List<StreetView> listMyAssignedStreets(
            Authentication authentication,
            @Parameter(description = "Start date (ISO YYYY-MM-DD, inclusive). Defaults to today in Africa/Lagos if omitted.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "End date (ISO YYYY-MM-DD, inclusive, max 30 days after fromDate). Defaults to fromDate + 29 days if omitted.")
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
