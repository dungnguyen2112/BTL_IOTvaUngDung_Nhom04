package com.example.smarttrash.controller;

import com.example.smarttrash.model.DashboardOverview;
import com.example.smarttrash.model.DeviceInfo;
import com.example.smarttrash.model.LogEntry;
import com.example.smarttrash.model.Settings;
import com.example.smarttrash.service.DashboardService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public DashboardOverview getOverview() {
        return dashboardService.getOverview();
    }

    @GetMapping("/logs")
    public List<LogEntry> getLogs() {
        return dashboardService.getRecentLogs();
    }

    @GetMapping("/settings")
    public Settings getSettings() {
        return dashboardService.getSettings();
    }

    @PutMapping("/settings")
    public Settings updateSettings(@RequestBody Settings updated) {
        return dashboardService.updateSettings(updated);
    }

    @GetMapping("/device")
    public DeviceInfo getDeviceInfo() {
        return dashboardService.getDeviceInfo();
    }
}


