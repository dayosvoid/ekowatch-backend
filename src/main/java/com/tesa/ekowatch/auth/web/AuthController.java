package com.tesa.ekowatch.auth.web;

import com.tesa.ekowatch.auth.api.AuthApi;
import com.tesa.ekowatch.auth.api.CreateSessionRequest;
import com.tesa.ekowatch.auth.api.SessionView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApi authApi;

    public AuthController(AuthApi authApi) {
        this.authApi = authApi;
    }

    @PostMapping("/sessions")
    public SessionView createSession(@Valid @RequestBody CreateSessionRequest request) {
        return authApi.createSession(request);
    }
}
