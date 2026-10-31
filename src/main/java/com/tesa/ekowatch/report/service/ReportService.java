package com.tesa.ekowatch.report.service;

import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.photo.api.PhotoApi;
import com.tesa.ekowatch.collection.api.CollectionApi;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class ReportService implements ReportApi {
    private final JdbcTemplate db;
    private final ActorAccess actors;
    private final PhotoApi photos;
    private final Clock clock;
    private final CollectionApi collections;
    private final Duration floodFreshness;

    public ReportService(JdbcTemplate db, ActorAccess actors, PhotoApi photos, Clock clock, CollectionApi collections,
                         @Value("${ekowatch.flood-freshness-hours:6}") long floodFreshnessHours) {
        this.db = db; this.actors = actors; this.photos = photos; this.clock = clock; this.collections = collections;
        if (floodFreshnessHours <= 0) throw new IllegalArgumentException("ekowatch.flood-freshness-hours must be positive");
        this.floodFreshness = Duration.ofHours(floodFreshnessHours);
    }

    // Select only projection data. Private submitted coordinates and reporter identity are not read.
    private static final String VIEWS = """
            with report_views as (
              select r.id as report_id,r.report_type,r.ward_id,r.street_id,s.name as street_name,
                     r.note,r.photo_id,r.created_at,r.collection_date,
                     s.latitude as street_latitude,s.longitude as street_longitude,
                     c.confirmation_count,coalesce(c.confirmed_by_me,false) as confirmed_by_me,
                     greatest(r.created_at,coalesce(c.last_confirmation,r.created_at)) as last_evidence_at
              from reports r join streets s on s.id=r.street_id and s.ward_id=r.ward_id
              left join lateral (
                select count(*) as confirmation_count,max(confirmed_at) as last_confirmation,
                       bool_or(resident_id=?) as confirmed_by_me
                from report_confirmations where report_id=r.id
              ) c on true
            )
            """;

    private ReportView map(ResultSet r, UUID actorId, Instant now) throws SQLException {
        ReportType type = ReportType.valueOf(r.getString("report_type"));
        Instant lastEvidenceAt = r.getTimestamp("last_evidence_at").toInstant();
        Instant expiresAt = type == ReportType.FLOODED_ROAD ? lastEvidenceAt.plus(floodFreshness) : null;
        FloodStatus floodStatus = expiresAt == null ? null : now.isBefore(expiresAt) ? FloodStatus.CURRENT : FloodStatus.EXPIRED;
        UUID photoId = r.getObject("photo_id", UUID.class);
        String photoUrl = photoId == null ? null : photos.getPhoto(actorId, photoId).photoUrl();
        Location mapLocation = r.getObject("street_latitude") == null ? null :
                new Location(r.getDouble("street_latitude"), r.getDouble("street_longitude"));
        return new ReportView(r.getObject("report_id", UUID.class), type, r.getObject("ward_id", UUID.class),
                r.getObject("street_id", UUID.class), r.getString("street_name"), r.getString("note"), photoUrl,
                r.getTimestamp("created_at").toInstant(), r.getObject("collection_date", LocalDate.class),
                r.getLong("confirmation_count"), r.getBoolean("confirmed_by_me"), lastEvidenceAt,
                floodStatus, expiresAt, mapLocation, "STREET");
    }

    public ReportView createReport(UUID actorId, CreateReportRequest request) {
        actors.requireRole(actorId, "RESIDENT");
        if (request == null || request.reportType() == null || request.streetId() == null || request.note() == null)
            throw new RuleViolation("VALIDATION_FAILED");
        String note = request.note().trim();
        if (note.isBlank() || note.codePointCount(0, note.length()) > 1000)
            throw new RuleViolation("VALIDATION_FAILED");
        Location location = request.location();
        if (location != null && (location.latitude() == null || location.longitude() == null ||
                !Double.isFinite(location.latitude()) || !Double.isFinite(location.longitude()) ||
                location.latitude() < -90 || location.latitude() > 90 ||
                location.longitude() < -180 || location.longitude() > 180)) throw new RuleViolation("VALIDATION_FAILED");
        if ((request.reportType() == ReportType.MISSED_PICKUP) != (request.collectionDate() != null))
            throw new RuleViolation("VALIDATION_FAILED");
        var wards = db.query("select ward_id from streets where id=?", (r,n) -> r.getObject(1,UUID.class), request.streetId());
        if (wards.isEmpty()) throw new RuleViolation("RESOURCE_NOT_FOUND");
        if (request.reportType() == ReportType.MISSED_PICKUP) {
            LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Africa/Lagos"));
            if (!request.collectionDate().isBefore(today) || collections.listCollectionSchedules(actorId, wards.getFirst(),
                    request.collectionDate(), request.collectionDate()).stream().noneMatch(s -> s.streetId().equals(request.streetId())))
                throw new RuleViolation("VALIDATION_FAILED");
        }
        if (request.photoId() != null) {
            var owners = db.query("select owner_id from photos where id=?", (r,n) -> r.getObject(1,UUID.class), request.photoId());
            if (owners.isEmpty()) throw new RuleViolation("RESOURCE_NOT_FOUND");
            if (!actorId.equals(owners.getFirst())) throw new RuleViolation("FORBIDDEN");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        db.update("""
                insert into reports(id,reporter_id,ward_id,street_id,report_type,note,photo_id,
                                    latitude,longitude,collection_date,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, actorId, wards.getFirst(), request.streetId(), request.reportType().name(), note, request.photoId(),
                location == null ? null : location.latitude(), location == null ? null : location.longitude(),
                request.collectionDate(), Timestamp.from(now), Timestamp.from(now));
        return find(actorId, id, now);
    }

    private ReportView find(UUID actorId, UUID reportId, Instant now) {
        if (reportId == null) throw new RuleViolation("VALIDATION_FAILED");
        return db.query(VIEWS + "select * from report_views where report_id=?", (r,n) -> map(r,actorId,now), actorId,reportId)
                .stream().findFirst().orElseThrow(() -> new RuleViolation("RESOURCE_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public ReportView getReport(UUID actorId, UUID reportId) {
        actors.requireUser(actorId);
        return find(actorId, reportId, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ReportView> listReports(UUID actorId, UUID wardId, UUID streetId, ReportType reportType,
                                        FloodStatus floodStatus, Integer page, Integer size, ReportSort sort) {
        actors.requireUser(actorId);
        int requestedPage = page == null ? 0 : page;
        int requestedSize = size == null ? 20 : size;
        if (wardId == null || requestedPage < 0 || requestedSize < 1 || requestedSize > 100 ||
                (floodStatus != null && reportType != ReportType.FLOODED_ROAD)) throw new RuleViolation("VALIDATION_FAILED");
        if (!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from wards where id=?)",Boolean.class,wardId)))
            throw new RuleViolation("RESOURCE_NOT_FOUND");
        if (streetId != null && !Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from streets where id=? and ward_id=?)",
                Boolean.class,streetId,wardId))) throw new RuleViolation("VALIDATION_FAILED");
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        StringBuilder where = new StringBuilder(" where ward_id=?");
        List<Object> args = new ArrayList<>();
        args.add(actorId); args.add(wardId);
        if (streetId != null) { where.append(" and street_id=?"); args.add(streetId); }
        if (reportType != null) { where.append(" and report_type=?"); args.add(reportType.name()); }
        if (floodStatus != null) {
            where.append(floodStatus == FloodStatus.CURRENT ? " and last_evidence_at > ?" : " and last_evidence_at <= ?");
            args.add(Timestamp.from(now.minus(floodFreshness)));
        }
        long total = Objects.requireNonNull(db.queryForObject(VIEWS + "select count(*) from report_views" + where, Long.class, args.toArray()));
        String order = sort == ReportSort.NEWEST ? "created_at desc,report_id" : "confirmation_count desc,created_at desc,report_id";
        args.add(requestedSize); args.add((long) requestedPage * requestedSize);
        var items = db.query(VIEWS + "select * from report_views" + where + " order by " + order + " limit ? offset ?",
                (r,n) -> map(r,actorId,now), args.toArray());
        return new Page<>(items,requestedPage,requestedSize,total,(int) ((total + requestedSize - 1) / requestedSize));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WardHazardsView listWardHazards(UUID actorId, UUID wardId) {
        actors.requireUser(actorId);
        if (wardId == null) throw new RuleViolation("VALIDATION_FAILED");
        if (!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from wards where id=?)",Boolean.class,wardId)))
            throw new RuleViolation("RESOURCE_NOT_FOUND");
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var items=db.query(VIEWS + """
            select * from report_views where ward_id=? and
            (report_type='BLOCKED_DRAIN' or (report_type='FLOODED_ROAD' and last_evidence_at > ?))
            order by created_at desc,report_id
            """,(r,n)->map(r,actorId,now),actorId,wardId,Timestamp.from(now.minus(floodFreshness)));
        Instant newest=db.queryForObject("select max(created_at) from reports where ward_id=?",
                (r,n)->r.getTimestamp(1)==null ? null : r.getTimestamp(1).toInstant(),wardId);
        return new WardHazardsView(wardId,now,newest,items);
    }
    // Internal collaboration: every evidence writer locks this row first in the same transaction.
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ReportView lockForConfirmation(UUID actorId, UUID id, Instant transactionStartedAt) {
        actors.requireRole(actorId, "RESIDENT");
        if (id == null || transactionStartedAt == null) throw new RuleViolation("VALIDATION_FAILED");
        var rows = db.queryForList("select reporter_id from reports where id=? for update", id);
        if (rows.isEmpty()) throw new RuleViolation("RESOURCE_NOT_FOUND");
        var row = rows.getFirst();
        if (actorId.equals(row.get("reporter_id"))) throw new RuleViolation("SELF_CONFIRMATION_NOT_ALLOWED");
        return find(actorId, id, transactionStartedAt);
    }
}
