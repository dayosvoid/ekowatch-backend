package com.tesa.ekowatch.report.api;
import java.time.Instant;
import java.util.*;
public record WardHazardsView(UUID wardId, Instant generatedAt, Instant newestReportAt, List<ReportView> items) {
    public WardHazardsView { items=List.copyOf(items); }
}
