package com.tesa.ekowatch.user.web;

import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.user.api.CurrentUserView;
import com.tesa.ekowatch.user.api.UpdatePreferencesRequest;
import com.tesa.ekowatch.user.api.UserApi;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserApi users;
    private final ActorAccess actors;

    public UserController(UserApi users, ActorAccess actors) {
        this.users = users;
        this.actors = actors;
    }

    @GetMapping
    public CurrentUserView getCurrentUser(Authentication authentication) {
        return users.getCurrentUser(actors.id(authentication));
    }

    @PutMapping("/preferences")
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
