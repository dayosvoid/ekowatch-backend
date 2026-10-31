package com.tesa.ekowatch;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.Instant;
import java.sql.SQLException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ConfirmationMigrationTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17-alpine").withUrlParam("sslmode","disable");
    final String schema="confirmation_"+UUID.randomUUID().toString().replace("-","");
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
    @Test void emptyDatabaseUsesCanonicalTableColumnsConstraintsAndIndex() {
        assertEquals(4,flyway("4").migrate().migrationsExecuted);
        assertNull(db.queryForObject("select to_regclass(?)::text",String.class,schema+".confirmations"));
        assertNotNull(db.queryForObject("select to_regclass(?)::text",String.class,schema+".ix_report_confirmations_resident"));
        assertEquals(List.of("id","report_id","resident_id","confirmed_at"),db.queryForList(
            "select column_name from information_schema.columns where table_schema=? and table_name='report_confirmations' order by ordinal_position",String.class,schema));
        assertEquals("UNIQUE (report_id, resident_id)",db.queryForObject(
            "select pg_get_constraintdef(oid) from pg_constraint where conrelid=?::regclass and conname='uq_report_confirmation_resident'",String.class,schema+".report_confirmations"));
        assertEquals(2,db.queryForObject("select count(*) from pg_constraint where conrelid=?::regclass and contype='f'",Integer.class,schema+".report_confirmations"));
    }

    @Test void populatedV1RetainsAllIdsTimestampsEvidenceAndForeignKeys() {
        flyway("1").migrate();
        UUID reporter=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(),ward=UUID.randomUUID(),street=UUID.randomUUID(),report=UUID.randomUUID();
        for(UUID id:List.of(reporter,a,b)) db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'disabled','Fictional','RESIDENT')",id,id+"@example.invalid");
        db.update("insert into wards(id,name) values (?,'Fictional')",ward);
        db.update("insert into streets(id,ward_id,name) values (?,?,'Fictional')",street,ward);
        db.update("insert into reports(id,reporter_id,ward_id,street_id,report_type,description,created_at,updated_at) values (?,?,?,?,'BLOCKED_DRAIN','Historical','2026-09-30T00:00:00Z','2026-09-30T00:00:00Z')",report,reporter,ward,street);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        Instant time=Instant.parse("2026-10-01T10:11:12.123456Z");
        db.update("insert into confirmations(id,report_id,user_id,created_at) values (?,?,?,?),(?,?,?,?)",
                first,report,a,java.sql.Timestamp.from(time),second,report,b,java.sql.Timestamp.from(time.plusSeconds(1)));
        var old=db.queryForList("select * from confirmations order by id");
        flyway("4").migrate();
        var renamed=db.queryForList("select * from report_confirmations order by id");
        for(var row:renamed) { row.put("user_id",row.remove("resident_id")); row.put("created_at",row.remove("confirmed_at")); }
        assertEquals(old,renamed);
        assertEquals(time,db.queryForObject("select confirmed_at from report_confirmations where id=?",java.sql.Timestamp.class,first).toInstant());
        assertEquals(0,flyway("4").migrate().migrationsExecuted);
        reject("23505","insert into report_confirmations(id,report_id,resident_id) values (?,?,?)",UUID.randomUUID(),report,a);
        reject("23503","insert into report_confirmations(id,report_id,resident_id) values (?,?,?)",UUID.randomUUID(),report,UUID.randomUUID());
        reject("23503","insert into report_confirmations(id,report_id,resident_id) values (?,?,?)",UUID.randomUUID(),UUID.randomUUID(),reporter);
        reject("23502","insert into report_confirmations(id,report_id,resident_id) values (?,?,?)",UUID.randomUUID(),report,null);
        assertEquals(2,db.queryForObject("select count(*) from report_confirmations",Integer.class));
        assertEquals("Historical",db.queryForObject("select note from reports where id=?",String.class,report));
    }
}