package com.tesa.ekowatch.report.api;

import java.util.*;

public interface ReportApi {
    ReportView create(UUID actorId, UUID wardId, UUID streetId, String description);

    ReportView get(UUID actorId, UUID reportId);

    List<ReportView> list(UUID actorId, int limit, int offset);

    List<ReportView> authorityReports(UUID actorId, int limit, int offset);

    void requireConfirmable(UUID reportId, UUID actorId);
}
