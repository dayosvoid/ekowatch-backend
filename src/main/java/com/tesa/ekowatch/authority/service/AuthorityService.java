package com.tesa.ekowatch.authority.service;

import com.tesa.ekowatch.authority.api.*;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.shared.RuleViolation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class AuthorityService implements AuthorityApi {
    private final JdbcTemplate db;
    private final ActorAccess actors;
    private final Clock clock;
    private final Duration freshness;
    public AuthorityService(JdbcTemplate db, ActorAccess actors, Clock clock,
            @Value("${ekowatch.flood-freshness-hours:6}") long hours) {
        this.db=db; this.actors=actors; this.clock=clock;
        if(hours<=0) throw new IllegalArgumentException("Flood freshness must be positive");
        freshness=Duration.ofHours(hours);
    }
    public WardDashboardView getWardDashboard(UUID actorId, UUID wardId, LocalDate fromDate, LocalDate toDate) {
        return com.tesa.ekowatch.shared.OperationLog.record("getWardDashboard", () -> {
            actors.requireRole(actorId,"AUTHORITY");
            if(wardId==null || fromDate==null || toDate==null || fromDate.isAfter(toDate) ||
                    ChronoUnit.DAYS.between(fromDate,toDate)>30) throw new RuleViolation("VALIDATION_FAILED");
            if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from wards where id=?)",Boolean.class,wardId)))
                throw new RuleViolation("RESOURCE_NOT_FOUND");
            Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);
            LocalDate today=LocalDate.ofInstant(now,ZoneId.of("Africa/Lagos"));
            var performance=db.query("""
                with occurrences as (
                    select cs.operator_id,c.id is not null completed,cs.collection_date,
                        exists(select 1 from reports r where r.street_id=cs.street_id
                            and r.collection_date=cs.collection_date and r.report_type='MISSED_PICKUP') reported
                    from collection_schedules cs join streets s on s.id=cs.street_id
                    left join collections c on c.street_id=cs.street_id and c.collection_date=cs.collection_date
                    where s.ward_id=? and cs.collection_date between ? and ?
                )
                select u.id,u.display_name,count(*) scheduled,
                    count(*) filter(where completed) completed,
                    count(*) filter(where not completed and collection_date < ?) missed,
                    count(*) filter(where completed and reported) disputed
                from occurrences o join users u on u.id=o.operator_id
                group by u.id,u.display_name order by u.display_name,u.id
                """,(r,n)->new OperatorPerformanceView(r.getObject("id",UUID.class),r.getString("display_name"),
                    r.getLong("scheduled"),r.getLong("completed"),r.getLong("missed"),r.getLong("disputed")),
                    wardId,fromDate,toDate,today);
            return db.queryForObject("""
                select count(*) filter(where report_type='BLOCKED_DRAIN') blocked,
                    count(*) filter(where report_type='FLOODED_ROAD' and
                        greatest(r.created_at,coalesce((select max(confirmed_at) from report_confirmations c
                            where c.report_id=r.id),r.created_at)) > ?) floods,
                    max(created_at) newest from reports r where ward_id=?
                """,(r,n)->new WardDashboardView(wardId,fromDate,toDate,now,
                    performance.stream().mapToLong(OperatorPerformanceView::missedPickupCount).sum(),
                    performance.stream().mapToLong(OperatorPerformanceView::disputedPickupCount).sum(),
                    r.getLong("blocked"),r.getLong("floods"),r.getTimestamp("newest")==null ? null : r.getTimestamp("newest").toInstant(),performance),
                    Timestamp.from(now.minus(freshness)),wardId);
        });
    }
}
