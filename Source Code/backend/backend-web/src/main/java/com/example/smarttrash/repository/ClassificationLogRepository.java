package com.example.smarttrash.repository;

import com.example.smarttrash.model.ClassificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ClassificationLogRepository extends JpaRepository<ClassificationLog, Long> {

    List<ClassificationLog> findTop20ByOrderByTimestampDesc();

    long countByTimestampAfter(LocalDateTime after);

    @Query("select avg(c.confidence) from ClassificationLog c")
    Double findAverageConfidence();
}


