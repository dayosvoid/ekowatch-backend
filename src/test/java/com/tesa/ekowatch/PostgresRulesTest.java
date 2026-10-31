package com.tesa.ekowatch;

import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.confirmation.api.*;
import com.tesa.ekowatch.collection.api.*;
import com.tesa.ekowatch.shared.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class PostgresRulesTest {
    @Container
    // Disposable PostgreSQL uses plaintext; do not negotiate SSL through Docker's port mapping.
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17-alpine")
            .withUrlParam("sslmode", "disable");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired
    JdbcTemplate db;
    @Autowired
    ReportApi reports;
    @Autowired
    ConfirmationApi confirmations;
    @Autowired
    CollectionApi collections;
    @Autowired
    DataSource source;
    @Autowired com.tesa.ekowatch.authority.api.AuthorityApi authorities;
    UUID reporter, a, b, authority, operator, ward, street, report;

    UUID user(String role) {
        UUID id = UUID.randomUUID();
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?, 'disabled','Test',?)", id, id + "@example.invalid", role);
        return id;
    }

    @BeforeEach
    void setup() {
        db.execute("truncate report_confirmations,reports,collections,collection_schedules,legacy_pickups,legacy_collection_schedules,operator_wards,streets,wards,users cascade");
        reporter = user("RESIDENT");
        a = user("RESIDENT");
        b = user("RESIDENT");
        authority = user("AUTHORITY");
        operator = user("OPERATOR");
        ward = UUID.randomUUID();
        street = UUID.randomUUID();
        db.update("insert into wards(id,name) values (?,?)", ward, "Ward " + ward);
        db.update("insert into streets(id,ward_id,name) values (?,?,?)", street, ward, "Test street");
        report = reports.createReport(reporter, new CreateReportRequest(ReportType.BLOCKED_DRAIN, street, "Blocked drain", null, null, null)).reportId();
    }

    List<String> race(UUID first, UUID second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<String>> futures = new ArrayList<>();
            for (UUID actor : List.of(first, second))
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    try {
                        confirmations.confirmReport(actor, report);
                        return "OK";
                    } catch (RuleViolation e) {
                        return e.code();
                    }
                }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void differentResidentsConcurrently() throws Exception {
        assertEquals(List.of("OK", "OK"), race(a, b));
        assertEquals(2, reports.getReport(authority, report).confirmationCount());
    }

    @Test
    void sameResidentConcurrently() throws Exception {
        var results = race(a, a);
        assertEquals(List.of("OK", "OK"), results);
        assertEquals(1, reports.getReport(authority, report).confirmationCount());
        try (var c = source.getConnection(); var s = c.prepareStatement("insert into report_confirmations(id,report_id,resident_id) values (?,?,?)")) {
            s.setObject(1, UUID.randomUUID());
            s.setObject(2, report);
            s.setObject(3, a);
            var e = assertThrows(java.sql.SQLException.class, s::executeUpdate);
            assertEquals("23505", e.getSQLState());
            System.out.println("Same-resident unique constraint SQLSTATE=" + e.getSQLState());
        }
    }

    @Test
    void isolatedConstraintEvidence() throws Exception {
        // DDL is confined to a temporary table in this disposable Testcontainers database.
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.execute("create temporary table constraint_evidence (like report_confirmations including all)");
            // Renamed source indexes need not produce a predictable cloned constraint name.
            String uniqueName;
            try (var constraints = s.executeQuery("select conname,pg_get_constraintdef(oid) from pg_constraint where conrelid='pg_temp.constraint_evidence'::regclass and contype='u'")) {
                assertTrue(constraints.next());
                uniqueName = constraints.getString(1);
                assertEquals("UNIQUE (report_id, resident_id)", constraints.getString(2));
                assertFalse(constraints.next());
            }
            s.execute("alter table pg_temp.constraint_evidence drop constraint \"" + uniqueName.replace("\"", "\"\"") + "\"");
            String values = "'" + report + "','" + a + "'";
            s.executeUpdate("insert into constraint_evidence(id,report_id,resident_id) values ('" + UUID.randomUUID() + "'," + values + "),('" + UUID.randomUUID() + "'," + values + ")");
            try (var r = s.executeQuery("select count(*) from constraint_evidence")) {
                r.next();
                assertEquals(2, r.getInt(1));
            }
            s.execute("truncate constraint_evidence");
            s.execute("alter table constraint_evidence add constraint evidence_unique unique(report_id,resident_id)");
            s.executeUpdate("insert into constraint_evidence(id,report_id,resident_id) values ('" + UUID.randomUUID() + "'," + values + ")");
            var e = assertThrows(java.sql.SQLException.class, () -> s.executeUpdate("insert into constraint_evidence(id,report_id,resident_id) values ('" + UUID.randomUUID() + "'," + values + ")"));
            assertEquals("23505", e.getSQLState());
            System.out.println("Restored constraint SQLSTATE=" + e.getSQLState());
        }
    }

    void violation(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(RuleViolation.class, action).code());
    }

    @Test
    void eligibilityAndAuthorityRules() {
        violation("SELF_CONFIRMATION_NOT_ALLOWED", () -> confirmations.confirmReport(reporter, report));
        violation("FORBIDDEN", () -> confirmations.confirmReport(authority, report));
        violation("RESOURCE_NOT_FOUND", () -> confirmations.confirmReport(a, UUID.randomUUID()));
        db.update("update reports set status='RESOLVED' where id=?", report);
        // Legacy lifecycle history is retained, not an MVP confirmation workflow.
        var historyBefore = db.queryForMap("select status,updated_at from reports where id=?", report);
        assertEquals(1, confirmations.confirmReport(a, report).confirmationCount());
        assertEquals(historyBefore, db.queryForMap("select status,updated_at from reports where id=?", report));
        violation("FORBIDDEN", () -> authorities.getWardDashboard(a, ward, LocalDate.now(), LocalDate.now()));
        assertEquals(1, authorities.getWardDashboard(authority, ward, LocalDate.now(), LocalDate.now()).blockedDrainCount());
        violation("VALIDATION_FAILED", () -> reports.createReport(a, new CreateReportRequest(ReportType.BLOCKED_DRAIN, street, " ", null, null, null)));
        violation("RESOURCE_NOT_FOUND", () -> reports.createReport(a, new CreateReportRequest(ReportType.BLOCKED_DRAIN, UUID.randomUUID(), "Drain", null, null, null)));
    }

    @Test
    void reportNotesAreTrimmedAndLimitedThroughCanonicalRequest() {
        var view = reports.createReport(a, new CreateReportRequest(ReportType.BLOCKED_DRAIN, street, "  " + "x".repeat(1000) + "  ", null, null, null));
        assertEquals("x".repeat(1000), view.note());
        assertEquals("x".repeat(1000), db.queryForObject("select note from reports where id=?", String.class, view.reportId()));
        violation("VALIDATION_FAILED", () -> reports.createReport(a, new CreateReportRequest(ReportType.BLOCKED_DRAIN, street, "x".repeat(1001), null, null, null)));
        violation("VALIDATION_FAILED", () -> reports.createReport(a, new CreateReportRequest(ReportType.BLOCKED_DRAIN, street, null, null, null, null)));
        violation("VALIDATION_FAILED", () -> reports.createReport(a, new CreateReportRequest(ReportType.BLOCKED_DRAIN, street, "\t\n ", null, null, null)));
        String unicodeNote = "\uD83C\uDF0D".repeat(1000);
        assertEquals(unicodeNote, reports.createReport(a, new CreateReportRequest(ReportType.BLOCKED_DRAIN, street, unicodeNote, null, null, null)).note());
    }

    @Test
    void missingPhotoUrlAdapterNeverExposesStorageKey() {
        UUID photoId = UUID.randomUUID();
        db.update("insert into photos(id,owner_id,storage_key,content_type,size_bytes) values (?,?,'private-test-key','image/jpeg',100)", photoId, reporter);
        var view = reports.createReport(reporter, new CreateReportRequest(ReportType.BLOCKED_DRAIN, street, "Drain", photoId, null, null));
        assertNull(view.photoUrl());
        assertNull(reports.getReport(a, view.reportId()).photoUrl());
        assertEquals(photoId, db.queryForObject("select photo_id from reports where id=?", UUID.class, view.reportId()));
    }

    @Test
    void collectionsAreRepeatSafeAndClearMissedStatus() {
        LocalDate day=LocalDate.now(ZoneId.of("Africa/Lagos")).minusDays(1);
        db.update("insert into operator_wards values (?,?)",operator,ward);
        db.update("insert into collection_schedules(id,street_id,operator_id,collection_date) values (?,?,?,?)",UUID.randomUUID(),street,operator,day);
        assertEquals(CollectionStatus.MISSED,collections.listCollectionSchedules(authority,ward,day,day).getFirst().collectionStatus());
        violation("FORBIDDEN",()->collections.recordCollection(a,street,day));
        var completed=collections.recordCollection(operator,street,day);
        assertEquals(completed,collections.recordCollection(operator,street,day));
        assertEquals(CollectionStatus.DONE,collections.listCollectionSchedules(authority,ward,day,day).getFirst().collectionStatus());
        assertEquals(1,db.queryForObject("select count(*) from collections",Integer.class));
    }
    @Test
    void seedIsRepeatable() {
        var seed = new ResourceDatabasePopulator(new ClassPathResource("db/demo/demo.sql"));
        seed.execute(source);
        long count = db.queryForObject("select count(*) from report_confirmations", Long.class);
        seed.execute(source);
        assertEquals(count, db.queryForObject("select count(*) from report_confirmations", Long.class));
        assertEquals(3, count);
        var counts = db.queryForList("select (select count(*) from report_confirmations c where c.report_id=r.id) n from reports r where note like 'Fictional blocked drain %' order by r.id", Long.class);
        assertEquals(List.of(0L, 1L, 2L), counts);
        LocalDate yesterday = db.queryForObject("select cutover_date-1 from collection_migration_cutover", LocalDate.class);
        assertEquals(CollectionStatus.MISSED, collections.listCollectionSchedules(authority, UUID.fromString("10000000-0000-0000-0000-000000000001"), yesterday, yesterday).getFirst().collectionStatus());
    }

    @Test
    void countOnlyIncludesCommittedRows() throws Exception {
        try (var c = source.getConnection(); var s = c.prepareStatement("insert into report_confirmations(id,report_id,resident_id) values (?,?,?)")) {
            c.setAutoCommit(false);
            s.setObject(1, UUID.randomUUID());
            s.setObject(2, report);
            s.setObject(3, a);
            s.executeUpdate();
            assertEquals(0, reports.getReport(authority, report).confirmationCount());
            c.commit();
            assertEquals(1, reports.getReport(authority, report).confirmationCount());
        }
    }

    @Test
    void retainedFailedHistoryIsNotACompletion() {
        LocalDate day=LocalDate.now(ZoneId.of("Africa/Lagos")).minusDays(1);
        db.update("insert into operator_wards values (?,?)",operator,ward);
        db.update("insert into collection_schedules(id,street_id,operator_id,collection_date) values (?,?,?,?)",UUID.randomUUID(),street,operator,day);
        UUID failed=UUID.randomUUID();
        db.update("insert into legacy_pickups(id,street_id,pickup_date,status,recorded_by) values (?,?,?,'FAILED',?)",failed,street,day,operator);
        assertEquals(CollectionStatus.MISSED,collections.listCollectionSchedules(authority,ward,day,day).getFirst().collectionStatus());
        assertEquals("FAILED",db.queryForObject("select status from legacy_pickups where id=?",String.class,failed));
        assertEquals(0,db.queryForObject("select count(*) from collections",Integer.class));
        violation("VALIDATION_FAILED",()->collections.listCollectionSchedules(authority,ward,day,day.plusDays(31)));
    }
}
