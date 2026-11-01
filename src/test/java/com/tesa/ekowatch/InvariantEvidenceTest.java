package com.tesa.ekowatch;

import com.tesa.ekowatch.authority.api.AuthorityApi;
import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.shared.*;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.junit.jupiter.api.Assertions.*;

/** Mutation switches exist only in test sources; every run owns a disposable container. */
@SpringBootTest
@Testcontainers
@Import(InvariantEvidenceTest.Configuration.class)
class InvariantEvidenceTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:17-alpine").withUrlParam("sslmode","disable");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl); r.add("spring.datasource.username",pg::getUsername); r.add("spring.datasource.password",pg::getPassword);
    }
    @TestConfiguration static class Configuration {
        @Bean @Primary ActorAccess evidenceActors(@Qualifier("databaseActorAccess") ActorAccess actual,
                @Value("${ekowatch.evidence.mutation:none}") String mutation) {
            if(!Set.of("none","confirmation","authority").contains(mutation)) throw new IllegalArgumentException("Unknown test mutation");
            return new ActorAccess() {
                public UUID id(Authentication authentication) { return actual.id(authentication); }
                public void requireUser(UUID actorId) { actual.requireUser(actorId); }
                public void requireRole(UUID actorId,String role) {
                    // Controlled removal of only the authority role check; actual AuthorityService and SQL still execute.
                    if(!(mutation.equals("authority") && role.equals("AUTHORITY"))) actual.requireRole(actorId,role);
                }
            };
        }
    }
    @Autowired JdbcTemplate db;
    @Autowired DataSource source;
    @Autowired ReportApi reports;
    @Autowired AuthorityApi authorities;
    @Value("${ekowatch.evidence.mutation:none}") String mutation;
    UUID resident,other,authority,ward,street,report;
    UUID user(String role) {
        UUID id=UUID.randomUUID(); db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'disabled','Fictional evidence',?)",id,id+"@example.invalid",role); return id;
    }
    @BeforeEach void fixture() {
        resident=user("RESIDENT"); other=user("RESIDENT"); authority=user("AUTHORITY"); ward=UUID.randomUUID(); street=UUID.randomUUID();
        db.update("insert into wards(id,name) values (?,?)",ward,"Fictional "+ward);
        db.update("insert into streets(id,ward_id,name) values (?,?,'Fictional evidence street')",street,ward);
        report=reports.createReport(resident,UUID.randomUUID(),new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Fictional private note sentinel",null,new Location(7.12345,4.98765),null)).reportId();
    }
    int insertEvidence() throws SQLException {
        // Each attempt has its own auto-commit connection. A rejected duplicate is never followed by SQL in an aborted transaction.
        try(var connection=source.getConnection(); var statement=connection.prepareStatement(
                "insert into report_confirmations(id,report_id,resident_id) values (?,?,?)")) {
            connection.setAutoCommit(true);
            statement.setObject(1,UUID.randomUUID()); statement.setObject(2,report); statement.setObject(3,other);
            try { return statement.executeUpdate(); }
            catch(SQLException e) { if("23505".equals(e.getSQLState())) return 0; throw e; }
        }
    }
    @Test void oneResidentCountsOnceInTheRealCanonicalTable() throws Exception {
        if(mutation.equals("confirmation")) db.execute("alter table report_confirmations drop constraint uq_report_confirmation_resident");
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt=()->{ ready.countDown(); assertTrue(start.await(10,TimeUnit.SECONDS)); return insertEvidence(); };
            var first=pool.submit(attempt); var second=pool.submit(attempt);
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            first.get(30,TimeUnit.SECONDS); second.get(30,TimeUnit.SECONDS);
        }
        long rows=db.queryForObject("select count(*) from report_confirmations where report_id=? and resident_id=?",Long.class,report,other);
        System.out.println("EVIDENCE invariant=oneResidentCountsOnce mutation="+mutation+" actualRows="+rows);
        assertEquals(1L,rows,"One resident must contribute exactly one persisted confirmation, even under concurrent writes");
        assertEquals(1L,reports.getReport(other,report).confirmationCount());
    }
    @Test void performanceQueriesRefuseResidentRole() {
        LocalDate date=LocalDate.of(2026,10,31);
        assertNotNull(authorities.getWardDashboard(authority,ward,date,date));
        var rejection=assertThrows(RuleViolation.class,()->authorities.getWardDashboard(resident,ward,date,date),
                "Resident must not receive authority performance data");
        assertEquals("FORBIDDEN",rejection.code());
        System.out.println("EVIDENCE invariant=authorityRole mutation="+mutation+" outcome=FORBIDDEN");
    }
    @Test void operationLoggingPreservesRequestIdAndOmitsPrivateArguments() {
        Logger logger=(Logger)LoggerFactory.getLogger(OperationLog.class);
        ListAppender<ILoggingEvent> appender=new ListAppender<>(); appender.start(); logger.addAppender(appender);
        String previous=MDC.get("requestId");
        try {
            MDC.put("requestId","step9-evidence");
            LocalDate date=LocalDate.of(2026,10,31);
            authorities.getWardDashboard(authority,ward,date,date);
            assertThrows(RuleViolation.class,()->authorities.getWardDashboard(resident,ward,date,date));
            assertEquals("step9-evidence",MDC.get("requestId"));
            assertEquals(List.of("operation=getWardDashboard outcome=committed","operation=getWardDashboard outcome=rolled_back"),appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
            for(var event:appender.list) {
                assertEquals("step9-evidence",event.getMDCPropertyMap().get("requestId"));
                assertNull(event.getThrowableProxy());
                for(String secret:List.of(resident.toString(),authority.toString(),report.toString(),"Fictional private note sentinel","7.12345","4.98765","disabled"))
                    assertFalse(event.getFormattedMessage().contains(secret));
            }
        } finally {
            logger.detachAppender(appender); appender.stop();
            if(previous==null) MDC.remove("requestId"); else MDC.put("requestId",previous);
        }
    }
}
