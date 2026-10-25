package com.tesa.ekowatch.confirmation.api;

import java.util.UUID;

public interface ConfirmationApi {
    void confirm(UUID actorId, UUID reportId);
}
