package com.tesa.ekowatch.report.web;

import com.tesa.ekowatch.report.api.CreateReportRequest;
import com.tesa.ekowatch.report.api.FloodStatus;
import com.tesa.ekowatch.report.api.ReportApi;
import com.tesa.ekowatch.report.api.ReportSort;
import com.tesa.ekowatch.report.api.ReportType;
import com.tesa.ekowatch.report.api.ReportView;
import com.tesa.ekowatch.report.api.WardHazardsView;
import com.tesa.ekowatch.shared.Page;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.user.api.ActorAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reports & Hazards", description = "Resident waste and flood reporting, feed listing, and ward hazard layers")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportApi reports;
    private final ActorAccess actors;

    public ReportController(ReportApi reports, ActorAccess actors) {
        this.reports = reports;
        this.actors = actors;
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createReport",
            summary = "Create a resident waste or flood report idempotently (returns 201 Created)",
            description = """
                    Requires Role: `RESIDENT` and a required `Idempotency-Key` UUID request header.
                    - **Always returns `201 Created`** (with `Location: /api/v1/reports/{reportId}`).
                    - **Idempotency**: If the client sends the **same `Idempotency-Key` and same request body** within 24 hours, the server returns the **exact same `ReportView` (`201 Created`)** without creating a duplicate report.
                    - If `Idempotency-Key` is missing, returns `400 Bad Request` (`code: "IDEMPOTENCY_KEY_REQUIRED"`).
                    - If the same `Idempotency-Key` is reused with a different request body, returns `409 Conflict` (`code: "IDEMPOTENCY_KEY_REUSED"`).
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Report created (or original ReportView replayed on idempotent retry)",
                            headers = @Header(name = "Location", description = "URI of the created report (/api/v1/reports/{reportId})", schema = @Schema(type = "string", format = "uri")),
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReportView.class))
                    )
            }
    )
    public ResponseEntity<ReportView> createReport(
            Authentication authentication,
            @Parameter(
                    name = "Idempotency-Key",
                    required = true,
                    description = "Required client-generated UUID. Sending the same key and same payload twice returns the same ReportView instead of creating a duplicate.",
                    example = "90000000-0000-0000-0000-000000000001",
                    schema = @Schema(type = "string", format = "uuid")
            )
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CreateReportRequest request) {

        UUID actorId = actors.id(authentication);
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            throw new RuleViolation("IDEMPOTENCY_KEY_REQUIRED");
        }
        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(idempotencyKeyHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new RuleViolation("VALIDATION_FAILED");
        }

        ReportView created = reports.createReport(actorId, idempotencyKey, request);
        URI location = created != null && created.reportId() != null
                ? URI.create("/api/v1/reports/" + created.reportId())
                : URI.create("/api/v1/reports");
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/reports/{reportId}")
    @Operation(
            operationId = "getReport",
            summary = "Get a single report by ID",
            description = "Requires `Authorization: Bearer <token>`. Returns the privacy-safe `ReportView`."
    )
    public ReportView getReport(
            Authentication authentication,
            @PathVariable UUID reportId) {
        return reports.getReport(actors.id(authentication), reportId);
    }

    @GetMapping("/reports")
    @Operation(
            operationId = "listReports",
            summary = "List reports in a ward with filtering, sorting, and pagination",
            description = "Requires `Authorization: Bearer <token>`. Filters by required `wardId` and optional `streetId`, `reportType`, `floodStatus`. Sorted by `CONFIRMATIONS` (default) or `RECENT`."
    )
    public Page<ReportView> listReports(
            Authentication authentication,
            @RequestParam UUID wardId,
            @RequestParam(required = false) UUID streetId,
            @RequestParam(required = false) ReportType reportType,
            @RequestParam(required = false) FloodStatus floodStatus,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "CONFIRMATIONS") ReportSort sort) {
        return reports.listReports(
                actors.id(authentication),
                wardId,
                streetId,
                reportType,
                floodStatus,
                page,
                size,
                sort
        );
    }

    @GetMapping("/wards/{wardId}/hazards")
    @Operation(
            operationId = "listWardHazards",
            summary = "List active ward hazards (blocked drains and current floods)",
            description = "Requires `Authorization: Bearer <token>`. Returns `WardHazardsView` containing active `BLOCKED_DRAIN` and `CURRENT` `FLOODED_ROAD` reports in the ward."
    )
    public WardHazardsView listWardHazards(
            Authentication authentication,
            @PathVariable UUID wardId) {
        return reports.listWardHazards(actors.id(authentication), wardId);
    }
}
