package com.tesa.ekowatch.user.api;

import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * Actor IDs must originate from a verified principal, never a request field.
 */
public interface ActorAccess {

    UUID id(Authentication authentication);

    void requireRole(UUID actorId, String role);

    void requireUser(UUID actorId);
}