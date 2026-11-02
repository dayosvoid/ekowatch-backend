package com.tesa.ekowatch.user.web;

import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.user.api.CurrentUserView;
import com.tesa.ekowatch.user.api.UpdatePreferencesRequest;
import com.tesa.ekowatch.user.api.UserApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Users", description = "Authenticated user profile and resident ward/street preferences")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserApi users;
    private final ActorAccess actors;

    public UserController(UserApi users, ActorAccess actors) {
        this.users = users;
        this.actors = actors;
    }

    @GetMapping
    @Operation(
            operationId = "getCurrentUser",
            summary = "Get the authenticated user's profile and ward/street preferences",
            description = "Requires `Authorization: Bearer <token>`."
    )
    public CurrentUserView getCurrentUser(Authentication authentication) {
        return users.getCurrentUser(actors.id(authentication));
    }

    @PutMapping("/preferences")
    @Operation(
            operationId = "updateMyPreferences",
            summary = "Set or update the authenticated resident's ward and street preference",
            description = "Requires Role: `RESIDENT` (`Authorization: Bearer <token>`). Validates that `streetId` belongs to `wardId`."
    )
    public CurrentUserView updateMyPreferences(
            Authentication authentication,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return users.updateMyPreferences(
                actors.id(authentication),
                request.wardId(),
                request.streetId()
        );
    }
}
