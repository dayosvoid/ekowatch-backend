package com.tesa.ekowatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.confirmation.api.ConfirmationApi;
import com.tesa.ekowatch.photo.api.PhotoUrlResolver;
import com.tesa.ekowatch.shared.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Import(ReportIdempotencyTest.Configuration.class)
class ReportIdempotencyTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17-alpine").withUrlParam("sslmode","disable");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl); r.add("spring.datasource.username",pg::getUsername); r.add("spring.datasource.password",pg::getPassword);
    }
    static final Instant T0=Instant.parse("2026-10-31T12:00:00.123456Z");
    static class TestClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(T0);
        public Instant instant() { return now.get(); }
        void set(Instant at) { now.set(at); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
    }
    static class TestUrls implements PhotoUrlResolver {
        volatile String url="https://example.invalid/original";
        volatile boolean fail;
        public String resolve(String ignored) {
            if(fail) throw new IllegalStateException("Synthetic projection failure");
            return url;
        }
    }
    static class TestMapper extends ObjectMapper {
        volatile boolean failResponse;
        TestMapper() { findAndRegisterModules(); }
        @Override public String writeValueAsString(Object value) throws JsonProcessingException {
            if(failResponse && value instanceof ReportView) throw new JsonMappingException(null,"Synthetic response persistence failure");
            return super.writeValueAsString(value);
        }
    }
    @TestConfiguration static class Configuration {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
        @Bean TestUrls testUrls() { return new TestUrls(); }
        @Bean @Primary TestMapper testMapper() { return new TestMapper(); }
    }
    @Autowired JdbcTemplate db;
    @Autowired javax.sql.DataSource source;
    @Autowired ReportApi reports;
    @Autowired ConfirmationApi confirmations;
    @Autowired TestClock clock;
    @Autowired TestUrls urls;
    @Autowired TestMapper json;
    @Autowired PlatformTransactionManager transactions;
    UUID resident,other,operator,ward,street,secondStreet;
    UUID user(String role) {
        UUID id=UUID.randomUUID();
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'disabled','Fictional',?)",id,id+"@example.invalid",role);
        return id;
    }
    @BeforeEach void fixture() {
        clock.set(T0); urls.fail=false; urls.url="https://example.invalid/original"; json.failResponse=false;
        resident=user("RESIDENT"); other=user("RESIDENT"); operator=user("OPERATOR");
        ward=UUID.randomUUID(); street=UUID.randomUUID(); secondStreet=UUID.randomUUID();
        db.update("insert into wards(id,name) values (?,?)",ward,"Fictional "+ward);
        db.update("insert into streets(id,ward_id,name,latitude,longitude) values (?,?,'First',6.5,3.4),(?,?,'Second',null,null)",street,ward,secondStreet,ward);
        db.update("insert into operator_wards values (?,?)",operator,ward);
        for(LocalDate date:List.of(LocalDate.of(2026,10,29),LocalDate.of(2026,10,30)))
            db.update("insert into collection_schedules(id,street_id,operator_id,collection_date) values (?,?,?,?)",UUID.randomUUID(),street,operator,date);
    }
    CreateReportRequest request() { return new CreateReportRequest(ReportType.FLOODED_ROAD,street,"Evidence",null,null,null); }
    UUID photo() {
        UUID id=UUID.randomUUID();
        db.update("insert into photos(id,owner_id,storage_key,content_type,size_bytes) values (?,?,'synthetic','image/jpeg',10)",id,resident); return id;
    }
    void violation(String code,org.junit.jupiter.api.function.Executable call) { assertEquals(code,assertThrows(RuleViolation.class,call).code()); }
    int reportCount() { return db.queryForObject("select count(*) from reports where ward_id=?",Integer.class,ward); }
    int recordCount() { return db.queryForObject("select count(*) from idempotency_records where user_id in (?,?)",Integer.class,resident,other); }
    Map<String,Object> record(UUID key) { return db.queryForMap("select * from idempotency_records where user_id=? and operation='createReport' and idempotency_key=?",resident,key); }
    List<ReportView> race(UUID key,CreateReportRequest one,CreateReportRequest two) throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<ReportView>> futures=new ArrayList<>();
            for(var request:List.of(one,two)) futures.add(pool.submit(()-> {
                ready.countDown(); assertTrue(start.await(10,TimeUnit.SECONDS)); return reports.createReport(resident,key,request);
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            return List.of(futures.get(0).get(30,TimeUnit.SECONDS),futures.get(1).get(30,TimeUnit.SECONDS));
        }
    }
    @Test void concurrentSameKeyCanonicalPayloadCreatesOneCompleteRecord() throws Exception {
        UUID key=UUID.randomUUID();
        var padded=new CreateReportRequest(ReportType.FLOODED_ROAD,street,"  Evidence  ",null,null,null);
        var results=race(key,request(),padded);
        assertEquals(results.getFirst(),results.getLast()); assertEquals("Evidence",results.getFirst().note());
        assertEquals(1,reportCount()); assertEquals(1,recordCount());
        var saved=record(key);
        assertEquals(results.getFirst().reportId(),saved.get("report_id"));
        assertEquals(T0,((Timestamp)saved.get("created_at")).toInstant());
        assertEquals(T0.plus(Duration.ofHours(24)),((Timestamp)saved.get("expires_at")).toInstant());
        assertEquals(64,((String)saved.get("request_hash")).length());
        assertEquals(results.getFirst(),json.readValue(saved.get("response_payload").toString(),ReportView.class));
        assertFalse(saved.get("response_payload").toString().contains("reporterId"));
    }
    @Test void lostResponseRetryReturnsOriginalSnapshotDespiteLaterEvidenceStreetPhotoAndFreshness() {
        UUID key=UUID.randomUUID();
        var request=new CreateReportRequest(ReportType.FLOODED_ROAD,street,"Evidence",photo(),new Location(7.2,4.3),null);
        var original=reports.createReport(resident,key,request);
        confirmations.confirmReport(other,original.reportId());
        db.update("update streets set name='Renamed',latitude=8,longitude=5 where id=?",street);
        urls.url="https://example.invalid/changed";
        clock.set(T0.plus(Duration.ofHours(7)));
        var current=reports.getReport(resident,original.reportId());
        assertEquals(1,current.confirmationCount()); assertEquals(FloodStatus.EXPIRED,current.floodStatus());
        assertNotEquals(original,current);
        urls.fail=true; // Replay must not call the adapter or current-view query.
        assertEquals(original,reports.createReport(resident,key,request));
        assertEquals(1,reportCount()); assertEquals(1,recordCount());
    }
    @Test void everyCanonicalFieldParticipatesAndSignedZeroNormalizes() {
        var base=new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Evidence",photo(),new Location(0.0,3.4),null);
        UUID key=UUID.randomUUID(); var original=reports.createReport(resident,key,base);
        assertEquals(original,reports.createReport(resident,key,new CreateReportRequest(base.reportType(),street," Evidence ",base.photoId(),new Location(-0.0,3.4),null)));
        for(var mismatch:List.of(
                new CreateReportRequest(ReportType.ILLEGAL_DUMPING,street,"Evidence",base.photoId(),base.location(),null),
                new CreateReportRequest(base.reportType(),secondStreet,"Evidence",base.photoId(),base.location(),null),
                new CreateReportRequest(base.reportType(),street,"Changed",base.photoId(),base.location(),null),
                new CreateReportRequest(base.reportType(),street,"Evidence",photo(),base.location(),null),
                new CreateReportRequest(base.reportType(),street,"Evidence",null,base.location(),null),
                new CreateReportRequest(base.reportType(),street,"Evidence",base.photoId(),new Location(0.1,3.4),null),
                new CreateReportRequest(base.reportType(),street,"Evidence",base.photoId(),new Location(0.0,3.5),null),
                new CreateReportRequest(base.reportType(),street,"Evidence",base.photoId(),null,null)))
            violation("IDEMPOTENCY_KEY_REUSED",()->reports.createReport(resident,key,mismatch));
        UUID datedKey=UUID.randomUUID();
        reports.createReport(resident,datedKey,new CreateReportRequest(ReportType.MISSED_PICKUP,street,"Missed",null,null,LocalDate.of(2026,10,29)));
        violation("IDEMPOTENCY_KEY_REUSED",()->reports.createReport(resident,datedKey,new CreateReportRequest(ReportType.MISSED_PICKUP,street,"Missed",null,null,LocalDate.of(2026,10,30))));
        assertEquals(2,reportCount()); assertEquals(2,recordCount());
    }
    @Test void concurrentMismatchCreatesExactlyOneIntentAndDifferentUsersAreIndependent() throws Exception {
        UUID key=UUID.randomUUID();
        var changed=new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Different",null,null,null);
        CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<String>> futures=new ArrayList<>();
            for(var payload:List.of(request(),changed)) futures.add(pool.submit(()-> {
                assertTrue(start.await(10,TimeUnit.SECONDS));
                try { reports.createReport(resident,key,payload); return "OK"; }
                catch(RuleViolation e) { return e.code(); }
            }));
            start.countDown();
            assertEquals(List.of("IDEMPOTENCY_KEY_REUSED","OK"),List.of(futures.getFirst().get(30,TimeUnit.SECONDS),futures.getLast().get(30,TimeUnit.SECONDS)).stream().sorted().toList());
        }
        assertEquals(1,reportCount()); assertEquals(1,recordCount());
        var another=reports.createReport(other,key,request());
        assertNotEquals(record(key).get("report_id"),another.reportId());
        assertEquals(2,reportCount()); assertEquals(2,recordCount());
    }
    @Test void expiryBoundaryReusesAtomicallyWithoutDeletingHistoricalReports() throws Exception {
        UUID key=UUID.randomUUID(); var original=reports.createReport(resident,key,request()); var stored=record(key);
        clock.set(T0.plus(Duration.ofHours(24)).minusNanos(1000));
        assertEquals(original,reports.createReport(resident,key,request()));
        var changed=new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"New intent",null,null,null);
        violation("IDEMPOTENCY_KEY_REUSED",()->reports.createReport(resident,key,changed));
        clock.set(T0.plus(Duration.ofHours(24)));
        var renewed=race(key,changed,changed);
        assertEquals(renewed.getFirst(),renewed.getLast()); assertNotEquals(original.reportId(),renewed.getFirst().reportId());
        assertEquals(2,reportCount()); assertEquals(1,recordCount());
        assertEquals(stored.get("id"),record(key).get("id"));
        assertEquals(T0.plus(Duration.ofHours(48)),((Timestamp)record(key).get("expires_at")).toInstant());
        assertEquals(original.reportId(),reports.getReport(resident,original.reportId()).reportId());
        violation("IDEMPOTENCY_KEY_REUSED",()->reports.createReport(resident,key,request()));
    }
    @Test void waitingRetrySamplesExpiryAfterAcquiringTheIntentLock() throws Exception {
        UUID key=UUID.randomUUID(); var original=reports.createReport(resident,key,request());
        clock.set(T0.plus(Duration.ofHours(23)));
        try(var connection=source.getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))")) {
                statement.setString(1,resident+":createReport:"+key); statement.execute();
            }
            try(var pool=Executors.newSingleThreadExecutor()) {
                Future<ReportView> future=pool.submit(()->reports.createReport(resident,key,request()));
                try {
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                    boolean waiting=false;
                    while(System.nanoTime()<deadline) {
                        waiting=Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from pg_locks where locktype='advisory' and not granted)",Boolean.class));
                        if(waiting) break;
                        Thread.sleep(10);
                    }
                    assertTrue(waiting,"Retry must wait on the intent lock before expiry advances");
                    clock.set(T0.plus(Duration.ofHours(24)));
                } finally {
                    connection.rollback(); // Release the fixture lock before executor shutdown even on assertion failure.
                }
                var renewed=future.get(30,TimeUnit.SECONDS);
                assertNotEquals(original.reportId(),renewed.reportId());
                assertEquals(clock.instant(),renewed.createdAt());
            }
        }
        assertEquals(2,reportCount()); assertEquals(1,recordCount());
    }
    @Test void responseAndTransactionFailuresLeaveNeitherPartialReportNorRecordAndAllowRetry() {
        UUID key=UUID.randomUUID(); var request=new CreateReportRequest(ReportType.FLOODED_ROAD,street,"Evidence",photo(),null,null);
        urls.fail=true;
        assertThrows(IllegalStateException.class,()->reports.createReport(resident,key,request));
        assertEquals(0,reportCount()); assertEquals(0,recordCount());
        urls.fail=false; json.failResponse=true;
        assertThrows(IllegalStateException.class,()->reports.createReport(resident,key,request));
        assertEquals(0,reportCount()); assertEquals(0,recordCount());
        json.failResponse=false;
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            reports.createReport(resident,key,request); assertEquals(1,recordCount()); throw new IllegalStateException("Synthetic outer rollback");
        }));
        assertEquals(0,reportCount()); assertEquals(0,recordCount());
        assertNotNull(reports.createReport(resident,key,request)); assertEquals(1,reportCount()); assertEquals(1,recordCount());
        var old=record(key); clock.set(T0.plus(Duration.ofHours(24))); json.failResponse=true;
        assertThrows(IllegalStateException.class,()->reports.createReport(resident,key,request));
        assertEquals(old,record(key)); assertEquals(1,reportCount());
    }
    @Test void missingKeysInvalidValuesAndWrongRolesCannotWrite() {
        violation("IDEMPOTENCY_KEY_REQUIRED",()->reports.createReport(resident,null,request()));
        for(UUID actor:List.of(operator,UUID.randomUUID())) violation("FORBIDDEN",()->reports.createReport(actor,UUID.randomUUID(),request()));
        violation("VALIDATION_FAILED",()->reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.FLOODED_ROAD,street,"   ",null,null,null)));
        assertEquals(0,reportCount()); assertEquals(0,recordCount());
        assertEquals(1,Arrays.stream(ReportApi.class.getMethods()).filter(m->m.getName().equals("createReport")).count());
        assertArrayEquals(new Class<?>[]{UUID.class,UUID.class,CreateReportRequest.class},Arrays.stream(ReportApi.class.getMethods()).filter(m->m.getName().equals("createReport")).findFirst().orElseThrow().getParameterTypes());
    }
}
