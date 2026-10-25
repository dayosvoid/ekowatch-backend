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
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17-alpine");

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
    UUID reporter, a, b, authority, operator, ward, street, report;

    UUID user(String role) {
        UUID id = UUID.randomUUID();
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?, 'disabled','Test',?)", id, id + "@example.invalid", role);
        return id;
    }

    @BeforeEach
    void setup() {
        db.execute("truncate confirmations,reports,pickups,collection_schedules,operator_wards,streets,wards,users cascade");
        reporter = user("RESIDENT");
        a = user("RESIDENT");
        b = user("RESIDENT");
        authority = user("AUTHORITY");
        operator = user("OPERATOR");
        ward = UUID.randomUUID();
        street = UUID.randomUUID();
        db.update("insert into wards(id,name) values (?,?)", ward, "Ward " + ward);
        db.update("insert into streets(id,ward_id,name) values (?,?,?)", street, ward, "Test street");
        report = reports.create(reporter, ward, street, "Blocked drain").id();
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
                        confirmations.confirm(actor, report);
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
        assertEquals(2, reports.get(authority, report).confirmationCount());
    }

    @Test
    void sameResidentConcurrently() throws Exception {
        var results = race(a, a);
        assertEquals(1, Collections.frequency(results, "OK"));
        assertEquals(1, Collections.frequency(results, "DUPLICATE_CONFIRMATION"));
        assertEquals(1, reports.get(authority, report).confirmationCount());
        try (var c = source.getConnection(); var s = c.prepareStatement("insert into confirmations(id,report_id,user_id) values (?,?,?)")) {
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
            s.execute("create temporary table constraint_evidence (like confirmations including all)");
            s.execute("alter table constraint_evidence drop constraint constraint_evidence_report_id_user_id_key");
            String values = "'" + report + "','" + a + "'";
            s.executeUpdate("insert into constraint_evidence(id,report_id,user_id) values ('" + UUID.randomUUID() + "'," + values + "),('" + UUID.randomUUID() + "'," + values + ")");
            try (var r = s.executeQuery("select count(*) from constraint_evidence")) {
                r.next();
                assertEquals(2, r.getInt(1));
            }
            s.execute("truncate constraint_evidence");
            s.execute("alter table constraint_evidence add constraint evidence_unique unique(report_id,user_id)");
            s.executeUpdate("insert into constraint_evidence(id,report_id,user_id) values ('" + UUID.randomUUID() + "'," + values + ")");
            var e = assertThrows(java.sql.SQLException.class, () -> s.executeUpdate("insert into constraint_evidence(id,report_id,user_id) values ('" + UUID.randomUUID() + "'," + values + ")"));
            assertEquals("23505", e.getSQLState());
            System.out.println("Restored constraint SQLSTATE=" + e.getSQLState());
        }
    }

    void violation(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(RuleViolation.class, action).code());
    }

    @Test
    void eligibilityAndAuthorityRules() {
        violation("SELF_CONFIRMATION", () -> confirmations.confirm(reporter, report));
        violation("FORBIDDEN", () -> confirmations.confirm(authority, report));
        violation("REPORT_NOT_FOUND", () -> confirmations.confirm(a, UUID.randomUUID()));
        db.update("update reports set status='RESOLVED' where id=?", report);
        violation("REPORT_NOT_ELIGIBLE", () -> confirmations.confirm(a, report));
        violation("FORBIDDEN", () -> reports.authorityReports(a, 10, 0));
        assertEquals(1, reports.authorityReports(authority, 10, 0).size());
        violation("INVALID_REPORT", () -> reports.create(a, ward, street, " "));
        violation("INVALID_LOCATION", () -> reports.create(a, UUID.randomUUID(), street, "Drain"));
    }

    @Test
    void pickupsAndMissedCollections() {
        LocalDate day = LocalDate.now(ZoneId.of("Africa/Lagos")).minusDays(1);
        db.update("insert into operator_wards values (?,?)", operator, ward);
        db.update("insert into collection_schedules(id,street_id,scheduled_day) values (?,?,?)", UUID.randomUUID(), street, day.getDayOfWeek().getValue());
        assertEquals(1, collections.missed(authority, day, day).size());
        violation("FORBIDDEN", () -> collections.recordPickup(a, street, day, "COMPLETED"));
        collections.recordPickup(operator, street, day, "COMPLETED");
        assertTrue(collections.missed(authority, day, day).isEmpty());
        violation("DUPLICATE_PICKUP", () -> collections.recordPickup(operator, street, day, "FAILED"));
    }

    @Test
    void seedIsRepeatable() {
        var seed = new ResourceDatabasePopulator(new ClassPathResource("db/demo/demo.sql"));
        seed.execute(source);
        long count = db.queryForObject("select count(*) from confirmations", Long.class);
        seed.execute(source);
        assertEquals(count, db.queryForObject("select count(*) from confirmations", Long.class));
        assertEquals(3, count);
        var counts = db.queryForList("select (select count(*) from confirmations c where c.report_id=r.id) n from reports r where description like 'Fictional blocked drain %' order by r.id", Long.class);
        assertEquals(List.of(0L, 1L, 2L), counts);
        LocalDate yesterday = LocalDate.now(ZoneId.of("Africa/Lagos")).minusDays(1);
        assertFalse(collections.missed(authority, yesterday, yesterday).isEmpty());
    }

    @Test
    void countOnlyIncludesCommittedRows() throws Exception {
        try (var c = source.getConnection(); var s = c.prepareStatement("insert into confirmations(id,report_id,user_id) values (?,?,?)")) {
            c.setAutoCommit(false);
            s.setObject(1, UUID.randomUUID());
            s.setObject(2, report);
            s.setObject(3, a);
            s.executeUpdate();
            assertEquals(0, reports.get(authority, report).confirmationCount());
            c.commit();
            assertEquals(1, reports.get(authority, report).confirmationCount());
        }
    }

    @Test
    void failedPickupStillCountsAsMissed() {
        LocalDate day = LocalDate.now(ZoneId.of("Africa/Lagos")).minusDays(1);
        db.update("insert into operator_wards values (?,?)", operator, ward);
        db.update("insert into collection_schedules(id,street_id,scheduled_day) values (?,?,?)", UUID.randomUUID(), street, day.getDayOfWeek().getValue());
        collections.recordPickup(operator, street, day, "FAILED");
        assertEquals(1, collections.missed(authority, day, day).size());
        violation("INVALID_DATE_RANGE", () -> collections.missed(authority, day, day.plusDays(400)));
    }
}
