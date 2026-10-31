package com.tesa.ekowatch.user.api;
import java.util.UUID;
public record CurrentUserView(UUID userId, String email, String displayName, Role role,
                              UUID wardId, UUID streetId) {}
