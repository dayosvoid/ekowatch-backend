package com.tesa.ekowatch;

import com.tesa.ekowatch.shared.DemoSeed;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.collection.api.*;
import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.authority.api.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.profiles.active=local-demo","ekowatch.demo.enabled=true"})
@Testcontainers
@Import(ResidentReportApiTest.Configuration.class)
@Transactional
class DemoSeedTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17-alpine").withUrlParam("sslmode","disable");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl); r.add("spring.datasource.username",pg::getUsername); r.add("spring.datasource.password",pg::getPassword);
    }
    static final Instant PRESENTATION=Instant.parse("2026-10-31T12:00:00Z");
    static final LocalDate FROM=LocalDate.of(2026,10,16), TO=LocalDate.of(2026,11,14);
    @Autowired JdbcTemplate db;
    @Autowired DemoSeed seed;
    @Autowired ReportApi reports;
    @Autowired CollectionApi collections;
    @Autowired AuthorityApi authorities;
    @Autowired ResidentReportApiTest.TestClock clock;
    UUID user(int n) { return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(n)); }
    UUID street(int n) { return UUID.fromString("20000000-0000-0000-0000-%012d".formatted(n)); }
    UUID report(int n) { return UUID.fromString("40000000-0000-0000-0000-%012d".formatted(n)); }
    final UUID ward=UUID.fromString("10000000-0000-0000-0000-000000000001");
    @BeforeEach void clock() { clock.set(PRESENTATION); }
    @AfterEach void resetClock() { clock.set(PRESENTATION); }
    int count(String table) { return db.queryForObject("select count(*) from "+table,Integer.class); }
    Map<String,Set<Map<String,Object>>> snapshot() {
        var snapshot=new TreeMap<String,Set<Map<String,Object>>>();
        for(String table:List.of("users","wards","streets","operator_wards","collection_schedules","collections","reports","report_confirmations","photos","idempotency_records"))
            snapshot.put(table,new HashSet<>(db.queryForList("select * from "+table)));
        return snapshot;
    }
    @Test void volumesPatternsPreferencesAndFixedIdentitiesAreComplete() {
        assertEquals(1,count("wards")); assertEquals(8,count("streets")); assertEquals(8,count("users"));
        assertEquals(4,db.queryForObject("select count(*) from users where role='RESIDENT'",Integer.class));
        assertEquals(3,db.queryForObject("select count(*) from users where role='OPERATOR'",Integer.class));
        assertEquals(1,db.queryForObject("select count(*) from users where role='AUTHORITY'",Integer.class));
        assertEquals(8,db.queryForObject("select count(*) from users where password_hash='!DEMO_LOGIN_DISABLED' and display_name like 'Fictional %'",Integer.class));
        assertEquals(4,db.queryForObject("select count(*) from users u join streets s on s.id=u.street_id and s.ward_id=u.ward_id where u.role='RESIDENT'",Integer.class));
        assertEquals(List.of(user(4),user(7)),db.queryForList("select operator_id from operator_wards order by operator_id",UUID.class));
        assertEquals(172,count("collection_schedules"));
        assertEquals(30,db.queryForObject("select count(distinct collection_date) from collection_schedules",Integer.class));
        assertEquals(FROM,db.queryForObject("select min(collection_date) from collection_schedules",LocalDate.class));
        assertEquals(TO,db.queryForObject("select max(collection_date) from collection_schedules",LocalDate.class));
        for(int n=1;n<=8;n++) {
            Set<DayOfWeek> days=Set.of(1,4,7,8).contains(n) ? EnumSet.allOf(DayOfWeek.class) :
                    Set.of(2,5).contains(n) ? EnumSet.of(DayOfWeek.MONDAY,DayOfWeek.WEDNESDAY,DayOfWeek.FRIDAY) :
                    EnumSet.of(DayOfWeek.TUESDAY,DayOfWeek.THURSDAY,DayOfWeek.SATURDAY);
            var expected=FROM.datesUntil(TO.plusDays(1)).filter(d->days.contains(d.getDayOfWeek())).toList();
            assertEquals(expected,db.queryForList("select collection_date from collection_schedules where street_id=? order by collection_date",LocalDate.class,street(n)));
            assertEquals(List.of(user(n<=4 ? 4 : 7)),db.queryForList("select distinct operator_id from collection_schedules where street_id=?",UUID.class,street(n)));
        }
        assertTrue(db.queryForObject("select length(name) from streets where id=?",Integer.class,street(7))>100);
        assertEquals(0,db.queryForObject("select count(*) from reports where street_id=?",Integer.class,street(8)));
        assertEquals(0,count("photos")); assertEquals(0,count("idempotency_records"));
        assertEquals(7,db.queryForObject("select count(*) from collection_schedules where id::text like '30000000-%'",Integer.class));
        assertEquals(Instant.parse("2026-10-15T08:00:00Z"),db.queryForObject("select created_at from users where id=?",java.sql.Timestamp.class,user(1)).toInstant());
    }
    @Test void realRecordsDeriveEveryRequiredPresentationCase() {
        assertEquals(9,count("reports")); assertEquals(5,count("report_confirmations")); assertEquals(3,count("collections"));
        assertEquals(EnumSet.allOf(ReportType.class),new HashSet<>(db.queryForList("select distinct report_type from reports",String.class).stream().map(ReportType::valueOf).toList()));
        assertEquals(List.of(0L,1L,2L),List.of(1,2,3).stream().map(n->reports.getReport(user(2),report(n)).confirmationCount()).toList());
        assertEquals(FloodStatus.CURRENT,reports.getReport(user(1),report(4)).floodStatus());
        assertEquals(FloodStatus.EXPIRED,reports.getReport(user(1),report(5)).floodStatus());
        var renewed=reports.getReport(user(1),report(9));
        assertEquals(2,renewed.confirmationCount()); assertEquals(FloodStatus.CURRENT,renewed.floodStatus());
        assertEquals(Instant.parse("2026-10-31T11:00:00Z"),renewed.lastEvidenceAt());
        var denied=reports.getReport(user(1),report(8));
        assertNull(denied.photoUrl());
        assertTrue(db.queryForList("select latitude,longitude,photo_id from reports where id=?",report(8)).getFirst().values().stream().allMatch(Objects::isNull));
        assertEquals("STREET",denied.locationPrecision());
        var views=collections.listCollectionSchedules(user(1),ward,FROM,TO);
        assertEquals(83,views.stream().filter(v->v.collectionStatus()==CollectionStatus.MISSED).count());
        assertEquals(1,views.stream().filter(v->v.collectionStatus()==CollectionStatus.DISPUTED).count());
        assertEquals(2,views.stream().filter(v->v.collectionStatus()==CollectionStatus.DONE).count());
        assertEquals(86,views.stream().filter(v->v.collectionStatus()==CollectionStatus.SCHEDULED).count());
        var disputed=views.stream().filter(v->v.collectionStatus()==CollectionStatus.DISPUTED).findFirst().orElseThrow();
        assertEquals(street(4),disputed.streetId()); assertEquals(LocalDate.of(2026,10,30),disputed.collectionDate()); assertEquals(1,disputed.missedReportCount());
        var d=authorities.getWardDashboard(user(5),ward,FROM,TO);
        assertEquals(83,d.missedPickupCount()); assertEquals(1,d.disputedPickupCount());
        assertEquals(3,d.blockedDrainCount()); assertEquals(2,d.currentFloodCount());
        assertEquals(Instant.parse("2026-10-31T10:00:00Z"),d.newestReportAt());
        assertEquals(2,d.operatorPerformance().size());
        for(var p:d.operatorPerformance()) {
            assertEquals(86,p.scheduledPickupCount());
            assertEquals(p.operatorId().equals(user(4)) ? 2 : 1,p.completedPickupCount());
        }
        assertEquals(5,reports.listWardHazards(user(1),ward).items().size());
        var before=snapshot(); clock.set(PRESENTATION.plus(Duration.ofHours(6)));
        assertEquals(0,authorities.getWardDashboard(user(5),ward,FROM,TO).currentFloodCount());
        assertEquals(3,reports.listWardHazards(user(1),ward).items().size()); assertEquals(before,snapshot());
        assertEquals("WARD_ASSIGNMENT_REQUIRED",assertThrows(RuleViolation.class,()->collections.recordCollection(user(8),street(1),LocalDate.of(2026,10,30))).code());
        assertEquals("FORBIDDEN",assertThrows(RuleViolation.class,()->authorities.getWardDashboard(user(1),ward,FROM,TO)).code());
    }
    @Test void rerunsPreserveEveryRowAndLaterUserActionsWithoutRefreshingEvidence() {
        var baseline=snapshot(); seed.run(new DefaultApplicationArguments()); assertEquals(baseline,snapshot());
        db.update("update users set street_id=?,password_hash='!FICTIONAL_PROVISIONING_TEST_SENTINEL' where id=?",street(8),user(1));
        db.update("update reports set note='Fictional resident edited evidence' where id=?",report(1));
        db.update("update report_confirmations set confirmed_at='2026-10-31T11:02:00Z' where id='50000000-0000-0000-0000-000000000004'");
        var completion=collections.recordCollection(user(4),street(1),LocalDate.of(2026,10,30));
        var submission=reports.createReport(user(2),UUID.randomUUID(),new CreateReportRequest(ReportType.ILLEGAL_DUMPING,street(8),"Fictional later user action",null,null,null));
        var changed=snapshot(); clock.set(PRESENTATION.plus(Duration.ofDays(10)));
        seed.run(new DefaultApplicationArguments()); seed.run(new DefaultApplicationArguments());
        assertEquals(changed,snapshot());
        assertEquals(completion.collectionId(),db.queryForObject("select id from collections where street_id=? and collection_date='2026-10-30'",UUID.class,street(1)));
        assertNotNull(reports.getReport(user(2),submission.reportId()));
        assertEquals(FloodStatus.EXPIRED,reports.getReport(user(1),report(4)).floodStatus());
    }
}
