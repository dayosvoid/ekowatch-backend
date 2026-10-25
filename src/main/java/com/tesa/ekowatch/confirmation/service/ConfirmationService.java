package com.tesa.ekowatch.confirmation.service;

import com.tesa.ekowatch.confirmation.api.ConfirmationApi;
import com.tesa.ekowatch.report.api.ReportApi;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.shared.RuleViolation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.UUID;

@Service
public class ConfirmationService implements ConfirmationApi {
    private final JdbcTemplate db;
    private final ActorAccess actors;
    private final ReportApi reports;

    public ConfirmationService(JdbcTemplate db, ActorAccess actors, ReportApi reports) {
        this.db = db;
        this.actors = actors;
        this.reports = reports;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void confirm(UUID actor, UUID report) {
        actors.requireRole(actor, "RESIDENT");
        // Compatible shared row locks stabilize eligibility while residents insert independently.
        reports.requireConfirmable(report, actor);
        int inserted = db.update("insert into confirmations(id,report_id,user_id) values (?,?,?) on conflict on constraint uq_confirmation_report_user do nothing", UUID.randomUUID(), report, actor);
        if (inserted == 0) throw new RuleViolation("DUPLICATE_CONFIRMATION");
    }
}
