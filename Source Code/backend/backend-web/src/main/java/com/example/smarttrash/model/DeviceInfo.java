package com.example.smarttrash.model;

public record DeviceInfo(
        String model,
        String firmware,
        String ipAddress,
        String uptime
) {
}


