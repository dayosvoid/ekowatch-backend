package com.tesa.ekowatch;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CollectionMigrationTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17-alpine").withUrlParam("sslmode","disable");
    final String schema="collections_"+UUID.randomUUID().toString().replace("-","");
    final DriverManagerDataSource source=new DriverManagerDataSource(pg.getJdbcUrl()+"&currentSchema="+schema,pg.getUsername(),pg.getPassword());
    final JdbcTemplate db=new JdbcTemplate(source);
    final UUID operator=UUID.randomUUID(),resident=UUID.randomUUID(),ward=UUID.randomUUID(),street=UUID.randomUUID();
    final String cutover="2026-10-01";
    Flyway migration(String target,String mapping) {
        return Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .placeholders(Map.of("collectionCutoverDate",cutover,"collectionMigrationMapping",mapping)).target(target).load();
    }
    void fixture() {
        migration("2","[]").migrate();
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'disabled','Fictional operator','OPERATOR'),(?,?,'disabled','Fictional resident','RESIDENT')",
            operator,operator+"@example.invalid",resident,resident+"@example.invalid");
        db.update("insert into wards(id,name) values (?,'Fictional ward')",ward);
        db.update("insert into streets(id,ward_id,name) values (?,?,'Fictional street')",street,ward);
        db.update("insert into operator_wards values (?,?)",operator,ward);
    }
    void weekly() {
        for(int day=1;day<=7;day++) db.update("insert into collection_schedules(id,street_id,scheduled_day) values (?,?,?)",UUID.randomUUID(),street,day);
    }
    UUID pickup(String status,String day,UUID actor) {
        UUID id=UUID.randomUUID();
        db.update("insert into pickups(id,street_id,pickup_date,status,recorded_by,created_at) values (?,?,cast(? as date),?,?,'2026-10-01T10:00:00Z')",id,street,day,status,actor);
        return id;
    }
    String mapping(UUID actor) { return "[{\"streetId\":\""+street+"\",\"operatorId\":\""+actor+"\"}]"; }
    void failed(String mapping,String message) {
        var error=assertThrows(FlywayException.class,()->migration("3",mapping).migrate());
        assertTrue(error.getMessage().contains(message),error.getMessage());
        assertEquals(0,db.queryForObject("select count(*) from flyway_schema_history where version='3'",Integer.class));
        assertNotNull(db.queryForObject("select to_regclass(?)::text",String.class,schema+".pickups"));
        assertNull(db.queryForObject("select to_regclass(?)::text",String.class,schema+".legacy_pickups"));
    }

    @Test void emptyDatabaseHasCanonicalConstraintsAndRecordedCutover() {
        assertEquals(3,migration("3","[]").migrate().migrationsExecuted);
        assertEquals(LocalDate.of(2026,10,1),db.queryForObject("select cutover_date from collection_migration_cutover",LocalDate.class));
        assertEquals(LocalDate.of(2026,10,30),db.queryForObject("select horizon_end_date from collection_migration_cutover",LocalDate.class));
        assertEquals(0,db.queryForObject("select count(*) from collection_schedules",Integer.class));
        assertEquals(0,db.queryForObject("select count(*) from collections",Integer.class));
        assertNotNull(db.queryForObject("select to_regclass(?)::text",String.class,schema+".legacy_pickups"));
        assertNotNull(db.queryForObject("select to_regclass(?)::text",String.class,schema+".legacy_collection_schedules"));
    }

    @Test void populatedMigrationKeepsEveryLegacyAttemptAndBackfillsOnlyRealCompletionDates() {
        fixture(); weekly();
        UUID old=pickup("COMPLETED","2026-09-30",operator);
        UUID overlap=pickup("COMPLETED","2026-10-01",operator);
        UUID failed=pickup("FAILED","2026-10-02",operator);
        UUID inactiveStreet=UUID.randomUUID();
        db.update("insert into streets(id,ward_id,name) values (?,?,'Inactive')",inactiveStreet,ward);
        db.update("insert into collection_schedules(id,street_id,scheduled_day,active) values (?,?,1,false)",UUID.randomUUID(),inactiveStreet);
        UUID report=UUID.randomUUID();
        db.update("insert into reports(id,reporter_id,ward_id,street_id,report_type,note,collection_date,status,created_at,updated_at) values (?,?,?,?,'MISSED_PICKUP','Historical','2026-09-30','RESOLVED','2026-10-01T11:00:00Z','2026-10-01T12:00:00Z')",report,resident,ward,street);
        var attempts=db.queryForList("select * from pickups order by id");
        var schedules=db.queryForList("select * from collection_schedules order by id");
        var evidence=db.queryForMap("select * from reports where id=?",report);
        migration("3","[]").migrate();
        assertEquals(attempts,db.queryForList("select * from legacy_pickups order by id"));
        assertEquals(schedules,db.queryForList("select * from legacy_collection_schedules order by id"));
        assertEquals(evidence,db.queryForMap("select * from reports where id=?",report));
        assertEquals(Set.of(old,overlap),new HashSet<>(db.queryForList("select id from collections",UUID.class)));
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"),db.queryForObject("select completed_at from collections where id=?",java.sql.Timestamp.class,old).toInstant());
        assertEquals(operator,db.queryForObject("select completed_by from collections where id=?",UUID.class,old));
        assertEquals(31,db.queryForObject("select count(*) from collection_schedules",Integer.class));
        assertEquals(0,db.queryForObject("select count(*) from collection_schedules where street_id=?",Integer.class,inactiveStreet));
        assertEquals(0,db.queryForObject("select count(*) from collection_schedules where collection_date<'2026-09-30' or collection_date>'2026-10-30'",Integer.class));
        assertEquals(0,db.queryForObject("select count(*) from collections where id=?",Integer.class,failed));
        var newSchedules=db.queryForList("select * from collection_schedules order by id");
        assertEquals(0,migration("3","[]").migrate().migrationsExecuted);
        assertEquals(newSchedules,db.queryForList("select * from collection_schedules order by id"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("insert into collections(id,street_id,collection_date,completed_by,completed_at) values (?,?,cast('2026-09-29' as date),?,now())",UUID.randomUUID(),street,operator));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("insert into collections(id,street_id,collection_date,completed_by,completed_at) values (?,?,cast('2026-09-30' as date),?,now())",UUID.randomUUID(),street,operator));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("insert into collection_schedules(id,street_id,operator_id,collection_date) values (?,?,?,'2026-10-01')",UUID.randomUUID(),street,operator));
    }

    @Test void multipleWardOperatorsNeedExplicitMappingAndRetryNeedsNoRepair() {
        fixture(); weekly();
        UUID selected=UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff1");
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,'selected@example.invalid','disabled','Selected','OPERATOR')",selected);
        db.update("insert into operator_wards values (?,?)",selected,ward);
        failed("[]","multiple eligible operators");
        migration("3",mapping(selected)).migrate();
        assertEquals(30,db.queryForObject("select count(*) from collection_schedules where operator_id=?",Integer.class,selected));
        assertEquals(0,db.queryForObject("select count(*) from collection_schedules where operator_id=?",Integer.class,operator));
    }

    @Test void missingWardOperatorCannotBeGuessed() {
        fixture(); weekly();
        db.update("delete from operator_wards");
        failed("[]","zero or multiple");
        failed(mapping(operator),"invalid explicit operator/ward mapping");
    }

    @Test void invalidHistoricalRoleAndWardAssignmentsFailWithoutLosingRows() {
        fixture();
        UUID id=pickup("COMPLETED","2026-09-30",resident);
        failed("[]","invalid historical completion");
        assertEquals(resident,db.queryForObject("select recorded_by from pickups where id=?",UUID.class,id));
        db.update("update pickups set recorded_by=? where id=?",operator,id);
        db.update("delete from operator_wards");
        failed("[]","invalid historical completion");
    }

    @Test void historicalCompletionWinsOnlyWhenExplicitWeeklyMappingAgrees() {
        fixture(); weekly(); UUID original=pickup("COMPLETED","2026-10-01",operator);
        UUID other=UUID.randomUUID();
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'disabled','Other','OPERATOR')",other,other+"@example.invalid");
        db.update("insert into operator_wards values (?,?)",other,ward);
        failed(mapping(other),"conflicts with the recorded historical operator");
        migration("3",mapping(operator)).migrate();
        assertEquals(operator,db.queryForObject("select operator_id from collection_schedules where collection_date='2026-10-01'",UUID.class));
        assertEquals(original,db.queryForObject("select id from collections",UUID.class));
    }

    @Test void preexistingMissedReportRequiresAnExplicitHistoricalOccurrence() {
        fixture();
        UUID report=UUID.randomUUID();
        db.update("insert into reports(id,reporter_id,ward_id,street_id,report_type,note,collection_date) values (?,?,?,?,'MISSED_PICKUP','Historical','2026-09-01')",report,resident,ward,street);
        failed("[]","existing missed-pickup reports need explicit dated occurrence mappings");
        String dated="[{\"streetId\":\""+street+"\",\"operatorId\":\""+operator+"\",\"collectionDate\":\"2026-09-01\"}]";
        migration("3",dated).migrate();
        assertEquals(1,db.queryForObject("select count(*) from collection_schedules",Integer.class));
        assertEquals(0,db.queryForObject("select count(*) from collections",Integer.class));
        assertEquals(report,db.queryForObject("select id from reports",UUID.class));
    }

    @Test void explicitDateOverrideResolvesWeeklyDefaultWithoutChangingRecordedOperator() {
        fixture(); weekly(); UUID original=pickup("COMPLETED","2026-10-01",operator);
        UUID other=UUID.randomUUID();
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'disabled','Other','OPERATOR')",other,other+"@example.invalid");
        db.update("insert into operator_wards values (?,?)",other,ward);
        String mappings="[{\"streetId\":\""+street+"\",\"operatorId\":\""+other+"\"},{\"streetId\":\""+street+"\",\"collectionDate\":\"2026-10-01\",\"operatorId\":\""+operator+"\"}]";
        migration("3",mappings).migrate();
        assertEquals(29,db.queryForObject("select count(*) from collection_schedules where operator_id=?",Integer.class,other));
        assertEquals(operator,db.queryForObject("select operator_id from collection_schedules where collection_date='2026-10-01'",UUID.class));
        assertEquals(original,db.queryForObject("select id from collections",UUID.class));
    }

    @Test void invalidActiveAssignmentCannotBeSilentlyIgnored() {
        fixture(); weekly();
        db.update("insert into operator_wards values (?,?)",resident,ward);
        failed("[]","invalid active ward assignment");
        assertEquals(2,db.queryForObject("select count(*) from operator_wards",Integer.class));
    }

    @Test void datedMappingCannotInventAnUnrelatedHistoricalOccurrence() {
        fixture();
        String dated="[{\"streetId\":\""+street+"\",\"operatorId\":\""+operator+"\",\"collectionDate\":\"2026-09-01\"}]";
        failed(dated,"unused dated mapping");
    }

    @Test void futureCompletionsAndFutureReportHistoryFailPreflight() {
        fixture(); UUID id=pickup("COMPLETED","9999-01-01",operator);
        failed("[]","future date");
        assertEquals(id,db.queryForObject("select id from pickups",UUID.class));
        db.update("delete from pickups where id=?",id);
        db.update("insert into reports(id,reporter_id,ward_id,street_id,report_type,note,collection_date) values (?,?,?,?,'MISSED_PICKUP','Future','9999-01-01')",UUID.randomUUID(),resident,ward,street);
        failed("[]","elapsed date");
    }
}
