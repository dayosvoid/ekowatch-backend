package com.tesa.ekowatch.user.api;
import java.util.UUID;
/** actorId is private, supplied by a trusted principal, never client JSON. */
public interface UserApi {
    CurrentUserView getCurrentUser(UUID actorId);
    CurrentUserView updateMyPreferences(UUID actorId, UUID wardId, UUID streetId);
}
