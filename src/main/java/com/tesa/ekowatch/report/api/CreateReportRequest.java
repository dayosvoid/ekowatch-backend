package com.tesa.ekowatch.report.api;
import com.tesa.ekowatch.shared.Location;
import java.time.LocalDate;
import java.util.UUID;
/** Actor, reporter and ward IDs are derived by the service, not client fields. */
public record CreateReportRequest(ReportType reportType, UUID streetId, String note,
                                  UUID photoId, Location location, LocalDate collectionDate) {}
