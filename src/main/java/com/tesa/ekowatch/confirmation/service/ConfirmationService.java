package com.tesa.ekowatch.confirmation.service;

import com.tesa.ekowatch.confirmation.api.ConfirmationApi;
import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ConfirmationService implements ConfirmationApi {
    private final JdbcTemplate db;
    private final ActorAccess actors;
    private final ReportApi reports;
    private final Clock clock;

    public ConfirmationService(JdbcTemplate db, ActorAccess actors, ReportApi reports, Clock clock) {
        this.db=db; this.actors=actors; this.reports=reports; this.clock=clock;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReportView confirmReport(UUID actorId, UUID reportId) {
        Instant transactionStartedAt=clock.instant().truncatedTo(ChronoUnit.MICROS);
        actors.requireRole(actorId,"RESIDENT");
        ReportView before=reports.lockForConfirmation(actorId,reportId,transactionStartedAt);
        int inserted=db.update("""
            insert into report_confirmations(id,report_id,resident_id,confirmed_at) values (?,?,?,?)
            on conflict on constraint uq_report_confirmation_resident do nothing
            """,UUID.randomUUID(),reportId,actorId,Timestamp.from(transactionStartedAt));
        if(inserted==0 && before.reportType()==ReportType.FLOODED_ROAD && before.floodStatus()==FloodStatus.EXPIRED) {
            db.update("update report_confirmations set confirmed_at=? where report_id=? and resident_id=?",
                    Timestamp.from(transactionStartedAt),reportId,actorId);
        }
        // Read own writes under the same lock; the transaction interceptor commits before returning to caller.
        return reports.getReport(actorId,reportId);
    }
}
