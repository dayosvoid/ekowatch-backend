package com.tesa.ekowatch.user.api;

import java.util.UUID;

/**
 * Actor IDs must originate from a verified principal, never a request field.
 */
public interface ActorAccess {
    void requireRole(UUID actorId, String role);

    void requireUser(UUID actorId);
}
