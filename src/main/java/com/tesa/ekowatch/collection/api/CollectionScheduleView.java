package com.tesa.ekowatch.collection.api;
import java.time.*;
import java.util.UUID;
public record CollectionScheduleView(UUID scheduleId, UUID wardId, UUID streetId, String streetName,
        LocalDate collectionDate, CollectionStatus collectionStatus, Instant completedAt, long missedReportCount) {}