package com.tesa.ekowatch.report.web;

import com.tesa.ekowatch.report.api.ReportApi;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/authority/reports")
public class AuthorityReportController {

    private final ReportApi reports;
    private final ActorAccess actors;

    public AuthorityReportController(
            ReportApi reports,
            ActorAccess actors) {
        this.reports = reports;
        this.actors = actors;
    }

    // Keep your existing reports() endpoint.
    // Replace:
    //
    // authenticatedActor.id(authentication)
    //
    // with:
    //
    // actors.id(authentication)
}