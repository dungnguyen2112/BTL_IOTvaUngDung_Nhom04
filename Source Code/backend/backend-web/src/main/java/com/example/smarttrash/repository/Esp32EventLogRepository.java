package com.example.smarttrash.repository;

import com.example.smarttrash.model.Esp32EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface Esp32EventLogRepository extends JpaRepository<Esp32EventLog, Long> {
    List<Esp32EventLog> findTop50ByOrderByReceivedAtDesc();
    
    @Query(value = "SELECT * FROM esp32_event_logs WHERE filename IS NOT NULL ORDER BY received_at DESC LIMIT 50", nativeQuery = true)
    List<Esp32EventLog> findTop50ByFilenameNotNullOrderByReceivedAtDesc();
}

