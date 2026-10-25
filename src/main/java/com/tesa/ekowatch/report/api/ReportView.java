package com.tesa.ekowatch.report.api;

import java.time.Instant;
import java.util.UUID;

public record ReportView(UUID id, UUID reporterId, UUID wardId, UUID streetId, String reportType, String description,
                         String status, Instant createdAt, Instant updatedAt, long confirmationCount) {
}
