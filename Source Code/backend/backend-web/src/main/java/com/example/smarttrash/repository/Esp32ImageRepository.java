package com.example.smarttrash.repository;

import com.example.smarttrash.model.Esp32ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Esp32ImageRepository extends JpaRepository<Esp32ImageEntity, Long> {

    Esp32ImageEntity findTopByOrderByReceivedAtDesc();
}

