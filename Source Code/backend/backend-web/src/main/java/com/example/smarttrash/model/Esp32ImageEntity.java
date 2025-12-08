package com.example.smarttrash.model;

import jakarta.persistence.*;

@Entity
@Table(name = "esp32_images")
public class Esp32ImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255, nullable = false)
    private String filename;

    @Column(length = 100, nullable = false)
    private String contentType;

    @Lob
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String data; // Base64 encoded

    @Column(nullable = false)
    private Integer size;

    @Column(nullable = false)
    private Long receivedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Long receivedAt) {
        this.receivedAt = receivedAt;
    }
}

