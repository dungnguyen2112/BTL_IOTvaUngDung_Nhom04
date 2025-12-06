package com.iot.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.websocket.model.Esp32Data;
import com.iot.websocket.model.Esp32Image;
import com.iot.websocket.model.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class IoTWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Topic subscriptions for targeted messaging
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> topicSubscriptions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Set<String>> sessionTopics = new ConcurrentHashMap<>();
    
    private volatile Esp32Data latestEsp32Data = null;
    private volatile Esp32Image latestEsp32Image = null;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        String remoteAddress = session.getRemoteAddress() != null 
                ? session.getRemoteAddress().toString() 
                : "unknown";
        
        log.info("WebSocket connected: {} (Session ID: {})", remoteAddress, session.getId());
        log.debug("Total active sessions: {}", sessions.size());

        // Send latest data to new connection for fast sync
        if (latestEsp32Data != null) {
            WebSocketMessage message = new WebSocketMessage("server:data", latestEsp32Data);
            sendMessage(session, message);
            log.debug("Sent latest ESP32 data to new connection: {}", session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        
        log.debug("Received TEXT message from session {}: {} bytes", session.getId(), payload.length());
        
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : null;
            JsonNode payloadNode = jsonNode.has("payload") ? jsonNode.get("payload") : null;

            if (type == null) {
                log.warn("Missing 'type' field in message from session: {}", session.getId());
                WebSocketMessage errorMsg = new WebSocketMessage("server:error", "Missing 'type' field");
                sendMessage(session, errorMsg);
                return;
            }

            log.info("Processing message type: {} from session: {}", type, session.getId());

            switch (type) {
                case "esp32:data":
                    handleEsp32Data(session, payloadNode);
                    break;
                case "esp32:image":
                    handleEsp32ImageWithTopic(session, payloadNode, payload);
                    break;
                case "ai:classify:result":
                    handleAIResult(session, payloadNode, payload);
                    break;
                case "client:subscribe":
                    handleSubscribe(session, payloadNode);
                    break;
                case "client:unsubscribe":
                    handleUnsubscribe(session, payloadNode);
                    break;
                case "esp32:ping":
                    handlePing(session);
                    break;
                default:
                    log.warn("Unknown message type: {} from session: {}", type, session.getId());
            }
        } catch (Exception e) {
            log.error("Failed to parse message from session {}: {}", session.getId(), e.getMessage(), e);
            log.debug("Raw message: {}", payload.substring(0, Math.min(200, payload.length())));
            WebSocketMessage errorMsg = new WebSocketMessage("server:error", "Invalid JSON payload: " + e.getMessage());
            sendMessage(session, errorMsg);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        byte[] payload = message.getPayload().array();
        log.info("Received BINARY message from session {}: {} bytes", session.getId(), payload.length);
        
        // Binary message support - có thể dùng cho hình ảnh lớn từ ESP32
        // Hiện tại chỉ log, bạn có thể xử lý thêm nếu cần
        log.warn("Binary message handling not implemented yet");
    }

    private void handleEsp32Data(WebSocketSession senderSession, JsonNode payloadNode) throws IOException {
        Esp32Data data = new Esp32Data();
        data.setData(payloadNode);
        data.setReceivedAt(System.currentTimeMillis());
        
        latestEsp32Data = data;
        
        log.info("ESP32 data received: {}", objectMapper.writeValueAsString(data));
        
        // Broadcast to all clients except sender
        WebSocketMessage broadcastMsg = new WebSocketMessage("server:data", data);
        broadcast(broadcastMsg, senderSession);
    }

    private void handleEsp32ImageWithTopic(WebSocketSession senderSession, JsonNode payloadNode, String fullPayload) throws IOException {
        String filename = payloadNode.has("filename") ? payloadNode.get("filename").asText() : null;
        String contentType = payloadNode.has("contentType") ? payloadNode.get("contentType").asText() : null;
        String data = payloadNode.has("data") ? payloadNode.get("data").asText() : null;

        if (filename == null || contentType == null || data == null) {
            log.warn("Missing filename/contentType/data for esp32:image from session: {}", senderSession.getId());
            WebSocketMessage errorMsg = new WebSocketMessage("server:error", 
                    "Missing filename/contentType/data for esp32:image");
            sendMessage(senderSession, errorMsg);
            return;
        }

        int size = Base64.getDecoder().decode(data).length;
        
        Esp32Image image = new Esp32Image();
        image.setFilename(filename);
        image.setContentType(contentType);
        image.setData(data);
        image.setReceivedAt(System.currentTimeMillis());
        image.setSize(size);
        
        latestEsp32Image = image;
        
        log.info("ESP32 image received: {} ({} bytes)", filename, size);

        // Send acknowledgment to ESP32
        Map<String, Object> ackPayload = new HashMap<>();
        ackPayload.put("filename", filename);
        ackPayload.put("receivedAt", image.getReceivedAt());
        ackPayload.put("size", size);
        WebSocketMessage ackMsg = new WebSocketMessage("server:image:ack", ackPayload);
        sendMessage(senderSession, ackMsg);

        // Broadcast to image topic (Backend AI subscribes here)
        broadcastToTopic("image", fullPayload, senderSession);
        log.info("Image forwarded to image topic");
    }
    
    // Handle AI classification result
    private void handleAIResult(WebSocketSession senderSession, JsonNode payloadNode, String fullPayload) throws IOException {
        String classification = payloadNode.has("class") ? payloadNode.get("class").asText() : "unknown";
        double confidence = payloadNode.has("confidence") ? payloadNode.get("confidence").asDouble() : 0.0;
        String motorAction = payloadNode.has("motorAction") ? payloadNode.get("motorAction").asText() : "none";
        
        log.info("AI Result received: class={}, confidence={}, motorAction={}", classification, confidence, motorAction);
        
        // Send result to ESP32 via command topic
        broadcastToTopic("command", fullPayload, senderSession);
        log.info("Control command sent to ESP32");
        
        // Also broadcast to dashboard for display
        broadcastToTopic("dashboard/updates", fullPayload, senderSession);
    }
    
    // Subscribe to topic
    private void handleSubscribe(WebSocketSession session, JsonNode payloadNode) throws IOException {
        String topic = payloadNode.has("topic") ? payloadNode.get("topic").asText() : null;
        
        if (topic == null || topic.isEmpty()) {
            WebSocketMessage errorMsg = new WebSocketMessage("server:error", "Missing topic for subscription");
            sendMessage(session, errorMsg);
            return;
        }
        
        topicSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionTopics.computeIfAbsent(session, k -> ConcurrentHashMap.newKeySet()).add(topic);
        
        log.info("Session {} subscribed to topic: {}", session.getId(), topic);
        
        Map<String, Object> response = new HashMap<>();
        response.put("topic", topic);
        response.put("message", "Successfully subscribed to " + topic);
        WebSocketMessage msg = new WebSocketMessage("server:subscribed", response);
        sendMessage(session, msg);
    }
    
    // Unsubscribe from topic
    private void handleUnsubscribe(WebSocketSession session, JsonNode payloadNode) throws IOException {
        String topic = payloadNode.has("topic") ? payloadNode.get("topic").asText() : null;
        
        if (topic != null) {
            CopyOnWriteArraySet<WebSocketSession> subscribers = topicSubscriptions.get(topic);
            if (subscribers != null) {
                subscribers.remove(session);
            }
            
            Set<String> topics = sessionTopics.get(session);
            if (topics != null) {
                topics.remove(topic);
            }
            
            log.info("Session {} unsubscribed from topic: {}", session.getId(), topic);
            
            Map<String, Object> response = new HashMap<>();
            response.put("topic", topic);
            response.put("message", "Successfully unsubscribed from " + topic);
            WebSocketMessage msg = new WebSocketMessage("server:unsubscribed", response);
            sendMessage(session, msg);
        }
    }
    
    // Broadcast to specific topic
    private void broadcastToTopic(String topic, String message, WebSocketSession exclude) {
        CopyOnWriteArraySet<WebSocketSession> subscribers = topicSubscriptions.get(topic);
        
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for topic: {}", topic);
            return;
        }
        
        int sentCount = 0;
        int failCount = 0;
        
        for (WebSocketSession session : subscribers) {
            if (session.equals(exclude) || !session.isOpen()) {
                continue;
            }
            
            try {
                session.sendMessage(new TextMessage(message));
                sentCount++;
            } catch (IOException e) {
                failCount++;
                log.error("Failed to send to session {}: {}", session.getId(), e.getMessage());
            }
        }
        
        log.debug("Broadcasted to topic '{}': {} sent, {} failed", topic, sentCount, failCount);
    }

    private void handlePing(WebSocketSession session) throws IOException {
        log.debug("Ping received from session: {}", session.getId());
        WebSocketMessage pongMsg = new WebSocketMessage("server:pong", System.currentTimeMillis());
        sendMessage(session, pongMsg);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        
        // Cleanup topic subscriptions
        Set<String> topics = sessionTopics.remove(session);
        if (topics != null) {
            for (String topic : topics) {
                CopyOnWriteArraySet<WebSocketSession> subscribers = topicSubscriptions.get(topic);
                if (subscribers != null) {
                    subscribers.remove(session);
                    if (subscribers.isEmpty()) {
                        topicSubscriptions.remove(topic);
                    }
                }
            }
        }
        
        String remoteAddress = session.getRemoteAddress() != null 
                ? session.getRemoteAddress().toString() 
                : "unknown";
        
        log.info("WebSocket closed: {} (Session ID: {}) - Code: {}, Reason: {}", 
                 remoteAddress, session.getId(), status.getCode(), status.getReason());
        log.debug("Total active sessions: {}", sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket error for session {}: {}", session.getId(), exception.getMessage(), exception);
    }

    private void broadcast(WebSocketMessage message, WebSocketSession exceptSession) {
        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("Failed to serialize broadcast message", e);
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        for (WebSocketSession session : sessions) {
            if (session.equals(exceptSession) || !session.isOpen()) {
                continue;
            }

            try {
                session.sendMessage(new TextMessage(messageJson));
                successCount++;
            } catch (IOException e) {
                failureCount++;
                log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            }
        }

        log.debug("Broadcast completed: {} successful, {} failed", successCount, failureCount);
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message) throws IOException {
        if (session.isOpen()) {
            String messageJson = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(messageJson));
        } else {
            log.warn("Attempted to send message to closed session: {}", session.getId());
        }
    }

    // Getters for health endpoint
    public Esp32Data getLatestEsp32Data() {
        return latestEsp32Data;
    }

    public Esp32Image getLatestEsp32Image() {
        return latestEsp32Image;
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}
