package com.tesa.ekowatch;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Captures actual EXPLAIN output on the fixed fictional fixture, with the normal planner settings. */
@Testcontainers
class ReadProjectionPlanTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17-alpine").withUrlParam("sslmode","disable");
    final DriverManagerDataSource source=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
    final JdbcTemplate db=new JdbcTemplate(source);
    Flyway migration(String target) {
        return Flyway.configure().dataSource(source).placeholders(Map.of("collectionCutoverDate","AUTO","collectionMigrationMapping","[]")).target(target).load();
    }
    String block(String module,String signature) throws Exception {
        String java=Files.readString(Path.of("src/main/java/com/tesa/ekowatch/"+module));
        String delimiter=String.valueOf('"').repeat(3);
        int cursor=0;
        while(true) {
            int start=java.indexOf(delimiter,cursor);
            if(start<0) break;
            int end=java.indexOf(delimiter,start+3);
            if(end<0) break;
            String sql=java.substring(start+3,end); cursor=end+3;
            if(sql.contains(signature)) return sql.stripIndent();
        }
        throw new AssertionError("Production SQL block not found: "+signature);
    }
    record Query(String name,String sql,int expectedRows,Object... parameters) {}
    List<Query> queries() throws Exception {
        UUID actor=UUID.fromString("00000000-0000-0000-0000-000000000001"),operator=UUID.fromString("00000000-0000-0000-0000-000000000004"),ward=UUID.fromString("10000000-0000-0000-0000-000000000001");
        LocalDate from=LocalDate.of(2026,10,16),to=LocalDate.of(2026,11,14),today=LocalDate.of(2026,10,31);
        Timestamp cutoff=Timestamp.from(Instant.parse("2026-10-31T06:00:00Z"));
        String report="report/service/ReportService.java",collection="collection/service/CollectionService.java",authority="authority/service/AuthorityService.java";
        String views=block(report,"with report_views as (");
        return List.of(
            new Query("listReports: FLOODED_ROAD / NEWEST / page 0 size 20",views+"select * from report_views where ward_id=? and report_type=? order by created_at desc,report_id limit ? offset ?",3,actor,ward,"FLOODED_ROAD",20,0L),
            new Query("listWardHazards: current at fixed presentation",views+block(report,"select * from report_views where ward_id=? and"),5,actor,ward,cutoff),
            new Query("listCollectionSchedules: full inclusive period",block(collection,"select cs.id,s.ward_id"),172,ward,from,to),
            new Query("listMyAssignedStreets: operator 4",block(collection,"select distinct s.id,s.ward_id"),4,operator,from,to),
            new Query("getWardDashboard: performance",block(authority,"with occurrences as ("),2,ward,from,to,today),
            new Query("getWardDashboard: hazard counts",block(authority,"select count(*) filter(where report_type='BLOCKED_DRAIN')"),1,cutoff,ward)
        );
    }
    void plans(String label,List<Query> queries,StringBuilder evidence) {
        evidence.append("\n=== ").append(label).append(" ===\n");
        for(var query:queries) {
            var rows=db.queryForList(query.sql(),query.parameters());
            assertEquals(query.expectedRows(),rows.size(),query.name());
            evidence.append("\nQUERY ").append(query.name()).append("\nSQL:\n").append(query.sql())
                    .append("\nPARAMETERS (fictional fixture only): ").append(Arrays.toString(query.parameters()))
                    .append("\nReturned rows: ").append(rows.size()).append("\n");
            for(String line:db.queryForList("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) "+query.sql(),String.class,query.parameters()))
                evidence.append(line).append('\n');
        }
    }
    @Test void freshForwardPathIndexesPreserveRowsAndCaptureProductionQueryPlans() throws Exception {
        assertEquals(5,migration("5").migrate().migrationsExecuted);
        new ResourceDatabasePopulator(new ClassPathResource("db/demo/demo.sql")).execute(source);
        var tables=List.of("users","wards","streets","operator_wards","collection_schedules","collections","reports","report_confirmations","photos","idempotency_records","legacy_pickups","legacy_collection_schedules","collection_migration_cutover");
        Map<String,Set<Map<String,Object>>> before=new HashMap<>();
        for(String table:tables) { before.put(table,new HashSet<>(db.queryForList("select * from "+table))); db.execute("vacuum analyze "+table); }
        var queries=queries();
        StringBuilder evidence=new StringBuilder("Step 9 actual PostgreSQL plans; fixed fictional demo; normal planner settings.\nServer: "+db.queryForObject("select version()",String.class)+"\nCaptured: "+Instant.now()+"\n9 reports, 5 confirmations, 172 schedules, 3 completions, 8 streets, 8 users.\nSQL text blocks extracted from production source; listReports appends its selected dynamic filter/order.\nNot a load test. Tiny-table sequential scans are expected; timings are observations, not a performance claim.\n");
        plans("BEFORE V6",queries,evidence);
        assertEquals(1,migration("6").migrate().migrationsExecuted);
        for(String table:tables) assertEquals(before.get(table),new HashSet<>(db.queryForList("select * from "+table)),table);
        for(String index:List.of("ix_reports_ward_type_created","ix_reports_missed_street_date","ix_schedule_date_street","ix_schedule_operator_date_street","ix_confirmation_report_evidence"))
            assertNotNull(db.queryForObject("select indexdef from pg_indexes where schemaname='public' and indexname=?",String.class,index));
        assertEquals(0,migration("6").migrate().migrationsExecuted);
        plans("AFTER V6",queries,evidence);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/step9-query-plans.txt"),evidence,StandardCharsets.UTF_8);
        System.out.println("EVIDENCE queryPlans=target/step9-query-plans.txt fixtureReports=9 fixtureSchedules=172");
    }
}
