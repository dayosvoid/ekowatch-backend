package com.tesa.ekowatch.auth.web;

import com.tesa.ekowatch.auth.api.AuthApi;
import com.tesa.ekowatch.auth.api.CreateSessionRequest;
import com.tesa.ekowatch.auth.api.SessionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login and Bearer session token creation")
public class AuthController {

    private final AuthApi authApi;

    public AuthController(AuthApi authApi) {
        this.authApi = authApi;
    }

    @PostMapping("/sessions")
    @Operation(
            operationId = "createSession",
            summary = "Authenticate user and issue a Bearer session token",
            description = "Public endpoint (no Bearer token required). Validates email and password and returns a signed Bearer accessToken (8h TTL) along with the authenticated CurrentUserView.",
            security = {},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Authenticated session created")
            }
    )
    public SessionView createSession(@Valid @RequestBody CreateSessionRequest request) {
        return authApi.createSession(request);
    }

    @PostMapping("/login")
    @Operation(
            operationId = "login",
            summary = "Login alias for POST /api/v1/auth/sessions",
            description = "Public endpoint (no Bearer token required). Alias for POST /api/v1/auth/sessions returning the same SessionView (accessToken, tokenType, expiresAt, user).",
            security = {},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Authenticated session created")
            }
    )
    public SessionView login(@Valid @RequestBody CreateSessionRequest request) {
        return authApi.createSession(request);
    }
}
