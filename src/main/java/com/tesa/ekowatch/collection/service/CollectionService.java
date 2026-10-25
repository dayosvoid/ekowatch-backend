package com.tesa.ekowatch.collection.service;

import com.tesa.ekowatch.collection.api.CollectionApi;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.shared.RuleViolation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Transactional
public class CollectionService implements CollectionApi {
    private final JdbcTemplate db;
    private final ActorAccess actors;

    public CollectionService(JdbcTemplate db, ActorAccess actors) {
        this.db = db;
        this.actors = actors;
    }

    public void recordPickup(UUID actor, UUID street, LocalDate date, String status) {
        actors.requireRole(actor, "OPERATOR");
        if (date == null || date.isAfter(LocalDate.now(ZoneId.of("Africa/Lagos"))) || !("COMPLETED".equals(status) || "FAILED".equals(status)))
            throw new RuleViolation("INVALID_PICKUP");
        if (!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from streets s join operator_wards o on o.ward_id=s.ward_id where s.id=? and o.operator_id=?)", Boolean.class, street, actor)))
            throw new RuleViolation("FORBIDDEN");
        if (db.update("insert into pickups(id,street_id,pickup_date,status,recorded_by) values (?,?,?,?,?) on conflict on constraint uq_pickup_street_date do nothing", UUID.randomUUID(), street, date, status, actor) == 0)
            throw new RuleViolation("DUPLICATE_PICKUP");
    }

    @Transactional(readOnly = true)
    public List<MissedPickup> missed(UUID actor, LocalDate from, LocalDate through) {
        actors.requireRole(actor, "AUTHORITY");
        if (from == null || through == null || from.isAfter(through) || java.time.temporal.ChronoUnit.DAYS.between(from, through) > 366 || !through.isBefore(LocalDate.now(ZoneId.of("Africa/Lagos"))))
            throw new RuleViolation("INVALID_DATE_RANGE");
        return db.query("select s.street_id,d::date as scheduled_date from collection_schedules s cross join generate_series(cast(? as date),cast(? as date),interval '1 day') d where s.active and s.scheduled_day=extract(isodow from d) and not exists(select 1 from pickups p where p.street_id=s.street_id and p.pickup_date=d::date and p.status='COMPLETED') order by scheduled_date,s.street_id", (r, n) -> new MissedPickup(r.getObject(1, UUID.class), r.getObject(2, LocalDate.class)), from, through);
    }
}
