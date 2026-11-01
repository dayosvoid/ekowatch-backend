package com.tesa.ekowatch.authority.api;
import java.time.LocalDate;
import java.util.UUID;
/** actorId comes only from a trusted principal, never client JSON. */
public interface AuthorityApi {
    WardDashboardView getWardDashboard(UUID actorId, UUID wardId, LocalDate fromDate, LocalDate toDate);
}
