package com.example.smarttrash.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bin_status")
public class BinStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private double organicLevel;

    @Column(nullable = false)
    private double inorganicLevel;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double getOrganicLevel() {
        return organicLevel;
    }

    public void setOrganicLevel(double organicLevel) {
        this.organicLevel = organicLevel;
    }

    public double getInorganicLevel() {
        return inorganicLevel;
    }

    public void setInorganicLevel(double inorganicLevel) {
        this.inorganicLevel = inorganicLevel;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


