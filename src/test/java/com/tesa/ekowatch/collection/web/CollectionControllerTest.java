package com.tesa.ekowatch.collection.web;

import com.tesa.ekowatch.collection.api.CollectionApi;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.*;

class CollectionControllerTest {

    private final CollectionApi collections = mock(CollectionApi.class);
    private final ActorAccess actors = mock(ActorAccess.class);
    private final Authentication authentication = mock(Authentication.class);

    private final CollectionController controller =
            new CollectionController(collections, actors);

    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID STREET_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void operatorCanRecordPickup() {

        when(actors.id(authentication)).thenReturn(ACTOR_ID);

        CollectionController.PickupRequest request =
                new CollectionController.PickupRequest(
                        STREET_ID,
                        LocalDate.of(2026, 10, 25),
                        "COMPLETED"
                );

        controller.recordPickup(authentication, request);

        verify(actors).id(authentication);

        verify(collections).recordPickup(
                ACTOR_ID,
                STREET_ID,
                LocalDate.of(2026, 10, 25),
                "COMPLETED"
        );
    }
}