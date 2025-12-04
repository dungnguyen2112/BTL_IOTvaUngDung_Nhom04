package com.example.smarttrash.model;

import java.time.LocalDateTime;

public record LogEntry(
        LocalDateTime timestamp,
        String type,       // "Hữu cơ" or "Vô cơ"
        double confidence,
        String status      // "success", "error", etc.
) {
}


