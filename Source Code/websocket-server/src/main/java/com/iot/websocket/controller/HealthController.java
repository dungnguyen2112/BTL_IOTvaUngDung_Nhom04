package com.iot.websocket.controller;

import com.iot.websocket.handler.IoTWebSocketHandler;
import com.iot.websocket.model.Esp32Data;
import com.iot.websocket.model.Esp32Image;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
public class HealthController {

    private final IoTWebSocketHandler webSocketHandler;

    public HealthController(IoTWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("Health check requested");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", System.currentTimeMillis());
        response.put("activeConnections", webSocketHandler.getActiveSessionCount());
        
        Esp32Data latestData = webSocketHandler.getLatestEsp32Data();
        response.put("latestEsp32Data", latestData);
        
        Esp32Image latestImage = webSocketHandler.getLatestEsp32Image();
        if (latestImage != null) {
            Map<String, Object> imageInfo = new HashMap<>();
            imageInfo.put("filename", latestImage.getFilename());
            imageInfo.put("contentType", latestImage.getContentType());
            imageInfo.put("receivedAt", latestImage.getReceivedAt());
            imageInfo.put("size", latestImage.getSize());
            response.put("latestEsp32Image", imageInfo);
        } else {
            response.put("latestEsp32Image", null);
        }
        
        return ResponseEntity.ok(response);
    }
}
