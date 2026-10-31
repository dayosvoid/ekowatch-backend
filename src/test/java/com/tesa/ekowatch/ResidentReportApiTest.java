package com.tesa.ekowatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesa.ekowatch.report.api.*;
import com.tesa.ekowatch.photo.api.*;
import com.tesa.ekowatch.user.api.*;
import com.tesa.ekowatch.ward.api.*;
import com.tesa.ekowatch.shared.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Import(ResidentReportApiTest.Configuration.class)
class ResidentReportApiTest {
    // Match the disposable server's non-TLS configuration; production TLS is unchanged.
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17-alpine")
            .withUrlParam("sslmode", "disable");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
    }

    static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    static class TestClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(NOW);
        void set(Instant value) { time.set(value); }
        public Instant instant() { return time.get(); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
    }
    @TestConfiguration static class Configuration {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
        // Synthetic test URL only: this proves adapter wiring, not real asset sanitisation.
        @Bean PhotoUrlResolver testPhotoUrls() { return key -> key.equals("test-only-reference") ? "https://example.invalid/test-only-asset" : null; }
    }

    @Autowired JdbcTemplate db;
    @Autowired ReportApi reports;
    @Autowired WardApi wards;
    @Autowired UserApi users;
    @Autowired PhotoApi photos;
    @Autowired TestClock clock;
    @Autowired ObjectMapper json;
    UUID resident, other, third, operator, authority, ward, street, secondStreet, otherWard, otherStreet, emptyWard;

    UUID user(String role) {
        UUID id = UUID.randomUUID();
        db.update("insert into users(id,email,password_hash,display_name,role) values (?,?,'secret-hash','Fictional user',?)",id,id+"@example.invalid",role);
        return id;
    }
    @BeforeEach void fixture() {
        clock.set(NOW);
        resident=user("RESIDENT"); other=user("RESIDENT"); third=user("RESIDENT"); operator=user("OPERATOR"); authority=user("AUTHORITY");
        ward=UUID.randomUUID(); otherWard=UUID.randomUUID(); emptyWard=UUID.randomUUID();
        for(UUID id : List.of(ward,otherWard,emptyWard)) db.update("insert into wards(id,name) values (?,?)",id,"Fictional ward "+id);
        street=UUID.randomUUID(); secondStreet=UUID.randomUUID(); otherStreet=UUID.randomUUID();
        db.update("insert into streets(id,ward_id,name,latitude,longitude) values (?,?,'Palm Street',6.5,3.4)",street,ward);
        db.update("insert into streets(id,ward_id,name) values (?,?,'No map street'),(?,?,'Other ward street')",secondStreet,ward,otherStreet,otherWard);
        db.update("insert into operator_wards values (?,?)",operator,ward);
        db.update("insert into collection_schedules(id,street_id,operator_id,collection_date) values (?,?,?,cast('2026-10-30' as date))",UUID.randomUUID(),street,operator);
    }
    CreateReportRequest request(ReportType type,UUID streetId,String note) {
        return new CreateReportRequest(type,streetId,note,null,null,type==ReportType.MISSED_PICKUP ? LocalDate.of(2026,10,30) : null);
    }
    ReportView create(ReportType type) { return reports.createReport(resident,request(type,street,"Evidence")); }
    Page<ReportView> list(UUID streetId, ReportType type, FloodStatus floodStatus, Integer page, Integer size, ReportSort sort) {
        return reports.listReports(other,ward,streetId,type,floodStatus,page,size,sort);
    }
    void violation(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code,assertThrows(RuleViolation.class,action).code());
    }
    void evidence(UUID reportId,UUID actorId,Instant at) {
        db.update("insert into report_confirmations(id,report_id,resident_id,confirmed_at) values (?,?,?,?)",UUID.randomUUID(),reportId,actorId,Timestamp.from(at));
    }
    UUID photo(UUID owner) {
        UUID id=UUID.randomUUID();
        db.update("insert into photos(id,owner_id,storage_key,content_type,size_bytes,created_at) values (?,?,'test-only-reference','image/jpeg',100,?)",id,owner,Timestamp.from(NOW));
        return id;
    }
    Set<String> fields(Object value) {
        JsonNode tree=json.valueToTree(value);
        Set<String> names=new HashSet<>(); tree.fieldNames().forEachRemaining(names::add); return names;
    }

    @Test void allFourTypesDeriveWardActorAndClockAndKeepCoordinatesPrivate() {
        // Preferences do not supply a report ward: the selected street does.
        users.updateMyPreferences(resident,otherWard,otherStreet);
        for (ReportType type:ReportType.values()) {
            var request=new CreateReportRequest(type,street,"  Evidence  ",null,new Location(7.123456,4.987654),
                    type==ReportType.MISSED_PICKUP ? LocalDate.of(2026,10,30) : null);
            var view=reports.createReport(resident,request);
            assertEquals(type,view.reportType()); assertEquals(ward,view.wardId()); assertEquals(street,view.streetId());
            assertEquals("Palm Street",view.streetName()); assertEquals("Evidence",view.note()); assertEquals(NOW,view.createdAt());
            assertEquals(request.collectionDate(),view.collectionDate());
            assertEquals(new Location(6.5,3.4),view.mapLocation()); assertEquals("STREET",view.locationPrecision());
            assertEquals(0,view.confirmationCount()); assertFalse(view.confirmedByMe()); assertEquals(NOW,view.lastEvidenceAt());
            var stored=db.queryForMap("select reporter_id,ward_id,latitude,longitude,created_at,updated_at from reports where id=?",view.reportId());
            assertEquals(resident,stored.get("reporter_id")); assertEquals(ward,stored.get("ward_id"));
            assertEquals(7.123456,stored.get("latitude")); assertEquals(4.987654,stored.get("longitude"));
            assertEquals(Timestamp.from(NOW),stored.get("created_at")); assertEquals(Timestamp.from(NOW),stored.get("updated_at"));
            var read=reports.getReport(other,view.reportId());
            assertEquals(view,read);
            if(type==ReportType.FLOODED_ROAD) {
                assertEquals(FloodStatus.CURRENT,view.floodStatus()); assertEquals(NOW.plusSeconds(21600),view.expiresAt());
            } else { assertNull(view.floodStatus()); assertNull(view.expiresAt()); }
        }
        var noLocation=reports.createReport(resident,request(ReportType.BLOCKED_DRAIN,secondStreet,"No submitted location"));
        assertNull(noLocation.mapLocation()); assertNull(noLocation.photoUrl());
        assertNull(db.queryForObject("select latitude from reports where id=?",Double.class,noLocation.reportId()));
    }

    @Test void serviceRejectsInvalidInputsAndWrongRolesWithoutPartialWrites() {
        long before=db.queryForObject("select count(*) from reports",Long.class);
        violation("FORBIDDEN",()->reports.createReport(operator,request(ReportType.BLOCKED_DRAIN,street,"Drain")));
        violation("FORBIDDEN",()->reports.createReport(authority,request(ReportType.BLOCKED_DRAIN,street,"Drain")));
        violation("FORBIDDEN",()->reports.createReport(UUID.randomUUID(),request(ReportType.BLOCKED_DRAIN,street,"Drain")));
        violation("FORBIDDEN",()->reports.createReport(null,request(ReportType.BLOCKED_DRAIN,street,"Drain")));
        violation("VALIDATION_FAILED",()->reports.createReport(resident,null));
        violation("VALIDATION_FAILED",()->reports.createReport(resident,new CreateReportRequest(null,street,"Drain",null,null,null)));
        violation("VALIDATION_FAILED",()->reports.createReport(resident,request(ReportType.BLOCKED_DRAIN,null,"Drain")));
        for(String note:Arrays.asList(null,"","  ","x".repeat(1001)))
            violation("VALIDATION_FAILED",()->reports.createReport(resident,request(ReportType.BLOCKED_DRAIN,street,note)));
        violation("RESOURCE_NOT_FOUND",()->reports.createReport(resident,request(ReportType.BLOCKED_DRAIN,UUID.randomUUID(),"Drain")));
        for(Location bad:List.of(new Location(null,3.0),new Location(6.0,null),new Location(null,null),new Location(91.0,0.0),
                new Location(-91.0,0.0),new Location(0.0,181.0),new Location(0.0,-181.0),new Location(Double.NaN,0.0),new Location(0.0,Double.POSITIVE_INFINITY)))
            violation("VALIDATION_FAILED",()->reports.createReport(resident,new CreateReportRequest(ReportType.FLOODED_ROAD,street,"Flood",null,bad,null)));
        violation("VALIDATION_FAILED",()->reports.createReport(resident,new CreateReportRequest(ReportType.MISSED_PICKUP,street,"Missed",null,null,null)));
        for(ReportType type:List.of(ReportType.BLOCKED_DRAIN,ReportType.ILLEGAL_DUMPING,ReportType.FLOODED_ROAD))
            violation("VALIDATION_FAILED",()->reports.createReport(resident,new CreateReportRequest(type,street,"Evidence",null,null,LocalDate.of(2026,10,30))));
        assertEquals(before,db.queryForObject("select count(*) from reports",Long.class));
        violation("RESOURCE_NOT_FOUND",()->reports.getReport(other,UUID.randomUUID()));
        violation("VALIDATION_FAILED",()->reports.getReport(other,null));
        violation("FORBIDDEN",()->reports.getReport(UUID.randomUUID(),UUID.randomUUID()));
    }

    @Test void preferenceAndLookupApisEnforceResidentAndMatchingWardStreet() {
        assertNull(users.getCurrentUser(resident).wardId());
        assertTrue(wards.listWards(resident).stream().anyMatch(v->v.wardId().equals(ward)));
        var streets=wards.listStreets(resident,ward);
        assertEquals(List.of(secondStreet,street),streets.stream().map(StreetView::streetId).toList());
        assertNull(streets.getFirst().mapLocation()); assertEquals(new Location(6.5,3.4),streets.getLast().mapLocation());
        assertTrue(wards.listStreets(authority,emptyWard).isEmpty());
        var view=users.updateMyPreferences(resident,ward,street);
        assertEquals(resident,view.userId()); assertEquals(Role.RESIDENT,view.role());
        assertEquals(ward,view.wardId()); assertEquals(street,view.streetId());
        violation("VALIDATION_FAILED",()->users.updateMyPreferences(resident,ward,otherStreet));
        violation("VALIDATION_FAILED",()->users.updateMyPreferences(resident,otherWard,street));
        violation("VALIDATION_FAILED",()->users.updateMyPreferences(resident,ward,UUID.randomUUID()));
        violation("VALIDATION_FAILED",()->users.updateMyPreferences(resident,null,street));
        violation("VALIDATION_FAILED",()->users.updateMyPreferences(resident,ward,null));
        violation("VALIDATION_FAILED",()->users.updateMyPreferences(resident,null,null));
        for(UUID actor:List.of(operator,authority)) violation("FORBIDDEN",()->users.updateMyPreferences(actor,ward,street));
        assertEquals(view,users.getCurrentUser(resident));
        assertNull(users.getCurrentUser(operator).wardId());
        violation("FORBIDDEN",()->users.getCurrentUser(UUID.randomUUID()));
        violation("FORBIDDEN",()->wards.listWards(null));
        violation("FORBIDDEN",()->wards.listStreets(UUID.randomUUID(),ward));
        violation("RESOURCE_NOT_FOUND",()->wards.listStreets(other,UUID.randomUUID()));
        violation("VALIDATION_FAILED",()->wards.listStreets(other,null));
        assertFalse(fields(view).contains("passwordHash")); assertFalse(fields(view).contains("password_hash"));
    }

    @Test void photoOwnershipIsRequiredAndOnlyAdapterUrlsAreReturned() {
        UUID ownPhoto=photo(resident),foreignPhoto=photo(other);
        violation("RESOURCE_NOT_FOUND",()->photos.getPhoto(other,ownPhoto));
        long before=db.queryForObject("select count(*) from reports",Long.class);
        violation("FORBIDDEN",()->reports.createReport(resident,new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Drain",foreignPhoto,null,null)));
        violation("RESOURCE_NOT_FOUND",()->reports.createReport(resident,new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Drain",UUID.randomUUID(),null,null)));
        assertEquals(before,db.queryForObject("select count(*) from reports",Long.class));
        var view=reports.createReport(resident,new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Drain",ownPhoto,null,null));
        assertEquals(ownPhoto,db.queryForObject("select photo_id from reports where id=?",UUID.class,view.reportId()));
        assertEquals("https://example.invalid/test-only-asset",view.photoUrl());
        assertEquals(view.photoUrl(),reports.getReport(other,view.reportId()).photoUrl());
        assertEquals(view.photoUrl(),photos.getPhoto(other,ownPhoto).photoUrl());
        assertFalse(fields(photos.getPhoto(other,ownPhoto)).contains("storageKey"));
        assertFalse(fields(photos.getPhoto(other,ownPhoto)).contains("ownerId"));
        // A resolvable public attachment is still not owned by another resident.
        violation("FORBIDDEN",()->reports.createReport(other,new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Drain",ownPhoto,null,null)));
        db.update("update photos set storage_key='unresolved-test-reference' where id=?",ownPhoto);
        assertNull(reports.getReport(other,view.reportId()).photoUrl());
    }

    @Test void reportAndRequestSerializationHaveOnlyCanonicalPublicFields() {
        var view=reports.createReport(resident,new CreateReportRequest(ReportType.BLOCKED_DRAIN,street,"Drain",null,new Location(7.123456,4.987654),null));
        assertEquals(Set.of("reportId","reportType","wardId","streetId","streetName","note","photoUrl","createdAt","collectionDate",
                "confirmationCount","confirmedByMe","lastEvidenceAt","floodStatus","expiresAt","mapLocation","locationPrecision"),fields(view));
        assertEquals(Set.of("reportType","streetId","note","photoId","location","collectionDate"),fields(request(ReportType.BLOCKED_DRAIN,street,"Drain")));
        assertEquals(Set.of("latitude","longitude"),fields(view.mapLocation()));
        String encoded=json.valueToTree(view).toString();
        assertFalse(encoded.contains("7.123456")); assertFalse(encoded.contains("4.987654"));
        assertFalse(encoded.contains(resident.toString())); assertFalse(encoded.contains("secret-hash"));
        assertFalse(encoded.contains("reporter")); assertFalse(encoded.contains("email"));
        for(String old:List.of("create","get","list")) assertTrue(Arrays.stream(ReportApi.class.getMethods()).noneMatch(m->m.getName().equals(old)));
        db.update("update reports set status='REJECTED' where id=?",view.reportId());
        assertEquals(view,reports.getReport(other,view.reportId()));
    }

    @Test void evidenceCountsAndFreshnessUseActualRowsAndReadsNeverWrite() {
        clock.set(NOW.minusSeconds(7*3600));
        var flood=create(ReportType.FLOODED_ROAD);
        clock.set(NOW);
        assertEquals(FloodStatus.EXPIRED,reports.getReport(other,flood.reportId()).floodStatus());
        evidence(flood.reportId(),other,NOW.minusSeconds(3600));
        evidence(flood.reportId(),third,NOW.minusSeconds(2*3600));
        var read=reports.getReport(other,flood.reportId());
        assertEquals(2,read.confirmationCount()); assertTrue(read.confirmedByMe());
        assertFalse(reports.getReport(resident,flood.reportId()).confirmedByMe());
        assertEquals(NOW.minusSeconds(3600),read.lastEvidenceAt()); assertEquals(NOW.plusSeconds(5*3600),read.expiresAt());
        assertEquals(FloodStatus.CURRENT,read.floodStatus());
        var storedBefore=db.queryForMap("select * from reports where id=?",flood.reportId());
        var confirmationsBefore=db.queryForList("select * from report_confirmations where report_id=? order by id",flood.reportId());
        clock.set(read.expiresAt().minusNanos(1));
        assertEquals(FloodStatus.CURRENT,reports.getReport(other,flood.reportId()).floodStatus());
        assertEquals(1,list(null,ReportType.FLOODED_ROAD,FloodStatus.CURRENT,0,20,null).totalElements());
        clock.set(read.expiresAt());
        assertEquals(FloodStatus.EXPIRED,reports.getReport(other,flood.reportId()).floodStatus());
        assertEquals(1,list(null,ReportType.FLOODED_ROAD,FloodStatus.EXPIRED,0,20,null).totalElements());
        clock.set(read.expiresAt().plusSeconds(3600));
        assertEquals(FloodStatus.EXPIRED,reports.getReport(other,flood.reportId()).floodStatus());
        assertEquals(storedBefore,db.queryForMap("select * from reports where id=?",flood.reportId()));
        assertEquals(confirmationsBefore,db.queryForList("select * from report_confirmations where report_id=? order by id",flood.reportId()));
    }

    @Test void wardFiltersCountsPaginationAndOrderingAreDeterministic() {
        var first=create(ReportType.BLOCKED_DRAIN);
        clock.set(NOW.plusSeconds(1)); var second=create(ReportType.ILLEGAL_DUMPING);
        clock.set(NOW.plusSeconds(2)); var thirdReport=reports.createReport(resident,request(ReportType.BLOCKED_DRAIN,secondStreet,"Third"));
        reports.createReport(resident,request(ReportType.FLOODED_ROAD,otherStreet,"Different ward"));
        evidence(first.reportId(),other,NOW); evidence(first.reportId(),third,NOW);
        evidence(second.reportId(),other,NOW);
        var defaults=list(null,null,null,null,null,null);
        assertEquals(0,defaults.page()); assertEquals(20,defaults.size()); assertEquals(3,defaults.totalElements()); assertEquals(1,defaults.totalPages());
        assertEquals(List.of(first.reportId(),second.reportId(),thirdReport.reportId()),defaults.items().stream().map(ReportView::reportId).toList());
        assertTrue(defaults.items().getFirst().confirmedByMe()); assertFalse(defaults.items().getLast().confirmedByMe());
        assertEquals(List.of(thirdReport.reportId(),second.reportId(),first.reportId()),list(null,null,null,0,20,ReportSort.NEWEST).items().stream().map(ReportView::reportId).toList());
        var page=list(null,null,null,1,2,ReportSort.CONFIRMATIONS);
        assertEquals(3,page.totalElements()); assertEquals(2,page.totalPages()); assertEquals(List.of(thirdReport),page.items());
        var beyond=list(null,null,null,100,2,ReportSort.NEWEST); assertTrue(beyond.items().isEmpty()); assertEquals(3,beyond.totalElements());
        assertEquals(2,list(null,ReportType.BLOCKED_DRAIN,null,0,20,null).totalElements());
        assertEquals(2,list(street,null,null,0,20,null).totalElements());
        assertEquals(List.of(first.reportId()),list(street,ReportType.BLOCKED_DRAIN,null,0,20,null).items().stream().map(ReportView::reportId).toList());
        var empty=reports.listReports(other,emptyWard,null,null,null,null,null,null);
        assertEquals(0,empty.totalElements()); assertEquals(0,empty.totalPages()); assertTrue(empty.items().isEmpty());
        assertEquals(Set.of("items","page","size","totalElements","totalPages"),fields(defaults));
    }

    @Test void equalTimestampsAndCountsUseStableUuidOrderAcrossPages() {
        List<UUID> ids=new ArrayList<>();
        for(int i=0;i<4;i++) ids.add(create(ReportType.BLOCKED_DRAIN).reportId());
        ids.sort(Comparator.comparing(UUID::toString));
        for(ReportSort sort:ReportSort.values()) {
            List<UUID> actual=new ArrayList<>();
            for(int page=0;page<2;page++) actual.addAll(list(null,null,null,page,2,sort).items().stream().map(ReportView::reportId).toList());
            assertEquals(ids,actual);
        }
    }

    @Test void floodFilteringUsesSameEvidenceAndExpiryBoundaryAsViews() {
        clock.set(NOW.minusSeconds(21600)); var expired=create(ReportType.FLOODED_ROAD);
        clock.set(NOW.minusSeconds(21599)); var current=create(ReportType.FLOODED_ROAD);
        clock.set(NOW.minusSeconds(30000)); var renewed=create(ReportType.FLOODED_ROAD);
        evidence(renewed.reportId(),other,NOW.minusSeconds(60)); clock.set(NOW);
        create(ReportType.BLOCKED_DRAIN);
        assertEquals(Set.of(current.reportId(),renewed.reportId()),new HashSet<>(list(null,ReportType.FLOODED_ROAD,FloodStatus.CURRENT,0,100,null).items().stream().map(ReportView::reportId).toList()));
        var expiredPage=list(null,ReportType.FLOODED_ROAD,FloodStatus.EXPIRED,0,100,null);
        assertEquals(1,expiredPage.totalElements()); assertEquals(expired.reportId(),expiredPage.items().getFirst().reportId());
        assertEquals(FloodStatus.EXPIRED,expiredPage.items().getFirst().floodStatus());
        assertEquals(3,list(null,ReportType.FLOODED_ROAD,null,0,100,null).totalElements());
    }

    @Test void invalidFiltersAndUnknownActorsAreRejected() {
        for(int size:List.of(0,-1,101)) violation("VALIDATION_FAILED",()->list(null,null,null,0,size,null));
        violation("VALIDATION_FAILED",()->list(null,null,null,-1,20,null));
        violation("VALIDATION_FAILED",()->list(otherStreet,null,null,0,20,null));
        violation("VALIDATION_FAILED",()->list(UUID.randomUUID(),null,null,0,20,null));
        violation("VALIDATION_FAILED",()->list(null,null,FloodStatus.CURRENT,0,20,null));
        violation("VALIDATION_FAILED",()->list(null,ReportType.BLOCKED_DRAIN,FloodStatus.EXPIRED,0,20,null));
        violation("VALIDATION_FAILED",()->reports.listReports(other,null,null,null,null,0,20,null));
        violation("RESOURCE_NOT_FOUND",()->reports.listReports(other,UUID.randomUUID(),null,null,null,0,20,null));
        violation("FORBIDDEN",()->reports.listReports(UUID.randomUUID(),ward,null,null,null,0,20,null));
        assertTrue(list(null,null,null,Integer.MAX_VALUE,100,null).items().isEmpty());
    }
}
