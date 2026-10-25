package com.tesa.ekowatch.collection.api;

import java.time.LocalDate;
import java.util.*;

public interface CollectionApi {
    void recordPickup(UUID actorId, UUID streetId, LocalDate date, String status);

    List<MissedPickup> missed(UUID authorityId, LocalDate from, LocalDate through);

    record MissedPickup(UUID streetId, LocalDate date) {
    }
}
