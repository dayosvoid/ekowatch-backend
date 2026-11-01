package com.tesa.ekowatch.ward.web;

import com.tesa.ekowatch.shared.ApiExceptionHandler;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.ward.api.StreetView;
import com.tesa.ekowatch.ward.api.WardApi;
import com.tesa.ekowatch.ward.api.WardView;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WardControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WARD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STREET_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void listWardsAndStreetsUsePrincipalAndCanonicalRoutes() throws Exception {
        WardApi wards = mock(WardApi.class);
        ActorAccess actors = mock(ActorAccess.class);
        Authentication auth = mock(Authentication.class);
        when(actors.id(auth)).thenReturn(ACTOR_ID);

        when(wards.listWards(ACTOR_ID)).thenReturn(List.of(new WardView(WARD_ID, "Fictional Yaba Ward")));
        when(wards.listStreets(ACTOR_ID, WARD_ID))
                .thenReturn(List.of(new StreetView(STREET_ID, WARD_ID, "Fictional Palm Street", null)));
        MockMvc wardMvc = MockMvcBuilders.standaloneSetup(new WardController(wards, actors))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        wardMvc.perform(get("/api/v1/wards").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].wardId").value(WARD_ID.toString()));
        wardMvc.perform(get("/api/v1/wards/{wardId}/streets", WARD_ID).principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].streetId").value(STREET_ID.toString()));
    }
}
