package com.tesa.ekowatch.report.api;

import com.tesa.ekowatch.shared.Page;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

/** All actorId parameters are private inputs from a trusted principal, never client JSON. */
public interface ReportApi {
    ReportView createReport(UUID actorId, CreateReportRequest request);
    ReportView getReport(UUID actorId, UUID reportId);
    Page<ReportView> listReports(UUID actorId, UUID wardId, UUID streetId, ReportType reportType,
                               FloodStatus floodStatus, Integer page, Integer size, ReportSort sort);

    /** Existing guarded read retained until the authority module replaces it in Step 6. */
    List<ReportView> authorityReports(UUID actorId, int limit, int offset);

    /** Internal confirmation collaboration only, not an HTTP operation. */
    ReportView lockForConfirmation(UUID actorId, UUID reportId, Instant transactionStartedAt);
}
