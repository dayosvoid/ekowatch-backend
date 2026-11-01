package com.tesa.ekowatch;

import com.tesa.ekowatch.confirmation.api.ConfirmationApi;
import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.photo.api.PhotoUrlResolver;
import com.tesa.ekowatch.shared.RuleViolation;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="ekowatch.flood-freshness-hours=2")
@Testcontainers
@Import(ConfirmationFreshnessTest.Configuration.class)
class ConfirmationFreshnessTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17-alpine").withUrlParam("sslmode","disable");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl); r.add("spring.datasource.username",pg::getUsername); r.add("spring.datasource.password",pg::getPassword);
    }
    static final Instant T0=Instant.parse("2026-10-31T10:00:00Z");
    static final Duration FRESHNESS=Duration.ofHours(2);
    static class EntryClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(T0);
        final AtomicReference<CountDownLatch> signal=new AtomicReference<>();
        void set(Instant value) { now.set(value); }
        public Instant instant() {
            Instant sampled=now.get(); CountDownLatch latch=signal.getAndSet(null);
            if(latch!=null) latch.countDown(); return sampled;
        }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now.get(),zone); }
    }
    static class TestUrls implements PhotoUrlResolver {
        final AtomicInteger reads=new AtomicInteger();
        volatile int failAt=Integer.MAX_VALUE;
        void reset(int at) { reads.set(0); failAt=at; }
        public String resolve(String ignored) {
            if(reads.incrementAndGet()==failAt) throw new IllegalStateException("Synthetic adapter failure");
            return null;
        }
    }
    @TestConfiguration static class Configuration {
        @Bean @Primary EntryClock entryClock() { return new EntryClock(); }
        @Bean TestUrls testUrls() { return new TestUrls(); }
    }
    @Autowired ConfirmationApi confirmations;
    @Autowired ReportApi reports;
    @Autowired JdbcTemplate db;
    @Autowired DataSource source;
    @Autowired EntryClock clock;
    @Autowired TestUrls urls;
    UUID reporter,a,b,authority,operator,ward,street;
    UUID user(String role) {
        UUID id=UUID.randomUUID();
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'disabled','Fictional',?)",id,id+"@example.invalid",role); return id;
    }
    @BeforeEach void fixture() {
        clock.set(T0); clock.signal.set(null); urls.reset(Integer.MAX_VALUE);
        reporter=user("RESIDENT"); a=user("RESIDENT"); b=user("RESIDENT"); authority=user("AUTHORITY"); operator=user("OPERATOR");
        ward=UUID.randomUUID(); street=UUID.randomUUID();
        db.update("insert into wards(id,name) values (?,?)",ward,"Fictional "+ward);
        db.update("insert into streets(id,ward_id,name) values (?,?,'Fictional street')",street,ward);
    }
    ReportView create(ReportType type) {
        return reports.createReport(reporter,UUID.randomUUID(),new CreateReportRequest(type,street,"Fictional evidence",null,null,
                type==ReportType.MISSED_PICKUP ? LocalDate.of(2026,10,30) : null));
    }
    Map<String,Object> row(UUID report,UUID actor) {
        return db.queryForMap("select * from report_confirmations where report_id=? and resident_id=?",report,actor);
    }
    void violation(String code,org.junit.jupiter.api.function.Executable call) { assertEquals(code,assertThrows(RuleViolation.class,call).code()); }
    List<ReportView> race(UUID report,UUID one,UUID two) throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<ReportView>> futures=new ArrayList<>();
            for(UUID actor:List.of(one,two)) futures.add(pool.submit(()-> {
                ready.countDown(); assertTrue(start.await(10,TimeUnit.SECONDS)); return confirmations.confirmReport(actor,report);
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            return List.of(futures.get(0).get(30,TimeUnit.SECONDS),futures.get(1).get(30,TimeUnit.SECONDS));
        }
    }

    @Test void firstConfirmationCommitsBeforeReturnAndImmediateRepeatIsANoop() throws Exception {
        var report=create(ReportType.FLOODED_ROAD);
        var storedReport=db.queryForMap("select * from reports where id=?",report.reportId());
        var first=confirmations.confirmReport(a,report.reportId());
        assertEquals(1,first.confirmationCount()); assertTrue(first.confirmedByMe());
        assertEquals(T0,first.lastEvidenceAt()); assertEquals(T0.plus(FRESHNESS),first.expiresAt());
        try(var connection=source.getConnection(); var statement=connection.prepareStatement("select count(*) from report_confirmations where report_id=? and resident_id=?")) {
            assertTrue(connection.getAutoCommit());
            statement.setObject(1,report.reportId()); statement.setObject(2,a);
            try(var result=statement.executeQuery()) { assertTrue(result.next()); assertEquals(1,result.getInt(1)); }
        }
        var original=row(report.reportId(),a);
        clock.set(T0.plusSeconds(1800));
        assertEquals(first,confirmations.confirmReport(a,report.reportId()));
        assertEquals(original,row(report.reportId(),a));
        assertEquals(storedReport,db.queryForMap("select * from reports where id=?",report.reportId()));
    }

    @Test void distinctResidentsCountTwiceAndSameResidentRacesCountOnce() throws Exception {
        var different=create(ReportType.FLOODED_ROAD);
        var views=race(different.reportId(),a,b);
        assertEquals(List.of(1L,2L),views.stream().map(ReportView::confirmationCount).sorted().toList());
        assertTrue(views.stream().allMatch(ReportView::confirmedByMe));
        assertEquals(2,reports.getReport(authority,different.reportId()).confirmationCount());
        var same=create(ReportType.FLOODED_ROAD);
        var repeated=race(same.reportId(),a,a);
        assertEquals(repeated.getFirst(),repeated.getLast());
        assertEquals(1,repeated.getFirst().confirmationCount());
        assertEquals(1,db.queryForObject("select count(*) from report_confirmations where report_id=?",Integer.class,same.reportId()));
    }

    @Test void expiredFloodReconfirmationPreservesIdentityAndPeopleAndLaterRenewalIsFreshTestimony() {
        var report=create(ReportType.FLOODED_ROAD);
        confirmations.confirmReport(a,report.reportId());
        var originalA=row(report.reportId(),a);
        clock.set(T0.plusSeconds(3600)); confirmations.confirmReport(b,report.reportId());
        var originalB=row(report.reportId(),b);
        Instant expiry=T0.plusSeconds(3600).plus(FRESHNESS);
        clock.set(expiry);
        var renewed=confirmations.confirmReport(a,report.reportId());
        assertEquals(2,renewed.confirmationCount()); assertTrue(renewed.confirmedByMe());
        assertEquals(FloodStatus.CURRENT,renewed.floodStatus()); assertEquals(expiry,renewed.lastEvidenceAt());
        assertEquals(expiry.plus(FRESHNESS),renewed.expiresAt());
        assertEquals(originalA.get("id"),row(report.reportId(),a).get("id"));
        assertEquals(originalB,row(report.reportId(),b));
        var afterRenewal=row(report.reportId(),a);
        clock.set(expiry.plusSeconds(30));
        assertEquals(renewed,confirmations.confirmReport(a,report.reportId()));
        assertEquals(afterRenewal,row(report.reportId(),a));
        clock.set(renewed.expiresAt());
        var later=confirmations.confirmReport(a,report.reportId());
        assertEquals(2,later.confirmationCount()); assertEquals(renewed.expiresAt(),later.lastEvidenceAt());
        assertEquals(originalA.get("id"),row(report.reportId(),a).get("id"));
        assertEquals(originalB,row(report.reportId(),b));
    }

    @Test void firstEvidenceFromANewResidentAfterExpiryInsertsRatherThanRecountsAnExistingPerson() {
        var report=create(ReportType.FLOODED_ROAD);
        confirmations.confirmReport(a,report.reportId());
        var old=row(report.reportId(),a);
        clock.set(T0.plus(FRESHNESS).plusSeconds(1));
        var fresh=confirmations.confirmReport(b,report.reportId());
        assertEquals(2,fresh.confirmationCount()); assertEquals(FloodStatus.CURRENT,fresh.floodStatus());
        assertEquals(old,row(report.reportId(),a));
        assertEquals(Timestamp.from(T0.plus(FRESHNESS).plusSeconds(1)),row(report.reportId(),b).get("confirmed_at"));
    }

    @Test void everyNonFloodRepeatAlwaysPreservesItsTimestamp() {
        db.update("insert into operator_wards values (?,?)",operator,ward);
        db.update("insert into collection_schedules(id,street_id,operator_id,collection_date) values (?,?,?,'2026-10-30')",UUID.randomUUID(),street,operator);
        for(ReportType type:List.of(ReportType.BLOCKED_DRAIN,ReportType.ILLEGAL_DUMPING,ReportType.MISSED_PICKUP)) {
            clock.set(T0);
            var report=create(type); var first=confirmations.confirmReport(a,report.reportId());
            var original=row(report.reportId(),a);
            clock.set(T0.plus(Duration.ofDays(10)));
            var repeated=confirmations.confirmReport(a,report.reportId());
            assertEquals(first,repeated); assertEquals(original,row(report.reportId(),a));
            assertNull(repeated.floodStatus()); assertNull(repeated.expiresAt());
        }
    }

    @Test void configuredBoundaryAndOneHourOutageReadsMatchFiltersAndNeverWrite() {
        var report=create(ReportType.FLOODED_ROAD);
        confirmations.confirmReport(a,report.reportId());
        var reportBefore=db.queryForMap("select * from reports where id=?",report.reportId());
        var evidenceBefore=row(report.reportId(),a);
        clock.set(T0.plus(FRESHNESS).minusNanos(1));
        assertEquals(FloodStatus.CURRENT,reports.getReport(b,report.reportId()).floodStatus());
        assertEquals(1,reports.listReports(b,ward,null,ReportType.FLOODED_ROAD,FloodStatus.CURRENT,0,20,null).totalElements());
        clock.set(T0.plus(FRESHNESS));
        assertEquals(FloodStatus.EXPIRED,reports.getReport(b,report.reportId()).floodStatus());
        assertEquals(0,reports.listReports(b,ward,null,ReportType.FLOODED_ROAD,FloodStatus.CURRENT,0,20,null).totalElements());
        assertEquals(1,reports.listReports(b,ward,null,ReportType.FLOODED_ROAD,FloodStatus.EXPIRED,0,20,null).totalElements());
        clock.set(T0.plus(FRESHNESS).plusSeconds(3600));
        assertEquals(FloodStatus.EXPIRED,reports.getReport(b,report.reportId()).floodStatus());
        assertEquals(evidenceBefore,row(report.reportId(),a));
        assertEquals(reportBefore,db.queryForMap("select * from reports where id=?",report.reportId()));
    }

    @Test void waitingAcrossExpiryDoesNotTurnACurrentAtEntryRepeatIntoNewEvidence() throws Exception {
        var report=create(ReportType.FLOODED_ROAD); confirmations.confirmReport(a,report.reportId());
        var original=row(report.reportId(),a);
        try(var pool=Executors.newSingleThreadExecutor();var connection=source.getConnection();var lock=connection.prepareStatement("select id from reports where id=? for update")) {
            connection.setAutoCommit(false); lock.setObject(1,report.reportId()); lock.executeQuery().close();
            clock.set(T0.plus(FRESHNESS).minusSeconds(1));
            CountDownLatch captured=new CountDownLatch(1); clock.signal.set(captured);
            var pending=pool.submit(()->confirmations.confirmReport(a,report.reportId()));
            assertTrue(captured.await(10,TimeUnit.SECONDS)); assertFalse(pending.isDone());
            clock.set(T0.plus(FRESHNESS).plusSeconds(60)); connection.commit();
            assertEquals(FloodStatus.EXPIRED,pending.get(30,TimeUnit.SECONDS).floodStatus());
        }
        assertEquals(original,row(report.reportId(),a));
    }

    @Test void expiredConcurrentRepeatsWithDifferentEntryTimesRenewExactlyOnce() throws Exception {
        var report=create(ReportType.FLOODED_ROAD); confirmations.confirmReport(a,report.reportId());
        var original=row(report.reportId(),a);
        try(var pool=Executors.newFixedThreadPool(2);var connection=source.getConnection();var lock=connection.prepareStatement("select id from reports where id=? for update")) {
            connection.setAutoCommit(false); lock.setObject(1,report.reportId()); lock.executeQuery().close();
            Instant firstAt=T0.plus(FRESHNESS),secondAt=firstAt.plusSeconds(60);
            clock.set(firstAt); CountDownLatch firstCaptured=new CountDownLatch(1); clock.signal.set(firstCaptured);
            var first=pool.submit(()->confirmations.confirmReport(a,report.reportId())); assertTrue(firstCaptured.await(10,TimeUnit.SECONDS));
            clock.set(secondAt); CountDownLatch secondCaptured=new CountDownLatch(1); clock.signal.set(secondCaptured);
            var second=pool.submit(()->confirmations.confirmReport(a,report.reportId())); assertTrue(secondCaptured.await(10,TimeUnit.SECONDS));
            connection.commit();
            var firstView=first.get(30,TimeUnit.SECONDS); var secondView=second.get(30,TimeUnit.SECONDS);
            assertEquals(firstView,secondView); assertEquals(1,firstView.confirmationCount());
            assertTrue(Set.of(firstAt,secondAt).contains(firstView.lastEvidenceAt()));
        }
        assertEquals(original.get("id"),row(report.reportId(),a).get("id"));
    }

    @Test void roleSelfMissingAndNullFailuresLeaveNoEvidence() {
        var report=create(ReportType.FLOODED_ROAD);
        for(UUID actor:List.of(authority,operator,UUID.randomUUID())) violation("FORBIDDEN",()->confirmations.confirmReport(actor,report.reportId()));
        violation("FORBIDDEN",()->confirmations.confirmReport(null,report.reportId()));
        violation("SELF_CONFIRMATION_NOT_ALLOWED",()->confirmations.confirmReport(reporter,report.reportId()));
        violation("RESOURCE_NOT_FOUND",()->confirmations.confirmReport(a,UUID.randomUUID()));
        violation("VALIDATION_FAILED",()->confirmations.confirmReport(a,null));
        assertEquals(0,db.queryForObject("select count(*) from report_confirmations where report_id=?",Integer.class,report.reportId()));
    }

    @Test void responseFailureRollsBackEvidenceBeforeReturningToCaller() {
        UUID photo=UUID.randomUUID();
        db.update("insert into photos(id,owner_id,storage_key,content_type,size_bytes) values (?,?,'synthetic-test-reference','image/jpeg',10)",photo,reporter);
        var report=reports.createReport(reporter,UUID.randomUUID(),new CreateReportRequest(ReportType.FLOODED_ROAD,street,"Fictional",photo,null,null));
        urls.reset(2); // Lock snapshot resolves once; response projection after insertion resolves a second time.
        assertThrows(IllegalStateException.class,()->confirmations.confirmReport(a,report.reportId()));
        assertEquals(0,db.queryForObject("select count(*) from report_confirmations where report_id=?",Integer.class,report.reportId()));
        urls.reset(Integer.MAX_VALUE);
        assertEquals(1,confirmations.confirmReport(a,report.reportId()).confirmationCount());
    }
}