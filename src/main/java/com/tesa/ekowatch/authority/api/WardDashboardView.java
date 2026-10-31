package com.tesa.ekowatch.authority.api;
import java.time.*;
import java.util.*;
public record WardDashboardView(UUID wardId, LocalDate fromDate, LocalDate toDate, Instant generatedAt,
        long missedPickupCount, long disputedPickupCount, long blockedDrainCount, long currentFloodCount,
        Instant newestReportAt, List<OperatorPerformanceView> operatorPerformance) {
    public WardDashboardView { operatorPerformance=List.copyOf(operatorPerformance); }
}
