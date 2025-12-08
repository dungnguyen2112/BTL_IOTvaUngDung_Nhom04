package com.example.smarttrash.model;

public record LiveEsp32Data(
        String data,
        Long receivedAt,
        String binType
) {
}

