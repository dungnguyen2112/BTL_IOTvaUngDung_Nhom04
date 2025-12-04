package com.example.smarttrash.model;

import java.time.LocalDateTime;

public record DashboardOverview(
        long totalClassifications,
        double averageAccuracy,
        int todayCount,
        int alertCount,
        String alertMessage,
        double organicLevel,
        double inorganicLevel,
        LastClassification lastClassification,
        LocalDateTime serverTime
) {
}


