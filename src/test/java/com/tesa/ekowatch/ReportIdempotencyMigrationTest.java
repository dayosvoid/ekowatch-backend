package com.tesa.ekowatch;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.SQLException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ReportIdempotencyMigrationTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17-alpine").withUrlParam("sslmode","disable");
    final String schema="idempotency_"+UUID.randomUUID().toString().replace("-","");
    final DriverManagerDataSource source=new DriverManagerDataSource(pg.getJdbcUrl()+"&currentSchema="+schema,pg.getUsername(),pg.getPassword());
    final JdbcTemplate db=new JdbcTemplate(source);
    Flyway flyway(String target) {
        return Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .placeholders(Map.of("collectionCutoverDate","AUTO","collectionMigrationMapping","[]")).target(target).load();
    }
    void reject(String state,String sql,Object... args) {
        var error=assertThrows(org.springframework.dao.DataAccessException.class,()->db.update(sql,args));
        assertInstanceOf(SQLException.class,error.getMostSpecificCause());
        assertEquals(state,((SQLException)error.getMostSpecificCause()).getSQLState());
    }
    @Test void freshSchemaHasRequiredColumnsUniqueScopeForeignKeysAndRetentionChecks() {
        assertEquals(5,flyway("5").migrate().migrationsExecuted);
        UUID resident=UUID.randomUUID(),ward=UUID.randomUUID(),street=UUID.randomUUID(),report=UUID.randomUUID(),key=UUID.randomUUID();
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'disabled','Fictional','RESIDENT')",resident,resident+"@example.invalid");
        db.update("insert into wards(id,name) values (?,'Fictional')",ward);
        db.update("insert into streets(id,ward_id,name) values (?,?,'Fictional')",street,ward);
        db.update("insert into reports(id,reporter_id,ward_id,street_id,report_type,note) values (?,?,?,?,'BLOCKED_DRAIN','Evidence')",report,resident,ward,street);
        String insert="""
            insert into idempotency_records(id,user_id,operation,idempotency_key,request_hash,report_id,response_payload,created_at,expires_at)
            values (?,?,'createReport',?,repeat('a',64),?,cast(? as jsonb),'2026-10-31T12:00:00Z','2026-11-01T12:00:00Z')
            """;
        assertEquals(1,db.update(insert,UUID.randomUUID(),resident,key,report,"{}"));
        reject("23505",insert,UUID.randomUUID(),resident,key,report,"{}");
        reject("23503",insert,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),report,"{}");
        reject("23503",insert,UUID.randomUUID(),resident,UUID.randomUUID(),UUID.randomUUID(),"{}");
        reject("23502",insert,UUID.randomUUID(),resident,null,report,"{}");
        reject("23502",insert,UUID.randomUUID(),resident,UUID.randomUUID(),report,null);
        reject("23514",insert,UUID.randomUUID(),resident,UUID.randomUUID(),report,"[]");
        reject("23514","update idempotency_records set operation='confirmReport'");
        reject("23514","update idempotency_records set request_hash='not-a-sha256-hash'");
        reject("23514","update idempotency_records set expires_at=created_at+interval '23 hours'");
        assertEquals(1,db.queryForObject("select count(*) from idempotency_records",Integer.class));
        assertEquals(0,flyway("5").migrate().migrationsExecuted);
    }
    @Test void upgradeFromPopulatedV4PreservesEveryExistingRecordAndCreatesNoFabricatedKeys() {
        flyway("4").migrate();
        // Existing insert-only seed supplies stable rows in all relevant current tables.
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/demo/demo.sql")).execute(source);
        var tables=List.of("users","wards","streets","operator_wards","collection_schedules","collections",
                "legacy_collection_schedules","legacy_pickups","reports","report_confirmations","photos","collection_migration_cutover");
        Map<String,Set<Map<String,Object>>> before=new HashMap<>();
        for(String table:tables) before.put(table,new HashSet<>(db.queryForList("select * from "+table)));
        assertEquals(1,flyway("5").migrate().migrationsExecuted);
        for(String table:tables) assertEquals(before.get(table),new HashSet<>(db.queryForList("select * from "+table)),table);
        assertEquals(0,db.queryForObject("select count(*) from idempotency_records",Integer.class));
        assertEquals(0,flyway("5").migrate().migrationsExecuted);
    }
}
