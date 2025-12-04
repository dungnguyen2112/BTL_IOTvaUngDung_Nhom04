package com.example.smarttrash.model;

public record Settings(
        int fullThresholdPercent,
        int minConfidencePercent,
        String websocketUrl,
        boolean autoEmptyEnabled
) {
}


