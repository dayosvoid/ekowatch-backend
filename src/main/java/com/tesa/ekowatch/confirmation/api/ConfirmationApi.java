package com.tesa.ekowatch.confirmation.api;

import java.util.UUID;
import com.tesa.ekowatch.report.api.ReportView;

public interface ConfirmationApi {
    /** actorId is private trusted-principal input, never client JSON. Returns after transaction commit. */
    ReportView confirmReport(UUID actorId, UUID reportId);
}
