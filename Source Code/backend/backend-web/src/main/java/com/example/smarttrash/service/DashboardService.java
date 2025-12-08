package com.example.smarttrash.service;

import com.example.smarttrash.model.*;
import com.example.smarttrash.repository.BinStatusRepository;
import com.example.smarttrash.repository.ClassificationLogRepository;
import com.example.smarttrash.repository.DeviceInfoRepository;
import com.example.smarttrash.repository.SettingsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DashboardService {

    private final ClassificationLogRepository classificationLogRepository;
    private final BinStatusRepository binStatusRepository;
    private final SettingsRepository settingsRepository;
    private final DeviceInfoRepository deviceInfoRepository;

    public DashboardService(ClassificationLogRepository classificationLogRepository,
                            BinStatusRepository binStatusRepository,
                            SettingsRepository settingsRepository,
                            DeviceInfoRepository deviceInfoRepository) {
        this.classificationLogRepository = classificationLogRepository;
        this.binStatusRepository = binStatusRepository;
        this.settingsRepository = settingsRepository;
        this.deviceInfoRepository = deviceInfoRepository;
    }

    public DashboardOverview getOverview() {
        long totalCount = classificationLogRepository.count();
        Double avgConf = classificationLogRepository.findAverageConfidence();
        long todayCount = classificationLogRepository.countByTimestampAfter(LocalDate.now().atStartOfDay());

        BinStatus binStatus = binStatusRepository.findAll().stream().findFirst().orElse(null);
        double organicLevel = binStatus != null ? binStatus.getOrganicLevel() : 0.0;
        double inorganicLevel = binStatus != null ? binStatus.getInorganicLevel() : 0.0;

        List<ClassificationLog> latestLogs = classificationLogRepository.findTop20ByOrderByTimestampDesc();
        ClassificationLog lastLog = latestLogs.isEmpty() ? null : latestLogs.get(0);

        LastClassification lastClassification = lastLog != null
                ? new LastClassification(
                "Hữu cơ".equals(lastLog.getType()) ? "organic" : "inorganic",
                lastLog.getConfidence(),
                lastLog.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        )
                : new LastClassification("organic", 0, "-");

        String alertMessage = organicLevel > 90 || inorganicLevel > 90
                ? "Ngăn đầy"
                : "Ổn định";

        int alertCount = (organicLevel > 90 || inorganicLevel > 90) ? 2 : 0;

        return new DashboardOverview(
                totalCount,
                avgConf != null ? Math.round(avgConf * 10.0) / 10.0 : 0.0,
                (int) todayCount,
                alertCount,
                alertMessage,
                organicLevel,
                inorganicLevel,
                lastClassification,
                LocalDateTime.now()
        );
    }

    public List<LogEntry> getRecentLogs() {
        List<ClassificationLog> logs = classificationLogRepository.findTop20ByOrderByTimestampDesc();
        return logs.stream()
                .map(l -> new LogEntry(l.getTimestamp(), l.getType(), l.getConfidence(), l.getStatus()))
                .toList();
    }

    public Settings getSettings() {
        SettingsEntity entity = settingsRepository.findAll().stream().findFirst().orElse(null);
        if (entity == null) {
            return new Settings(75, 85, "wss://ntdung.systems/ws\n", false);
        }
        return new Settings(
                entity.getFullThresholdPercent(),
                entity.getMinConfidencePercent(),
                entity.getWebsocketUrl(),
                entity.isAutoEmptyEnabled()
        );
    }

    public Settings updateSettings(Settings updated) {
        SettingsEntity entity = settingsRepository.findAll().stream().findFirst().orElse(null);
        if (entity == null) {
            entity = new SettingsEntity();
        }
        entity.setFullThresholdPercent(updated.fullThresholdPercent());
        entity.setMinConfidencePercent(updated.minConfidencePercent());
        entity.setWebsocketUrl(updated.websocketUrl());
        entity.setAutoEmptyEnabled(updated.autoEmptyEnabled());
        settingsRepository.save(entity);
        return updated;
    }

    public DeviceInfo getDeviceInfo() {
        DeviceInfoEntity entity = deviceInfoRepository.findAll().stream().findFirst().orElse(null);
        if (entity == null) {
            return new DeviceInfo(
                    "ESP32-CAM",
                    "v1.2.3",
                    "192.168.1.100",
                    "3d 12h 45m"
            );
        }
        return new DeviceInfo(
                entity.getModel(),
                entity.getFirmware(),
                entity.getIpAddress(),
                entity.getUptime()
        );
    }
}

