package com.tesa.ekowatch.collection.service;

import com.tesa.ekowatch.collection.api.*;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.ward.api.StreetView;
import com.tesa.ekowatch.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class CollectionService implements CollectionApi {
    private static final ZoneId LAGOS=ZoneId.of("Africa/Lagos");
    private final JdbcTemplate db;
    private final ActorAccess actors;
    private final Clock clock;
    public CollectionService(JdbcTemplate db, ActorAccess actors, Clock clock) {
        this.db=db; this.actors=actors; this.clock=clock;
    }

    private void range(LocalDate from,LocalDate to) {
        if(from==null || to==null || from.isAfter(to) || ChronoUnit.DAYS.between(from,to)>30)
            throw new RuleViolation("VALIDATION_FAILED");
    }

    public List<CollectionScheduleView> listCollectionSchedules(UUID actorId,UUID wardId,LocalDate fromDate,LocalDate toDate) {
        actors.requireUser(actorId); range(fromDate,toDate);
        if(wardId==null) throw new RuleViolation("VALIDATION_FAILED");
        if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from wards where id=?)",Boolean.class,wardId)))
            throw new RuleViolation("RESOURCE_NOT_FOUND");
        LocalDate today=LocalDate.ofInstant(clock.instant(),LAGOS);
        return db.query("""
            select cs.id,s.ward_id,cs.street_id,s.name,cs.collection_date,c.completed_at,
             (select count(*) from reports r where r.street_id=cs.street_id
               and r.collection_date=cs.collection_date and r.report_type='MISSED_PICKUP') missed_report_count
            from collection_schedules cs join streets s on s.id=cs.street_id
            left join collections c on c.street_id=cs.street_id and c.collection_date=cs.collection_date
            where s.ward_id=? and cs.collection_date between ? and ?
            order by cs.collection_date,s.name,cs.street_id,cs.id
            """,(r,n)-> {
                Timestamp completed=r.getTimestamp("completed_at");
                LocalDate date=r.getObject("collection_date",LocalDate.class);
                CollectionStatus status=completed!=null ? CollectionStatus.DONE :
                        date.isBefore(today) ? CollectionStatus.MISSED : CollectionStatus.SCHEDULED;
                return new CollectionScheduleView(r.getObject("id",UUID.class),r.getObject("ward_id",UUID.class),
                        r.getObject("street_id",UUID.class),r.getString("name"),date,status,
                        completed==null ? null : completed.toInstant(),r.getLong("missed_report_count"));
            },wardId,fromDate,toDate);
    }

    public List<StreetView> listMyAssignedStreets(UUID actorId,LocalDate fromDate,LocalDate toDate) {
        actors.requireRole(actorId,"OPERATOR"); range(fromDate,toDate);
        return db.query("""
            select distinct s.id,s.ward_id,s.name,s.latitude,s.longitude from collection_schedules cs
            join streets s on s.id=cs.street_id join operator_wards ow on ow.ward_id=s.ward_id and ow.operator_id=cs.operator_id
            where cs.operator_id=? and cs.collection_date between ? and ? order by s.name,s.id
            """,(r,n)->new StreetView(r.getObject("id",UUID.class),r.getObject("ward_id",UUID.class),r.getString("name"),
                r.getObject("latitude")==null ? null : new Location(r.getDouble("latitude"),r.getDouble("longitude"))),
                actorId,fromDate,toDate);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CollectionView recordCollection(UUID actorId,UUID streetId,LocalDate collectionDate) {
        actors.requireRole(actorId,"OPERATOR");
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        if(streetId==null || collectionDate==null || collectionDate.isAfter(LocalDate.ofInstant(now,LAGOS)))
            throw new RuleViolation("VALIDATION_FAILED");
        var wards=db.query("select ward_id from streets where id=?",(r,n)->r.getObject(1,UUID.class),streetId);
        if(wards.isEmpty()) throw new RuleViolation("RESOURCE_NOT_FOUND");
        if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from operator_wards where operator_id=? and ward_id=?)",
                Boolean.class,actorId,wards.getFirst()))) throw new RuleViolation("WARD_ASSIGNMENT_REQUIRED");
        var scheduled=db.query("select operator_id from collection_schedules where street_id=? and collection_date=?",
                (r,n)->r.getObject(1,UUID.class),streetId,collectionDate);
        if(scheduled.isEmpty()) throw new RuleViolation("VALIDATION_FAILED");
        if(!actorId.equals(scheduled.getFirst())) throw new RuleViolation("FORBIDDEN");
        db.update("""
            insert into collections(id,street_id,collection_date,completed_by,completed_at) values (?,?,?,?,?)
            on conflict on constraint uq_collection_street_date do nothing
            """,UUID.randomUUID(),streetId,collectionDate,actorId,Timestamp.from(now));
        return db.queryForObject("select id,street_id,collection_date,completed_at from collections where street_id=? and collection_date=?",
            (r,n)->new CollectionView(r.getObject("id",UUID.class),r.getObject("street_id",UUID.class),
                r.getObject("collection_date",LocalDate.class),r.getTimestamp("completed_at").toInstant(),CollectionStatus.DONE),
            streetId,collectionDate);
    }
}
