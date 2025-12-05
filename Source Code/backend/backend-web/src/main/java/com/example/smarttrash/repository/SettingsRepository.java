package com.example.smarttrash.repository;

import com.example.smarttrash.model.SettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<SettingsEntity, Long> {
}


