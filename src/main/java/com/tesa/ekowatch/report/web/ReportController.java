package com.tesa.ekowatch.report.web;

import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.shared.Page;
import com.tesa.ekowatch.user.api.ActorAccess;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportApi reports;
    private final ActorAccess actors;

    public ReportController(ReportApi reports, ActorAccess actors) {
        this.reports = reports;
        this.actors = actors;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportView create(Authentication authentication, @Valid @RequestBody CreateReportRequest request) {
        return reports.createReport(actors.id(authentication), request);
    }

    @GetMapping("/{reportId}")
    public ReportView get(Authentication authentication, @PathVariable UUID reportId) {
        return reports.getReport(actors.id(authentication), reportId);
    }

    @GetMapping
    public Page<ReportView> list(
            Authentication authentication,
            @RequestParam UUID wardId,
            @RequestParam(required = false) UUID streetId,
            @RequestParam(required = false) ReportType reportType,
            @RequestParam(required = false) FloodStatus floodStatus,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "CONFIRMATIONS") ReportSort sort) {
        return reports.listReports(actors.id(authentication), wardId, streetId, reportType, floodStatus, page, size, sort);
    }
}
