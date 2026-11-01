package com.tesa.ekowatch.user.web;

import com.tesa.ekowatch.shared.ApiExceptionHandler;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.user.api.CurrentUserView;
import com.tesa.ekowatch.user.api.Role;
import com.tesa.ekowatch.user.api.UserApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WARD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STREET_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void getCurrentUserAndUpdatePreferencesUsePrincipalAndCanonicalRoutes() throws Exception {
        UserApi users = mock(UserApi.class);
        ActorAccess actors = mock(ActorAccess.class);
        Authentication auth = mock(Authentication.class);
        when(actors.id(auth)).thenReturn(ACTOR_ID);

        CurrentUserView profile = new CurrentUserView(
                ACTOR_ID,
                "resident.one@demo.ekowatch.invalid",
                "Fictional Resident One",
                Role.RESIDENT,
                WARD_ID,
                STREET_ID
        );
        when(users.getCurrentUser(ACTOR_ID)).thenReturn(profile);
        when(users.updateMyPreferences(ACTOR_ID, WARD_ID, STREET_ID)).thenReturn(profile);

        MockMvc userMvc = MockMvcBuilders.standaloneSetup(new UserController(users, actors))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        userMvc.perform(get("/api/v1/users/me").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ACTOR_ID.toString()));
        userMvc.perform(put("/api/v1/users/me/preferences")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"wardId":"%s","streetId":"%s"}
                                """.formatted(WARD_ID, STREET_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wardId").value(WARD_ID.toString()));
    }
}
