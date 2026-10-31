package com.tesa.ekowatch.report.api;

import com.tesa.ekowatch.shared.Location;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportView(UUID reportId, ReportType reportType, UUID wardId, UUID streetId,
                         String streetName, String note, String photoUrl, Instant createdAt,
                         LocalDate collectionDate, long confirmationCount, boolean confirmedByMe,
                         Instant lastEvidenceAt, FloodStatus floodStatus, Instant expiresAt,
                         Location mapLocation, String locationPrecision) {}
