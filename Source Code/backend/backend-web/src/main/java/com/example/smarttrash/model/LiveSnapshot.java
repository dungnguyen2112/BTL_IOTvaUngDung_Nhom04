package com.example.smarttrash.model;

public record LiveSnapshot(
        LiveEsp32Image latestEsp32Image,
        LiveEsp32Data latestEsp32Data,
        Integer activeConnections,
        String status,
        Long timestamp,
        String trashType,
        String binType
) {
}

