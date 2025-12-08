package com.example.smarttrash.model;

public record LiveEsp32Image(
        String filename,
        Integer size,
        Long receivedAt,
        String contentType,
        String data
) {
}

