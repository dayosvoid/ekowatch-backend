package com.tesa.ekowatch;

import com.tesa.ekowatch.collection.api.*;
import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.shared.RuleViolation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Import(ResidentReportApiTest.Configuration.class)
class CollectionRulesTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17-alpine").withUrlParam("sslmode","disable");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl); r.add("spring.datasource.username",pg::getUsername); r.add("spring.datasource.password",pg::getPassword);
    }
    @Autowired JdbcTemplate db;
    @Autowired CollectionApi collections;
    @Autowired ReportApi reports;
    @Autowired ResidentReportApiTest.TestClock clock;
    @Autowired ObjectMapper json;
    UUID operator,resident,authority,otherOperator,unassigned,ward,otherWard,street,otherStreet;
    final LocalDate yesterday=LocalDate.of(2026,10,30),today=LocalDate.of(2026,10,31);
    UUID user(String role) {
        UUID id=UUID.randomUUID();
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'disabled','Fictional',?)",id,id+"@example.invalid",role); return id;
    }
    @BeforeEach void fixture() {
        clock.set(Instant.parse("2026-10-30T23:30:00Z")); // Lagos is already 31 October.
        operator=user("OPERATOR"); otherOperator=user("OPERATOR"); unassigned=user("OPERATOR"); resident=user("RESIDENT"); authority=user("AUTHORITY");
        ward=UUID.randomUUID(); otherWard=UUID.randomUUID(); street=UUID.randomUUID(); otherStreet=UUID.randomUUID();
        db.update("insert into wards(id,name) values (?,?),(?,?)",ward,"A "+ward,otherWard,"B "+otherWard);
        db.update("insert into streets(id,ward_id,name) values (?,?,'Main'),(?,?,'Other')",street,ward,otherStreet,otherWard);
        db.update("insert into operator_wards values (?,?),(?,?)",operator,ward,otherOperator,ward);
        schedule(street,operator,yesterday); schedule(street,operator,today); schedule(street,operator,today.plusDays(1));
    }
    void schedule(UUID streetId,UUID actor,LocalDate date) {
        db.update("insert into collection_schedules(id,street_id,operator_id,collection_date) values (?,?,?,?)",UUID.randomUUID(),streetId,actor,date);
    }
    void violation(String code,org.junit.jupiter.api.function.Executable action) { assertEquals(code,assertThrows(RuleViolation.class,action).code()); }
    List<CollectionScheduleView> views(LocalDate from,LocalDate to) { return collections.listCollectionSchedules(resident,ward,from,to); }

    @Test void statusesFollowLagosCalendarWithoutWritesAndReportsDoNotPrematurelyDispute() {
        var initial=views(yesterday,today.plusDays(1));
        assertEquals(List.of(CollectionStatus.MISSED,CollectionStatus.SCHEDULED,CollectionStatus.SCHEDULED),initial.stream().map(CollectionScheduleView::collectionStatus).toList());
        assertTrue(initial.stream().allMatch(v->v.completedAt()==null));
        var stored=db.queryForList("select * from collection_schedules where street_id=? order by collection_date",street);
        clock.set(Instant.parse("2026-10-30T22:59:59.999999Z"));
        assertEquals(CollectionStatus.SCHEDULED,views(yesterday,yesterday).getFirst().collectionStatus());
        clock.set(Instant.parse("2026-10-30T23:00:00Z"));
        assertEquals(CollectionStatus.MISSED,views(yesterday,yesterday).getFirst().collectionStatus());
        reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.MISSED_PICKUP,street,"Missed",null,null,yesterday));
        assertEquals(1,views(yesterday,yesterday).getFirst().missedReportCount());
        clock.set(Instant.parse("2026-10-30T22:59:59Z"));
        assertEquals(CollectionStatus.SCHEDULED,views(yesterday,yesterday).getFirst().collectionStatus());
        assertEquals(0,dashboard().missedPickupCount());
        clock.set(Instant.parse("2026-10-30T23:30:00Z"));
        var completion=collections.recordCollection(operator,street,yesterday);
        var done=views(yesterday,yesterday).getFirst();
        assertEquals(CollectionStatus.DISPUTED,done.collectionStatus()); assertEquals(completion.completedAt(),done.completedAt());
        assertEquals(1,done.missedReportCount()); // Both facts survive the derived dispute.
        assertEquals(stored,db.queryForList("select * from collection_schedules where street_id=? order by collection_date",street));
    }

    @Test void completionRepeatsKeepOriginalUuidAndTimestampIncludingLagosToday() {
        var first=collections.recordCollection(operator,street,today);
        assertEquals(Instant.parse("2026-10-30T23:30:00Z"),first.completedAt());
        clock.set(clock.instant().plusSeconds(3600));
        assertEquals(first,collections.recordCollection(operator,street,today));
        assertEquals(1,db.queryForObject("select count(*) from collections where street_id=?",Integer.class,street));
        db.update("delete from operator_wards where operator_id=? and ward_id=?",operator,ward);
        violation("WARD_ASSIGNMENT_REQUIRED",()->collections.recordCollection(operator,street,today));
        assertEquals(first.completedAt(),db.queryForObject("select completed_at from collections where id=?",java.sql.Timestamp.class,first.collectionId()).toInstant());
    }

    @Test void concurrentRepeatsReturnOneCommittedRecord() throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<CollectionView> task=()-> { ready.countDown(); assertTrue(start.await(10,TimeUnit.SECONDS)); return collections.recordCollection(operator,street,yesterday); };
            var a=pool.submit(task); var b=pool.submit(task);
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            assertEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
        assertEquals(1,db.queryForObject("select count(*) from collections where street_id=? and collection_date=?",Integer.class,street,yesterday));
    }

    @Test void wrongRolesWardsUnscheduledAndFutureWritesAreRejected() {
        for(UUID actor:List.of(resident,authority,UUID.randomUUID())) violation("FORBIDDEN",()->collections.recordCollection(actor,street,yesterday));
        violation("WARD_ASSIGNMENT_REQUIRED",()->collections.recordCollection(unassigned,street,yesterday));
        violation("WARD_ASSIGNMENT_REQUIRED",()->collections.recordCollection(operator,otherStreet,yesterday));
        violation("FORBIDDEN",()->collections.recordCollection(otherOperator,street,yesterday));
        violation("VALIDATION_FAILED",()->collections.recordCollection(operator,street,today.plusDays(1)));
        violation("VALIDATION_FAILED",()->collections.recordCollection(operator,street,yesterday.minusDays(1)));
        violation("VALIDATION_FAILED",()->collections.recordCollection(operator,street,null));
        violation("VALIDATION_FAILED",()->collections.recordCollection(operator,null,today));
        violation("RESOURCE_NOT_FOUND",()->collections.recordCollection(operator,UUID.randomUUID(),today));
        assertEquals(0,db.queryForObject("select count(*) from collections where street_id=?",Integer.class,street));
        LocalDate databaseTomorrow=db.queryForObject("select (CURRENT_TIMESTAMP at time zone 'Africa/Lagos')::date+1",LocalDate.class);
        db.update("insert into collection_schedules(id,street_id,operator_id,collection_date) values (?,?,?,?) on conflict(street_id,collection_date) do nothing",UUID.randomUUID(),street,operator,databaseTomorrow);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update(
            "insert into collections(id,street_id,collection_date,completed_by,completed_at) values (?,?,?,?,now())",
            UUID.randomUUID(),street,databaseTomorrow,operator));
    }

    @Test void rangesAreInclusiveWardScopedAndAssignedStreetsAreDistinct() {
        assertEquals(2,views(today.minusDays(30),today).size()); // Exactly 31 inclusive dates.
        violation("VALIDATION_FAILED",()->views(today.minusDays(31),today));
        violation("VALIDATION_FAILED",()->views(today,yesterday));
        violation("VALIDATION_FAILED",()->views(null,today));
        violation("RESOURCE_NOT_FOUND",()->collections.listCollectionSchedules(resident,UUID.randomUUID(),today,today));
        var assigned=collections.listMyAssignedStreets(operator,yesterday,today.plusDays(1));
        assertEquals(List.of(street),assigned.stream().map(com.tesa.ekowatch.ward.api.StreetView::streetId).toList());
        assertTrue(collections.listMyAssignedStreets(otherOperator,yesterday,today).isEmpty());
        assertTrue(collections.listMyAssignedStreets(unassigned,yesterday,today).isEmpty());
        violation("FORBIDDEN",()->collections.listMyAssignedStreets(resident,yesterday,today));
        violation("VALIDATION_FAILED",()->collections.listMyAssignedStreets(operator,today,today.plusDays(31)));
        Set<String> fields=new HashSet<>(); json.valueToTree(views(yesterday,today).getFirst()).fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("scheduleId","wardId","streetId","streetName","collectionDate","collectionStatus","completedAt","missedReportCount"),fields);
        assertFalse(fields.contains("operatorId"));
    }

    @Test void missedReportsRequireExactElapsedOccurrenceAtServiceAndDatabaseBoundaries() {
        violation("VALIDATION_FAILED",()->reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.MISSED_PICKUP,street,"Missed",null,null,today)));
        violation("VALIDATION_FAILED",()->reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.MISSED_PICKUP,street,"Missed",null,null,yesterday.minusDays(1))));
        violation("VALIDATION_FAILED",()->reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.MISSED_PICKUP,otherStreet,"Missed",null,null,yesterday)));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update(
            "insert into reports(id,reporter_id,ward_id,street_id,report_type,note,collection_date) values (?,?,?,?,'MISSED_PICKUP','Missed',?)",
            UUID.randomUUID(),resident,ward,street,yesterday.minusDays(1)));
        LocalDate databaseToday=db.queryForObject("select (CURRENT_TIMESTAMP at time zone 'Africa/Lagos')::date",LocalDate.class);
        db.update("insert into collection_schedules(id,street_id,operator_id,collection_date) values (?,?,?,?) on conflict(street_id,collection_date) do nothing",UUID.randomUUID(),street,operator,databaseToday);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update(
            "insert into reports(id,reporter_id,ward_id,street_id,report_type,note,collection_date) values (?,?,?,?,'MISSED_PICKUP','Missed',?)",
            UUID.randomUUID(),resident,ward,street,databaseToday));
        assertNotNull(reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.MISSED_PICKUP,street,"Missed",null,null,yesterday)).reportId());
    }
    @Autowired com.tesa.ekowatch.authority.api.AuthorityApi authorities;
    @Autowired javax.sql.DataSource source;
    com.tesa.ekowatch.authority.api.WardDashboardView dashboard() {
        return authorities.getWardDashboard(authority,ward,yesterday,today.plusDays(1));
    }
    @Test void metricsUseExactDatesAndHistoricalOperatorWithIndependentHazardSnapshot() {
        schedule(street,otherOperator,yesterday.minusDays(1));
        collections.recordCollection(operator,street,yesterday);
        reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.MISSED_PICKUP,street,"Missed twice",null,null,yesterday));
        reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.MISSED_PICKUP,street,"Another witness",null,null,yesterday));
        assertEquals(CollectionStatus.MISSED,views(yesterday.minusDays(1),yesterday.minusDays(1)).getFirst().collectionStatus());
        reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Drain one",null,null,null));
        reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Drain two",null,null,null));
        var flood=reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.FLOODED_ROAD,street,"Flood",null,null,null));
        db.update("delete from operator_wards where operator_id=?",operator);
        var d=authorities.getWardDashboard(authority,ward,yesterday.minusDays(1),today.plusDays(1));
        assertEquals(1,d.missedPickupCount()); assertEquals(1,d.disputedPickupCount());
        assertEquals(2,d.blockedDrainCount()); assertEquals(1,d.currentFloodCount());
        assertEquals(clock.instant(),d.generatedAt()); assertEquals(flood.createdAt(),d.newestReportAt());
        var op=d.operatorPerformance().stream().filter(v->v.operatorId().equals(operator)).findFirst().orElseThrow();
        assertEquals(3,op.scheduledPickupCount()); assertEquals(1,op.completedPickupCount());
        assertEquals(0,op.missedPickupCount()); assertEquals(1,op.disputedPickupCount());
        var futureOnly=authorities.getWardDashboard(authority,ward,today.plusDays(10),today.plusDays(10));
        assertTrue(futureOnly.operatorPerformance().isEmpty());
        assertEquals(2,futureOnly.blockedDrainCount()); assertEquals(1,futureOnly.currentFloodCount());
        assertEquals(3,reports.listWardHazards(resident,ward).items().size());
        clock.set(flood.expiresAt());
        assertEquals(0,dashboard().currentFloodCount());
        assertEquals(2,reports.listWardHazards(resident,ward).items().size());
        clock.set(flood.expiresAt().plusSeconds(3600));
        assertEquals(0,dashboard().currentFloodCount());
        assertEquals(flood.lastEvidenceAt(),reports.getReport(resident,flood.reportId()).lastEvidenceAt());
    }
    @Test void emptyWardRolesRangesAndUntruncatedPrivateHazards() {
        var empty=authorities.getWardDashboard(authority,otherWard,today.minusDays(30),today);
        assertEquals(0,empty.missedPickupCount()); assertEquals(0,empty.disputedPickupCount());
        assertEquals(0,empty.blockedDrainCount()); assertEquals(0,empty.currentFloodCount());
        assertNull(empty.newestReportAt()); assertTrue(empty.operatorPerformance().isEmpty());
        assertTrue(reports.listWardHazards(resident,otherWard).items().isEmpty());
        assertNull(reports.listWardHazards(resident,otherWard).newestReportAt());
        for(UUID actor:List.of(resident,operator,UUID.randomUUID()))
            violation("FORBIDDEN",()->authorities.getWardDashboard(actor,ward,yesterday,today));
        violation("VALIDATION_FAILED",()->authorities.getWardDashboard(authority,ward,today.minusDays(31),today));
        violation("VALIDATION_FAILED",()->authorities.getWardDashboard(authority,ward,today,yesterday));
        violation("VALIDATION_FAILED",()->authorities.getWardDashboard(authority,ward,null,today));
        violation("RESOURCE_NOT_FOUND",()->authorities.getWardDashboard(authority,UUID.randomUUID(),today,today));
        violation("VALIDATION_FAILED",()->reports.listWardHazards(resident,null));
        violation("RESOURCE_NOT_FOUND",()->reports.listWardHazards(resident,UUID.randomUUID()));
        for(int i=0;i<105;i++) reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Drain",null,new com.tesa.ekowatch.shared.Location(6.0,3.0),null));
        var hazards=reports.listWardHazards(resident,ward);
        assertEquals(105,hazards.items().size()); assertEquals(105,dashboard().blockedDrainCount());
        assertEquals(hazards,reports.listWardHazards(resident,ward));
        var hazardJson=json.valueToTree(hazards);
        Set<String> hazardFields=new HashSet<>(); hazardJson.fieldNames().forEachRemaining(hazardFields::add);
        assertEquals(Set.of("wardId","generatedAt","newestReportAt","items"),hazardFields);
        assertEquals(105,hazardJson.get("items").size());
        var publicJson=hazardJson.toString();
        for(String field:List.of("reporterId","operatorId","operatorPerformance","latitude","longitude")) assertFalse(publicJson.contains(field));
        assertTrue(hazards.items().stream().allMatch(v->v.mapLocation()==null));
    }
    @Test void completionAndReportOverlapInBothCommitOrdersPreservingFacts() throws Exception {
        for(boolean completionFirst:List.of(true,false)) {
            LocalDate date=completionFirst ? yesterday : yesterday.minusDays(1);
            if(!completionFirst) schedule(street,operator,date);
            // Hold the first fact uncommitted while the other service writes and commits.
            try(var connection=source.getConnection()) {
                connection.setAutoCommit(false);
                String sql=completionFirst ?
                    "insert into collections(id,street_id,collection_date,completed_by,completed_at) values (?,?,?,?,now())" :
                    "insert into reports(id,street_id,collection_date,reporter_id,ward_id,report_type,note) values (?,?,?,?,?,'MISSED_PICKUP','Witness')";
                UUID firstId=UUID.randomUUID();
                try(var stmt=connection.prepareStatement(sql)) {
                    stmt.setObject(1,firstId); stmt.setObject(2,street); stmt.setObject(3,date);
                    stmt.setObject(4,completionFirst ? operator : resident);
                    if(!completionFirst) stmt.setObject(5,ward);
                    stmt.executeUpdate();
                }
                try(var pool=Executors.newSingleThreadExecutor()) {
                    var concurrent=pool.submit(()-> {
                        if(completionFirst) reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.MISSED_PICKUP,street,"Concurrent witness",null,null,date));
                        else collections.recordCollection(operator,street,date);
                    });
                    concurrent.get(30,TimeUnit.SECONDS);
                    connection.commit();
                }
                assertEquals(1,db.queryForObject("select count(*) from "+(completionFirst ? "collections" : "reports")+" where id=?",Integer.class,firstId));
            }
            var view=views(date,date).getFirst();
            assertEquals(CollectionStatus.DISPUTED,view.collectionStatus()); assertEquals(1,view.missedReportCount());
            var original=collections.recordCollection(operator,street,date);
            assertEquals(CollectionStatus.DISPUTED,original.collectionStatus());
            assertEquals(original,collections.recordCollection(operator,street,date));
        }
        assertEquals(2,authorities.getWardDashboard(authority,ward,yesterday.minusDays(1),today).disputedPickupCount());
    }}
