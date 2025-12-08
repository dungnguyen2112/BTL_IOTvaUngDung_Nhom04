package com.example.smarttrash.model;

import jakarta.persistence.*;

@Entity
@Table(name = "esp32_event_logs")
public class Esp32EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String eventType; // IMAGE | DATA

    @Column(length = 20, nullable = false)
    private String trashType; // organic | inorganic | unknown

    @Column(length = 255)
    private String filename; // filename for IMAGE events

    @Column(nullable = false)
    private Long receivedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTrashType() {
        return trashType;
    }

    public void setTrashType(String trashType) {
        this.trashType = trashType;
    }

    public Long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Long receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}

