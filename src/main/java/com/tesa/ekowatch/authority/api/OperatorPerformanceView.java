package com.tesa.ekowatch.authority.api;
import java.util.UUID;
public record OperatorPerformanceView(UUID operatorId, String operatorName, long scheduledPickupCount,
        long completedPickupCount, long missedPickupCount, long disputedPickupCount) {}
