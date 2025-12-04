package com.example.smarttrash.model;

public record LastClassification(
        String type,          // "organic" or "inorganic"
        double confidence,    // percentage
        String time           // formatted time string
) {
}


