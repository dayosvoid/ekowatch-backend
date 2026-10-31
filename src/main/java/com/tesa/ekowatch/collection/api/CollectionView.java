package com.tesa.ekowatch.collection.api;
import java.time.*;
import java.util.UUID;
public record CollectionView(UUID collectionId, UUID streetId, LocalDate collectionDate,
                             Instant completedAt, CollectionStatus collectionStatus) {}