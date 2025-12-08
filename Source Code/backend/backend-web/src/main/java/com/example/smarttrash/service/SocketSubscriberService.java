package com.example.smarttrash.service;

import com.example.smarttrash.model.*;
import com.example.smarttrash.service.LiveDataService;
import com.example.smarttrash.repository.BinStatusRepository;
import com.example.smarttrash.repository.ClassificationLogRepository;
import com.example.smarttrash.repository.Esp32ImageRepository;
import com.example.smarttrash.repository.DeviceInfoRepository;
import com.example.smarttrash.repository.SettingsRepository;
import com.example.smarttrash.repository.Esp32EventLogRepository;
import com.example.smarttrash.repository.Esp32EventLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Subscribes to the IoT WebSocket server and persists incoming readings
 * so REST APIs can serve live data to the dashboard.
 */
@Slf4j
@Service
public class SocketSubscriberService {

    private final SettingsRepository settingsRepository;
    private final BinStatusRepository binStatusRepository;
    private final ClassificationLogRepository classificationLogRepository;
    private final DeviceInfoRepository deviceInfoRepository;
    private final Esp32ImageRepository esp32ImageRepository;
    private final Esp32EventLogRepository esp32EventLogRepository;
    private final LiveDataService liveDataService;
    private final ObjectMapper objectMapper;
    private final WebSocketClient webSocketClient;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicReference<String> currentTrashType = new AtomicReference<>("unknown");
    private final AtomicReference<Long> lastImageReceivedAt = new AtomicReference<>(0L);
    private final AtomicReference<String> lastImageFilename = new AtomicReference<>(null);
    // Lưu trữ mapping giữa thời gian và loại rác từ rotation events
    // Key: timestamp (millis), Value: trashType
    private final ConcurrentHashMap<Long, String> rotationEvents = new ConcurrentHashMap<>();

    @Value("${app.websocket.default-url:ws://localhost:4000/ws}")
    private String defaultSocketUrl;

    private volatile WebSocketSession session;

    public SocketSubscriberService(SettingsRepository settingsRepository,
                                   BinStatusRepository binStatusRepository,
                                   ClassificationLogRepository classificationLogRepository,
                                   DeviceInfoRepository deviceInfoRepository,
                                   Esp32ImageRepository esp32ImageRepository,
                                   Esp32EventLogRepository esp32EventLogRepository,
                                   LiveDataService liveDataService,
                                   ObjectMapper objectMapper) {
        this.settingsRepository = settingsRepository;
        this.binStatusRepository = binStatusRepository;
        this.classificationLogRepository = classificationLogRepository;
        this.deviceInfoRepository = deviceInfoRepository;
        this.esp32ImageRepository = esp32ImageRepository;
        this.esp32EventLogRepository = esp32EventLogRepository;
        this.liveDataService = liveDataService;
        this.objectMapper = objectMapper;
        this.webSocketClient = buildWebSocketClient();
    }

    private WebSocketClient buildWebSocketClient() {
        // Increase buffers to handle large base64 images safely
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(12 * 1024 * 1024);   // 12MB
        container.setDefaultMaxBinaryMessageBufferSize(12 * 1024 * 1024); // 12MB
        container.setAsyncSendTimeout(600_000L); // align with server
        container.setDefaultMaxSessionIdleTimeout(86_400_000L);
        return new StandardWebSocketClient(container);
    }

    @PostConstruct
    public void start() {
        connect();
    }

    @PreDestroy
    public void stop() {
        closeSession();
        reconnectExecutor.shutdownNow();
    }

    private void connect() {
        if (connecting.getAndSet(true)) {
            return;
        }

        String url = resolveSocketUrl();
        log.info("Connecting to IoT WebSocket at {}", url);

        webSocketClient.doHandshake(new SubscriberHandler(), url)
                .addCallback(result -> {
                    session = result;
                    connecting.set(false);
                    log.info("Connected to IoT WebSocket (session: {})", result.getId());
                }, ex -> {
                    connecting.set(false);
                    log.warn("Failed to connect to IoT WebSocket: {}. Retrying in 5s", ex.getMessage());
                    scheduleReconnect();
                });
    }

    private void scheduleReconnect() {
        reconnectExecutor.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    private String resolveSocketUrl() {
        Optional<SettingsEntity> settings = settingsRepository.findAll().stream().findFirst();
        return settings
                .map(SettingsEntity::getWebsocketUrl)
                .filter(StringUtils::hasText)
                .orElse(defaultSocketUrl);
    }

    private void closeSession() {
        WebSocketSession current = session;
        if (current != null && current.isOpen()) {
            try {
                current.close();
            } catch (IOException e) {
                log.warn("Failed to close WebSocket session: {}", e.getMessage());
            }
        }
    }

    private class SubscriberHandler extends AbstractWebSocketHandler {
        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText(null);
            if (type != null && !type.isEmpty()) {
                JsonNode payload = root.path("payload");
                switch (type) {
                    case "server:data" -> handleServerData(payload);
                    case "server:image" -> handleServerImage(payload);
                    default -> log.debug("Unhandled WebSocket message type: {}", type);
                }
            } else if (root.has("latestEsp32Data") || root.has("latestEsp32Image")) {
                // Direct snapshot payload from socket
                liveDataService.updateFromSocket(root);
                
                // Xử lý data TRƯỚC để cập nhật currentTrashType trước khi log image
                String trashTypeFromCurrentSnapshot = null; // Lưu loại rác từ snapshot hiện tại
                if (root.has("latestEsp32Data")) {
                    JsonNode dataNode = root.get("latestEsp32Data");
                    String dataValue = text(dataNode, "data");
                    // Chỉ xử lý nếu có rotation data (ROTATE_CW/ROTATE_CCW)
                    if ("ROTATE_CW".equals(dataValue) || "ROTATE_CCW".equals(dataValue)) {
                        String mappedTrashType = mapRotationToTrashType(dataValue);
                        if (mappedTrashType != null) {
                            trashTypeFromCurrentSnapshot = mappedTrashType; // Lưu loại rác từ snapshot này
                            currentTrashType.set(mappedTrashType);
                            liveDataService.updateTrashType(mappedTrashType);
                            Long dataReceivedAt = longVal(dataNode, "receivedAt");
                            if (dataReceivedAt == null) {
                                dataReceivedAt = System.currentTimeMillis();
                            }
                            persistRotationClassification(mappedTrashType, dataReceivedAt);
                            // Lưu rotation event để map với ảnh sau này
                            rotationEvents.put(dataReceivedAt, mappedTrashType);
                            // Giữ chỉ 100 events gần nhất để tránh memory leak
                            if (rotationEvents.size() > 100) {
                                Long oldestKey = rotationEvents.keySet().stream()
                                        .min(Long::compareTo)
                                        .orElse(null);
                                if (oldestKey != null) {
                                    rotationEvents.remove(oldestKey);
                                }
                            }
                        }
                        handleServerData(dataNode);
                    }
                }
                
                // Xử lý ảnh mới - chỉ log nếu là ảnh mới thực sự (khác filename hoặc receivedAt lớn hơn)
                if (root.has("latestEsp32Image")) {
                    JsonNode imgNode = root.get("latestEsp32Image");
                    Long receivedAt = longVal(imgNode, "receivedAt");
                    String filename = text(imgNode, "filename");
                    
                    // Chỉ xử lý nếu là ảnh mới (filename khác hoặc receivedAt lớn hơn)
                    boolean isNewImage = false;
                    if (filename != null && receivedAt != null) {
                        String lastFilename = lastImageFilename.get();
                        Long lastReceivedAt = lastImageReceivedAt.get();
                        
                        // Ảnh mới nếu filename khác hoặc receivedAt lớn hơn
                        if (!filename.equals(lastFilename) || (receivedAt > lastReceivedAt)) {
                            isNewImage = true;
                        }
                    } else if (receivedAt != null && receivedAt > lastImageReceivedAt.get()) {
                        isNewImage = true;
                    }
                    
                    if (isNewImage) {
                        persistImage(imgNode);
                        lastImageReceivedAt.set(receivedAt);
                        if (filename != null) {
                            lastImageFilename.set(filename);
                        }
                        // Nếu snapshot có cả data rotation và image cùng lúc, dùng trực tiếp loại rác từ data
                        // Nếu không, tìm trong rotationEvents
                        String trashTypeForImage;
                        if (trashTypeFromCurrentSnapshot != null) {
                            // Snapshot có rotation data mới, dùng trực tiếp (ưu tiên tuyệt đối)
                            trashTypeForImage = trashTypeFromCurrentSnapshot;
                            log.debug("Using trash type from current snapshot rotation data: {} for image: {} at {}", 
                                    trashTypeForImage, filename, receivedAt);
                        } else {
                            // Không có rotation data trong snapshot này, tìm trong rotationEvents
                            trashTypeForImage = findTrashTypeForImage(receivedAt);
                            log.debug("Using trash type from rotationEvents: {} for image: {} at {}", 
                                    trashTypeForImage, filename, receivedAt);
                        }
                        logEvent("IMAGE", trashTypeForImage, receivedAt, filename);
                    }
                }
                
                log.debug("Live snapshot updated from socket payload");
            } else {
                log.debug("Unhandled socket message without type");
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            log.info("IoT WebSocket closed: {} ({})", status.getCode(), status.getReason());
            scheduleReconnect();
        }
    }

    private void handleServerData(JsonNode payload) {
        JsonNode dataNode = payload.has("data") ? payload.get("data") : payload;
        long receivedAt = payload.path("receivedAt").asLong(System.currentTimeMillis());

        String dataValue = dataNode.isTextual() ? dataNode.asText() : dataNode.path("data").asText(null);
        String trashType = mapRotationToTrashType(dataValue);
        if (trashType != null) {
            currentTrashType.set(trashType);
            liveDataService.updateTrashType(trashType);
            persistRotationClassification(trashType, receivedAt);
            // Lưu rotation event để map với ảnh sau này
            rotationEvents.put(receivedAt, trashType);
            // Giữ chỉ 100 events gần nhất để tránh memory leak
            if (rotationEvents.size() > 100) {
                Long oldestKey = rotationEvents.keySet().stream()
                        .min(Long::compareTo)
                        .orElse(null);
                if (oldestKey != null) {
                    rotationEvents.remove(oldestKey);
                }
            }
        }

        updateBinStatus(dataNode, receivedAt);
        persistClassificationLog(dataNode, receivedAt);
        updateDeviceInfo(dataNode);
        logEvent("DATA", trashType != null ? trashType : currentTrashType.get(), receivedAt);
    }

    private void updateBinStatus(JsonNode dataNode, long receivedAtMillis) {
        Double organic = readDouble(dataNode, "organicLevel");
        Double inorganic = readDouble(dataNode, "inorganicLevel");

        if (organic == null && inorganic == null) {
            return;
        }

        BinStatus status = binStatusRepository.findAll().stream().findFirst().orElse(new BinStatus());
        if (organic != null) {
            status.setOrganicLevel(organic);
        }
        if (inorganic != null) {
            status.setInorganicLevel(inorganic);
        }
        status.setUpdatedAt(toLocalDateTime(receivedAtMillis));
        binStatusRepository.save(status);
    }

    private void persistClassificationLog(JsonNode dataNode, long receivedAtMillis) {
        JsonNode classification = dataNode.path("classification");
        if (classification.isMissingNode() || classification.isNull()) {
            return;
        }

        String rawType = classification.path("type").asText(null);
        Double confidence = readDouble(classification, "confidence");
        String status = classification.path("status").asText("success");

        if (!StringUtils.hasText(rawType)) {
            return;
        }

        ClassificationLog logEntry = new ClassificationLog();
        logEntry.setTimestamp(toLocalDateTime(receivedAtMillis));
        logEntry.setType(mapType(rawType));
        logEntry.setConfidence(confidence != null ? confidence : 0.0);
        logEntry.setStatus(StringUtils.hasText(status) ? status : "unknown");

        classificationLogRepository.save(logEntry);
    }

    private void updateDeviceInfo(JsonNode dataNode) {
        JsonNode device = dataNode.path("device");
        if (device.isMissingNode() || device.isNull()) {
            return;
        }

        DeviceInfoEntity entity = deviceInfoRepository.findAll().stream().findFirst().orElse(new DeviceInfoEntity());
        if (device.hasNonNull("model")) {
            entity.setModel(device.get("model").asText());
        }
        if (device.hasNonNull("firmware")) {
            entity.setFirmware(device.get("firmware").asText());
        }
        if (device.hasNonNull("ipAddress")) {
            entity.setIpAddress(device.get("ipAddress").asText());
        }
        if (device.hasNonNull("uptime")) {
            entity.setUptime(device.get("uptime").asText());
        }

        // Only save if we have at least one populated field
        if (StringUtils.hasText(entity.getModel())
                || StringUtils.hasText(entity.getFirmware())
                || StringUtils.hasText(entity.getIpAddress())
                || StringUtils.hasText(entity.getUptime())) {
            deviceInfoRepository.save(entity);
        }
    }

    private Double readDouble(JsonNode node, String field) {
        if (node.hasNonNull(field)) {
            return node.get(field).asDouble();
        }
        return null;
    }

    private String mapType(String raw) {
        if ("organic".equalsIgnoreCase(raw)) {
            return "Hữu cơ";
        }
        if ("inorganic".equalsIgnoreCase(raw) || "recyclable".equalsIgnoreCase(raw)) {
            return "Vô cơ";
        }
        return raw;
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private void handleServerImage(JsonNode payload) {
        String filename = text(payload, "filename");
        Long receivedAt = longVal(payload, "receivedAt");
        
        // Chỉ xử lý nếu là ảnh mới thực sự (filename khác hoặc receivedAt lớn hơn)
        boolean isNewImage = false;
        if (filename != null && receivedAt != null) {
            String lastFilename = lastImageFilename.get();
            Long lastReceivedAt = lastImageReceivedAt.get();
            
            // Ảnh mới nếu filename khác hoặc receivedAt lớn hơn
            if (!filename.equals(lastFilename) || (receivedAt > lastReceivedAt)) {
                isNewImage = true;
            }
        } else if (receivedAt != null && receivedAt > lastImageReceivedAt.get()) {
            isNewImage = true;
        }
        
        if (isNewImage) {
            persistImage(payload);
            lastImageReceivedAt.set(receivedAt);
            if (filename != null) {
                lastImageFilename.set(filename);
            }
            liveDataService.updateFromSocket(objectMapper.createObjectNode().set("latestEsp32Image", payload));
            
            // Tìm loại rác từ rotation event gần nhất (trong vòng 10 giây trước khi nhận ảnh)
            String trashTypeForImage = findTrashTypeForImage(receivedAt);
            logEvent("IMAGE", trashTypeForImage, receivedAt, filename);
        }
    }
    
    /**
     * Tìm loại rác từ rotation event gần nhất với thời điểm nhận ảnh
     * Tìm trong khoảng 10 giây trước khi nhận ảnh
     * Ưu tiên rotation event có timestamp gần nhất với imageReceivedAt
     */
    private String findTrashTypeForImage(Long imageReceivedAt) {
        if (imageReceivedAt == null) {
            return currentTrashType.get();
        }
        
        // Tìm rotation event gần nhất trong vòng 10 giây trước khi nhận ảnh
        // Hoặc trong vòng 2 giây sau khi nhận ảnh (để bao gồm cả trường hợp rotation đến sau ảnh một chút)
        long timeWindowBefore = 10_000L; // 10 giây trước
        long timeWindowAfter = 2_000L; // 2 giây sau
        long minTime = imageReceivedAt - timeWindowBefore;
        long maxTime = imageReceivedAt + timeWindowAfter;
        
        return rotationEvents.entrySet().stream()
                .filter(entry -> entry.getKey() >= minTime && entry.getKey() <= maxTime)
                .max((e1, e2) -> {
                    // Ưu tiên rotation event có timestamp gần nhất với imageReceivedAt
                    // Ưu tiên rotation event trước ảnh hơn sau ảnh
                    long diff1 = Math.abs(e1.getKey() - imageReceivedAt);
                    long diff2 = Math.abs(e2.getKey() - imageReceivedAt);
                    if (diff1 != diff2) {
                        return Long.compare(diff2, diff1); // Lấy event gần nhất
                    }
                    // Nếu cùng khoảng cách, ưu tiên event trước ảnh
                    if (e1.getKey() <= imageReceivedAt && e2.getKey() > imageReceivedAt) {
                        return 1;
                    }
                    if (e1.getKey() > imageReceivedAt && e2.getKey() <= imageReceivedAt) {
                        return -1;
                    }
                    return Long.compare(e2.getKey(), e1.getKey()); // Lấy event mới nhất
                })
                .map(entry -> entry.getValue())
                .orElseGet(() -> {
                    // Nếu không tìm thấy trong time window, dùng currentTrashType
                    String current = currentTrashType.get();
                    return "unknown".equals(current) ? "unknown" : current;
                });
    }

    private void persistImage(JsonNode img) {
        if (img == null || img.isMissingNode()) {
            return;
        }
        String filename = text(img, "filename");
        String contentType = text(img, "contentType");
        String data = text(img, "data");
        Integer size = intVal(img, "size");
        Long receivedAt = longVal(img, "receivedAt");

        if (!StringUtils.hasText(filename) || !StringUtils.hasText(contentType) || !StringUtils.hasText(data)) {
            log.warn("Skipping image persistence due to missing filename/contentType/data");
            return;
        }

        try {
            Esp32ImageEntity entity = new Esp32ImageEntity();
            entity.setFilename(filename);
            entity.setContentType(contentType);
            entity.setData(data);
            entity.setSize(size != null ? size : data.length());
            entity.setReceivedAt(receivedAt != null ? receivedAt : System.currentTimeMillis());
            esp32ImageRepository.save(entity);
            log.info("Stored ESP32 image: {}", filename);
        } catch (Exception e) {
            log.error("Failed to persist ESP32 image: {}", e.getMessage(), e);
        }
    }

    private String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private Integer intVal(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private Long longVal(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asLong() : null;
    }

    private void logEvent(String eventType, String trashType, Long receivedAt) {
        logEvent(eventType, trashType, receivedAt, null);
    }

    private void logEvent(String eventType, String trashType, Long receivedAt, String filename) {
        try {
            // Kiểm tra xem đã log event này chưa (để tránh duplicate)
            // Với IMAGE event, kiểm tra filename và receivedAt
            if ("IMAGE".equals(eventType) && filename != null && receivedAt != null) {
                // Kiểm tra xem đã có event với cùng filename và receivedAt chưa
                List<Esp32EventLog> existing = esp32EventLogRepository.findAll().stream()
                        .filter(log -> "IMAGE".equals(log.getEventType())
                                && filename.equals(log.getFilename())
                                && receivedAt.equals(log.getReceivedAt()))
                        .toList();
                if (!existing.isEmpty()) {
                    log.debug("Skipping duplicate IMAGE event for filename: {} at {}", filename, receivedAt);
                    return;
                }
            }
            
            Esp32EventLog logEntry = new Esp32EventLog();
            logEntry.setEventType(eventType);
            logEntry.setTrashType(StringUtils.hasText(trashType) ? trashType : "unknown");
            logEntry.setReceivedAt(receivedAt != null ? receivedAt : System.currentTimeMillis());
            logEntry.setFilename(filename);
            esp32EventLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to log ESP32 event: {}", e.getMessage(), e);
        }
    }

    private String mapRotationToTrashType(String dataValue) {
        if (dataValue == null) return null;
        return switch (dataValue) {
            case "ROTATE_CW" -> "inorganic";
            case "ROTATE_CCW" -> "organic";
            default -> null;
        };
    }

    private void persistRotationClassification(String trashType, long receivedAt) {
        try {
            ClassificationLog logEntry = new ClassificationLog();
            logEntry.setTimestamp(toLocalDateTime(receivedAt));
            logEntry.setType("organic".equals(trashType) ? "Hữu cơ" : "Vô cơ");
            logEntry.setConfidence(0.0);
            logEntry.setStatus("ws");
            classificationLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to persist rotation classification: {}", e.getMessage());
        }
    }
}

