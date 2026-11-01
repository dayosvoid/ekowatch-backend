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
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportApi reports;
    private final ActorAccess actors;

    public ReportController(ReportApi reports, ActorAccess actors) {
        this.reports = reports;
        this.actors = actors;
    }

    @PostMapping("/reports")
    public ResponseEntity<ReportView> createReport(
            Authentication authentication,
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
    public ReportView getReport(
            Authentication authentication,
            @PathVariable UUID reportId) {
        return reports.getReport(actors.id(authentication), reportId);
    }

    @GetMapping("/reports")
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
    public WardHazardsView listWardHazards(
            Authentication authentication,
            @PathVariable UUID wardId) {
        return reports.listWardHazards(actors.id(authentication), wardId);
    }
}
