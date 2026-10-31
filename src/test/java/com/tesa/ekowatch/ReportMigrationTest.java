package com.tesa.ekowatch;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Every test has a fresh schema in a disposable PostgreSQL container; no clean/reset calls. */
@Testcontainers
class ReportMigrationTest {
    @Container
    // All Flyway, JDBC and validation connections use this disposable non-TLS server.
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17-alpine")
            .withUrlParam("sslmode", "disable");
    final String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
    final String url = pg.getJdbcUrl() + (pg.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
    final DriverManagerDataSource source = new DriverManagerDataSource(url, pg.getUsername(), pg.getPassword());
    final JdbcTemplate db = new JdbcTemplate(source);
    final UUID user = UUID.randomUUID(), resident = UUID.randomUUID(), ward = UUID.randomUUID(), street = UUID.randomUUID();
    final UUID report = UUID.randomUUID(), photo = UUID.randomUUID();

    Flyway flyway(String target) {
        return Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").placeholders(Map.of("collectionCutoverDate","AUTO","collectionMigrationMapping","[]")).target(target).load();
    }

    void baseRows() {
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'disabled','Operator','OPERATOR'),(?,?,'disabled','Resident','RESIDENT')",
                user, user + "@example.invalid", resident, resident + "@example.invalid");
        db.update("insert into wards(id,name) values (?,'Migration ward')", ward);
        db.update("insert into streets(id,ward_id,name) values (?,?,'Migration street')", street, ward);
    }

    void legacyReport(String note) {
        db.update("insert into reports(id,reporter_id,ward_id,street_id,report_type,description,status,created_at,updated_at) values (?,?,?,?,'BLOCKED_DRAIN',?,'RESOLVED','2026-01-01T10:00:00Z','2026-01-02T10:00:00Z')",
                report, resident, ward, street, note);
    }

    void reject(String state, String sql, Object... args) {
        var error = assertThrows(org.springframework.dao.DataAccessException.class, () -> db.update(sql, args));
        assertInstanceOf(SQLException.class, error.getMostSpecificCause());
        assertEquals(state, ((SQLException) error.getMostSpecificCause()).getSQLState());
    }

    @Test
    void populatedV1PreservesEveryTableAndValidatesJpa() {
        flyway("1").migrate();
        baseRows();
        legacyReport("Historical evidence");
        db.update("insert into operator_wards values (?,?)", user, ward);
        db.update("insert into collection_schedules(id,street_id,scheduled_day,active) values (?,?,1,false)", UUID.randomUUID(), street);
        for (String status : List.of("COMPLETED", "FAILED"))
            db.update("insert into pickups(id,street_id,pickup_date,status,recorded_by,created_at) values (?,?,cast(? as date),?,?,'2026-01-03T10:00:00Z')",
                    UUID.randomUUID(), street, status.equals("COMPLETED") ? "2026-01-01" : "2026-01-02", status, user);
        db.update("insert into confirmations(id,report_id,user_id,created_at) values (?,?,?,'2026-01-02T11:00:00Z')", UUID.randomUUID(), report, resident);
        List<String> tables = List.of("users", "wards", "streets", "operator_wards", "collection_schedules", "pickups", "reports", "confirmations");
        Map<String, List<Map<String, Object>>> before = new HashMap<>();
        for (String table : tables) before.put(table, db.queryForList("select * from " + table));

        assertEquals(1, flyway("2").migrate().migrationsExecuted);
        for (String table : tables) {
            var after = db.queryForList("select * from " + table);
            for (var row : after) {
                if (table.equals("reports")) {
                    row.put("description", row.remove("note"));
                    for (String column : List.of("photo_id", "latitude", "longitude", "collection_date")) assertNull(row.remove(column));
                }
                if (table.equals("users")) {
                    assertNull(row.remove("ward_id"));
                    assertNull(row.remove("street_id"));
                }
                if (table.equals("streets")) {
                    assertNull(row.remove("latitude"));
                    assertNull(row.remove("longitude"));
                }
            }
            assertEquals(new HashSet<>(before.get(table)), new HashSet<>(after), table);
        }
        assertEquals(0, flyway("2").migrate().migrationsExecuted);
        flyway("4").migrate();
        try (var context = new SpringApplicationBuilder(EkoWatchApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + pg.getUsername(),
                "--spring.datasource.password=" + pg.getPassword(), "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.schemas=" + schema, "--spring.flyway.default-schema=" + schema,
                "--spring.jpa.properties.hibernate.default_schema=" + schema, "--ekowatch.demo.enabled=false")) {
            assertNotNull(context.getBean(jakarta.persistence.EntityManagerFactory.class));
        }
    }

    @Test
    void emptyDatabaseMigratesAndEnforcesNewAndExistingConstraints() {
        assertEquals(2, flyway("2").migrate().migrationsExecuted);
        baseRows();
        db.update("insert into photos(id,owner_id,storage_key,content_type,size_bytes,created_at) values (?,?,?,'image/jpeg',123,'2026-01-01T10:00:00Z')", photo, resident, "test/sanitised-reference");
        assertEquals(Instant.parse("2026-01-01T10:00:00Z"), db.queryForObject("select created_at from photos where id=?", java.sql.Timestamp.class, photo).toInstant());
        for (String type : List.of("MISSED_PICKUP", "ILLEGAL_DUMPING", "BLOCKED_DRAIN", "FLOODED_ROAD")) {
            db.update("insert into reports(id,reporter_id,ward_id,street_id,report_type,note,photo_id,latitude,longitude,collection_date) values (?,?,?,?,?,?,?,90,-180,cast(? as date))",
                    UUID.randomUUID(), resident, ward, street, type, "Evidence", photo, type.equals("MISSED_PICKUP") ? "2026-01-01" : null);
        }
        assertEquals(4, db.queryForObject("select count(*) from reports", Integer.class));
        String insert = "insert into reports(id,reporter_id,ward_id,street_id,report_type,note,collection_date) values (?,?,?,?,?,?,cast(? as date))";
        reject("23514", insert, UUID.randomUUID(), resident, ward, street, "OTHER", "Evidence", null);
        reject("23514", insert, UUID.randomUUID(), resident, ward, street, "MISSED_PICKUP", "Evidence", null);
        reject("23514", insert, UUID.randomUUID(), resident, ward, street, "BLOCKED_DRAIN", "Evidence", "2026-01-01");
        for (String note : List.of("", " ", " padded", "padded "))
            reject("23514", insert, UUID.randomUUID(), resident, ward, street, "BLOCKED_DRAIN", note, null);
        reject("22001", insert, UUID.randomUUID(), resident, ward, street, "BLOCKED_DRAIN", "x".repeat(1001), null);
        db.update(insert, report, resident, ward, street, "BLOCKED_DRAIN", "x".repeat(1000), null);
        db.update("update users set ward_id=?,street_id=? where id=?", ward, street, resident);
        reject("23514", "update users set street_id=null where id=?", resident);
        reject("23514", "update users set ward_id=null where id=?", resident);
        UUID otherWard = UUID.randomUUID();
        db.update("insert into wards(id,name) values (?,'Other ward')", otherWard);
        reject("23503", "update users set ward_id=? where id=?", otherWard, resident);
        reject("23503", "update users set street_id=? where id=?", UUID.randomUUID(), resident);
        reject("23503", "update reports set ward_id=? where id=?", otherWard, report);
        reject("23503", "update reports set photo_id=? where id=?", UUID.randomUUID(), report);
        reject("23503", "update photos set owner_id=? where id=?", UUID.randomUUID(), photo);
        reject("23514", "update photos set size_bytes=0 where id=?", photo);
        reject("23514", "update photos set size_bytes=-1 where id=?", photo);
        reject("23514", "update photos set storage_key=' ' where id=?", photo);
        reject("23514", "update photos set content_type='' where id=?", photo);
        reject("23502", "update photos set owner_id=null where id=?", photo);
        for (String table : List.of("streets", "reports")) {
            UUID id = table.equals("streets") ? street : report;
            for (String coordinates : List.of("latitude=1,longitude=null", "latitude=null,longitude=1", "latitude=91,longitude=0",
                    "latitude=-91,longitude=0", "latitude=0,longitude=181", "latitude=0,longitude=-181",
                    "latitude='NaN',longitude=0", "latitude=0,longitude='Infinity'"))
                reject("23514", "update " + table + " set " + coordinates + " where id=?", id);
            db.update("update " + table + " set latitude=-90,longitude=180 where id=?", id);
            db.update("update " + table + " set latitude=null,longitude=null where id=?", id);
        }
    }

    @Test
    void overlongLegacyNotesFailPreflightWithoutPartialChanges() {
        assertPreflightFailure("x".repeat(1001));
    }

    @Test
    void untrimmedLegacyNotesRequireExplicitRemediation() {
        assertPreflightFailure(" Historical evidence ");
    }

    void assertPreflightFailure(String note) {
        flyway("1").migrate();
        baseRows();
        legacyReport(note);
        var error = assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway("2").migrate());
        assertTrue(error.getMessage().contains("V2 preflight: incompatible report notes"));
        assertEquals(note, db.queryForObject("select description from reports where id=?", String.class, report));
        assertEquals("RESOLVED", db.queryForObject("select status from reports where id=?", String.class, report));
        assertEquals(1, db.queryForObject("select count(*) from flyway_schema_history where success and version='1'", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from flyway_schema_history where version='2'", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from information_schema.columns where table_schema=? and column_name='note' and table_name='reports'", Integer.class, schema));
        assertNull(db.queryForObject("select to_regclass(?)::text", String.class, schema + ".photos"));
    }
}
