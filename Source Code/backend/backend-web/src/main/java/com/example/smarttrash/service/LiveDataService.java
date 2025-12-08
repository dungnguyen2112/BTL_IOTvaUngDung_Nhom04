package com.example.smarttrash.service;

import com.example.smarttrash.model.LiveEsp32Data;
import com.example.smarttrash.model.LiveEsp32Image;
import com.example.smarttrash.model.LiveSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class LiveDataService {

    private final AtomicReference<LiveSnapshot> snapshotRef =
            new AtomicReference<>(new LiveSnapshot(null, null, 0, "unknown", null, "unknown", null));

    public LiveSnapshot getSnapshot() {
        return snapshotRef.get();
    }

    public void updateFromSocket(JsonNode root) {
        LiveEsp32Image image = null;
        if (root.hasNonNull("latestEsp32Image")) {
            JsonNode img = root.get("latestEsp32Image");
            image = new LiveEsp32Image(
                    text(img, "filename"),
                    intVal(img, "size"),
                    longVal(img, "receivedAt"),
                    text(img, "contentType"),
                    text(img, "data")
            );
        }

        LiveEsp32Data data = null;
        String binType = null;
        if (root.hasNonNull("latestEsp32Data")) {
            JsonNode d = root.get("latestEsp32Data");
            binType = text(d, "binType");
            data = new LiveEsp32Data(
                    text(d, "data"),
                    longVal(d, "receivedAt"),
                    binType
            );
        }

        Integer activeConnections = intVal(root, "activeConnections");
        String status = text(root, "status");
        Long timestamp = longVal(root, "timestamp");
        String trashType = text(root, "trashType");

        snapshotRef.set(new LiveSnapshot(
                image,
                data,
                activeConnections != null ? activeConnections : 0,
                status != null ? status : "unknown",
                timestamp,
                trashType != null ? trashType : currentTrashType(),
                binType
        ));
    }

    public void updateTrashType(String trashType) {
        snapshotRef.getAndUpdate(prev -> new LiveSnapshot(
                prev.latestEsp32Image(),
                prev.latestEsp32Data(),
                prev.activeConnections(),
                prev.status(),
                prev.timestamp(),
                trashType != null ? trashType : prev.trashType(),
                prev.binType()
        ));
    }

    public String currentTrashType() {
        return snapshotRef.get().trashType();
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private Integer intVal(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private Long longVal(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asLong() : null;
    }
}

