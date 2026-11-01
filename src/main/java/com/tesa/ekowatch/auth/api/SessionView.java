package com.tesa.ekowatch.auth.api;

import com.tesa.ekowatch.user.api.CurrentUserView;

import java.time.Instant;

public record SessionView(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        CurrentUserView user
) {
}
