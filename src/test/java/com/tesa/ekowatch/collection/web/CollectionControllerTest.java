package com.tesa.ekowatch.collection.web;

import com.tesa.ekowatch.collection.api.CollectionApi;
import com.tesa.ekowatch.collection.api.CollectionScheduleView;
import com.tesa.ekowatch.collection.api.CollectionStatus;
import com.tesa.ekowatch.collection.api.CollectionView;
import com.tesa.ekowatch.shared.ApiExceptionHandler;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.ward.api.StreetView;
import com.tesa.ekowatch.ward.api.WardApi;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CollectionControllerTest {

    private final CollectionApi collections = mock(CollectionApi.class);
    private final ActorAccess actors = mock(ActorAccess.class);
    private final WardApi wards = mock(WardApi.class);
    private final Authentication authentication = mock(Authentication.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);

    private final CollectionController controller =
            new CollectionController(collections, actors, wards, clock);

    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WARD_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STREET_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID COLLECTION_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void operatorCanRecordCollectionIdempotentlyReturning201Then200() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 25);
        CollectionView view = new CollectionView(
                COLLECTION_ID,
                STREET_ID,
                date,
                Instant.parse("2026-10-25T10:00:00Z"),
                CollectionStatus.DONE
        );
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        when(collections.recordCollection(ACTOR_ID, STREET_ID, date)).thenReturn(view);

        mvc().perform(put("/api/v1/streets/{streetId}/collections/{collectionDate}", STREET_ID, date)
                        .principal(authentication))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectionId").value(COLLECTION_ID.toString()))
                .andExpect(jsonPath("$.collectionStatus").value("DONE"));

        mvc().perform(put("/api/v1/streets/{streetId}/collections/{collectionDate}", STREET_ID, date)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectionId").value(COLLECTION_ID.toString()));

        verify(actors, times(2)).id(authentication);
        verify(collections, times(2)).recordCollection(ACTOR_ID, STREET_ID, date);
    }

    @Test
    void listCollectionSchedulesSupportsStreetFilterAndPagination() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 20);
        LocalDate to = LocalDate.of(2026, 10, 25);
        CollectionScheduleView item = new CollectionScheduleView(
                UUID.randomUUID(),
                WARD_ID,
                STREET_ID,
                "Fictional Palm Street",
                from,
                CollectionStatus.SCHEDULED,
                null,
                0
        );
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        when(collections.listCollectionSchedules(ACTOR_ID, WARD_ID, from, to)).thenReturn(List.of(item));
        when(wards.listStreets(ACTOR_ID, WARD_ID)).thenReturn(
                List.of(new StreetView(STREET_ID, WARD_ID, "Fictional Palm Street", null))
        );

        var page = controller.listCollectionSchedules(authentication, WARD_ID, from, to, STREET_ID, 0, 20);
        assertEquals(1, page.totalElements());
        assertEquals(STREET_ID, page.items().getFirst().streetId());

        mvc().perform(get("/api/v1/collection-schedules")
                        .principal(authentication)
                        .param("wardId", WARD_ID.toString())
                        .param("fromDate", "2026-10-20")
                        .param("toDate", "2026-10-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].streetId").value(STREET_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void operatorControllerListsAssignedStreetsUsingDefaultLagosWindow() throws Exception {
        when(actors.id(authentication)).thenReturn(ACTOR_ID);
        when(collections.listMyAssignedStreets(
                ACTOR_ID,
                LocalDate.of(2026, 10, 25),
                LocalDate.of(2026, 11, 23)))
                .thenReturn(List.of(new StreetView(STREET_ID, WARD_ID, "Fictional Palm Street", null)));

        MockMvc operatorMvc = MockMvcBuilders.standaloneSetup(new OperatorController(collections, actors, clock))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        operatorMvc.perform(get("/api/v1/operators/me/streets").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].streetId").value(STREET_ID.toString()));
        verify(collections).listMyAssignedStreets(
                ACTOR_ID,
                LocalDate.of(2026, 10, 25),
                LocalDate.of(2026, 11, 23)
        );
    }
}
