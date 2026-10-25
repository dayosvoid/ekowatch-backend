package com.tesa.ekowatch.report.service;

import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.shared.RuleViolation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ReportService implements ReportApi {
    private final JdbcTemplate db;
    private final ActorAccess actors;

    public ReportService(JdbcTemplate db, ActorAccess actors) {
        this.db = db;
        this.actors = actors;
    }

    private static final String SELECT = "select r.*, (select count(*) from confirmations c where c.report_id=r.id) confirmations from reports r ";

    private ReportView map(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new ReportView(r.getObject("id", UUID.class), r.getObject("reporter_id", UUID.class), r.getObject("ward_id", UUID.class), r.getObject("street_id", UUID.class), r.getString("report_type"), r.getString("description"), r.getString("status"), r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant(), r.getLong("confirmations"));
    }

    public ReportView create(UUID actor, UUID ward, UUID street, String description) {
        actors.requireRole(actor, "RESIDENT");
        if (description == null || description.isBlank() || description.length() > 2000)
            throw new RuleViolation("INVALID_REPORT");
        if (!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from streets where id=? and ward_id=?)", Boolean.class, street, ward)))
            throw new RuleViolation("INVALID_LOCATION");
        UUID id = UUID.randomUUID();
        db.update("insert into reports(id,reporter_id,ward_id,street_id,report_type,description) values (?,?,?,?,'BLOCKED_DRAIN',?)", id, actor, ward, street, description.trim());
        return find(id);
    }

    private ReportView find(UUID id) {
        return db.query(SELECT + "where r.id=?", this::map, id).stream().findFirst().orElseThrow(() -> new RuleViolation("REPORT_NOT_FOUND"));
    }

    public ReportView get(UUID actor, UUID id) {
        actors.requireUser(actor);
        return find(id);
    }

    public List<ReportView> list(UUID actor, int limit, int offset) {
        actors.requireUser(actor);
        return page(limit, offset);
    }

    public List<ReportView> authorityReports(UUID actor, int limit, int offset) {
        actors.requireRole(actor, "AUTHORITY");
        return page(limit, offset);
    }

    private List<ReportView> page(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw new RuleViolation("INVALID_PAGE");
        return db.query(SELECT + "order by r.created_at desc,r.id limit ? offset ?", this::map, limit, offset);
    }

    public void requireConfirmable(UUID id, UUID actor) {
        var rows = db.queryForList("select reporter_id,status from reports where id=? for share", id);
        if (rows.isEmpty()) throw new RuleViolation("REPORT_NOT_FOUND");
        var row = rows.getFirst();
        if (!"OPEN".equals(row.get("status"))) throw new RuleViolation("REPORT_NOT_ELIGIBLE");
        if (actor.equals(row.get("reporter_id"))) throw new RuleViolation("SELF_CONFIRMATION");
    }
}
