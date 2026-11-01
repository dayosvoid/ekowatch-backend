package com.tesa.ekowatch.user.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdatePreferencesRequest(
        @NotNull(message = "wardId is required.")
        UUID wardId,
        @NotNull(message = "streetId is required.")
        UUID streetId
) {
}
