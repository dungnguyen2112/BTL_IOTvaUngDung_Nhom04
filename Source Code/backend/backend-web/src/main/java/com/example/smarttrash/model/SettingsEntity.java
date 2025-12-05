package com.example.smarttrash.model;

import jakarta.persistence.*;

@Entity
@Table(name = "settings")
public class SettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int fullThresholdPercent;

    @Column(nullable = false)
    private int minConfidencePercent;

    @Column(nullable = false, length = 200)
    private String websocketUrl;

    @Column(nullable = false)
    private boolean autoEmptyEnabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getFullThresholdPercent() {
        return fullThresholdPercent;
    }

    public void setFullThresholdPercent(int fullThresholdPercent) {
        this.fullThresholdPercent = fullThresholdPercent;
    }

    public int getMinConfidencePercent() {
        return minConfidencePercent;
    }

    public void setMinConfidencePercent(int minConfidencePercent) {
        this.minConfidencePercent = minConfidencePercent;
    }

    public String getWebsocketUrl() {
        return websocketUrl;
    }

    public void setWebsocketUrl(String websocketUrl) {
        this.websocketUrl = websocketUrl;
    }

    public boolean isAutoEmptyEnabled() {
        return autoEmptyEnabled;
    }

    public void setAutoEmptyEnabled(boolean autoEmptyEnabled) {
        this.autoEmptyEnabled = autoEmptyEnabled;
    }
}


