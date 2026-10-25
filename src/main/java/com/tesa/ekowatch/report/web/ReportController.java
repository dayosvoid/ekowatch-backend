package com.tesa.ekowatch.report.web;

import com.tesa.ekowatch.report.api.ReportApi;
import com.tesa.ekowatch.report.api.ReportView;
import com.tesa.ekowatch.user.api.ActorAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportApi reports;
    private final ActorAccess actors;

    public ReportController(
            ReportApi reports,
            ActorAccess actors) {
        this.reports = reports;
        this.actors = actors;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportView create(
            Authentication authentication,
            @Valid @RequestBody CreateReportRequest request) {

        UUID actorId = actors.id(authentication);

        return reports.create(
                actorId,
                request.wardId(),
                request.streetId(),
                request.description()
        );
    }

    @GetMapping("/{reportId}")
    public ReportView get(
            Authentication authentication,
            @PathVariable UUID reportId) {

        UUID actorId = actors.id(authentication);

        return reports.get(
                actorId,
                reportId
        );
    }

    @GetMapping
    public List<ReportView> list(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        UUID actorId = actors.id(authentication);

        return reports.list(
                actorId,
                limit,
                offset
        );
    }

    public record CreateReportRequest(

            @NotNull
            UUID wardId,

            @NotNull
            UUID streetId,

            @NotBlank
            String description
    ) {
    }
}