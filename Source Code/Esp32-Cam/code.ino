#include <WiFi.h>
#include <WebSocketsClient.h>
#include "esp_camera.h"
#include <time.h>
#include <ESP32Servo.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <ArduinoJson.h>

#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ==========================
// Servo & LCD Config
// ==========================
const int SERVO_PIN = 12;                       // CHÂN PWM cho servo điều khiển nắp
const int SERVO_CENTER_ANGLE    = 90;          // vị trí nắp ở giữa (đóng)
const int SERVO_ORGANIC_ANGLE   = 15;          // vị trí nghiêng về thùng hữu cơ
const int SERVO_INORGANIC_ANGLE = 165;         // vị trí nghiêng về thùng vô cơ
const unsigned long SERVO_HOLD_MS = 1500;      // thời gian giữ ở vị trí mở (ms)

Servo lidServo;

// ==========================
// LCD 1602 I2C Config
// ==========================
const uint8_t LCD_I2C_ADDR = 0x27;             // Địa chỉ I2C (thường là 0x27 hoặc 0x3F)
const int LCD_COLS = 16;
const int LCD_ROWS = 2;
// Chân I2C cho LCD (không trùng chân camera)
const int LCD_SDA_PIN = 14;
const int LCD_SCL_PIN = 1;

LiquidCrystal_I2C lcd(LCD_I2C_ADDR, LCD_COLS, LCD_ROWS);

// ================== WIFI CONFIG ==================
const char* WIFI_SSID     = "iQOO Z9";        // <-- ĐỔI LẠI NẾU CẦN
const char* WIFI_PASSWORD = "88888888";       // <-- ĐỔI LẠI NẾU CẦN

// ================== WEBSOCKET CONFIG =============
WebSocketsClient webSocket;
const char* WS_HOST = "ntdung.systems";
const uint16_t WS_PORT = 443;
const char* WS_PATH = "/ws";

// ================== NTP CONFIG ===================
const char* NTP_SERVER = "pool.ntp.org";
const long  GMT_OFFSET_SEC = 7 * 3600;   // GMT+7
const int   DAYLIGHT_OFFSET_SEC = 0;

// ================== CAMERA PINS (AI THINKER) =====
#define CAMERA_MODEL_AI_THINKER

#if defined(CAMERA_MODEL_AI_THINKER)
  #define PWDN_GPIO_NUM     32
  #define RESET_GPIO_NUM    -1
  #define XCLK_GPIO_NUM      0
  #define SIOD_GPIO_NUM     26
  #define SIOC_GPIO_NUM     27

  #define Y9_GPIO_NUM       35
  #define Y8_GPIO_NUM       34
  #define Y7_GPIO_NUM       39
  #define Y6_GPIO_NUM       36
  #define Y5_GPIO_NUM       21
  #define Y4_GPIO_NUM       19
  #define Y3_GPIO_NUM       18
  #define Y2_GPIO_NUM        5
  #define VSYNC_GPIO_NUM    25
  #define HREF_GPIO_NUM     23
  #define PCLK_GPIO_NUM     22
#endif

// ================== CAPTURE SETTINGS =============
unsigned long lastCaptureMs = 0;                          // lần chụp gần nhất (dùng để cooldown)
const unsigned long CAPTURE_INTERVAL_MS = 10 * 1000;      // thời gian chờ tối thiểu giữa 2 lần chụp (10s)
uint32_t imageCounter = 0;

// ================== ULTRASONIC (HC-SR04) CONFIG =============
// Dùng chung 1 chân TRIG cho 3 cảm biến (lối vào, thùng hữu cơ, thùng vô cơ)
const int TRIG_PIN   = 2; 
const int ECHO_ENTRY_PIN   = 13;   // cảm biến phía trên miệng thùng (phát hiện có rác đưa vào)
const int ECHO_ORGANIC_PIN = 4;  // cảm biến kiểm tra thùng hữu cơ đầy
const int ECHO_INORG_PIN   = 15;  // cảm biến kiểm tra thùng vô cơ đầy

const float DIST_THRESHOLD_CM      = 20.0;    // ngưỡng 20cm để chụp ảnh
const float BIN_FULL_THRESHOLD_CM  = 10.0;     // < 10cm coi như đầy thùng
const unsigned long BIN_FULL_HOLD_MS = 3000;  // phải giữ liên tục 3s

// trạng thái thùng
unsigned long organicNearSince   = 0;
unsigned long inorganicNearSince = 0;
bool organicFull   = false;
bool inorganicFull = false;

// dùng để tránh update LCD liên tục
bool lastOrganicFull   = false;
bool lastInorganicFull = false;

// ========== FORWARD DECLARATIONS ==========
void captureAndSendImage();

// ========== BASE64 ENCODE (tự cài, không cần lib ngoài) ==========
String base64Encode(const uint8_t* data, size_t length) {
  const char* b64chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  String result;
  result.reserve(((length + 2) / 3) * 4);

  for (size_t i = 0; i < length; i += 3) {
    uint32_t n = (uint32_t)data[i] << 16;

    if (i + 1 < length) {
      n |= (uint32_t)data[i + 1] << 8;
    }
    if (i + 2 < length) {
      n |= data[i + 2];
    }

    result += b64chars[(n >> 18) & 0x3F];
    result += b64chars[(n >> 12) & 0x3F];

    if (i + 1 < length) {
      result += b64chars[(n >> 6) & 0x3F];
    } else {
      result += '=';
    }

    if (i + 2 < length) {
      result += b64chars[n & 0x3F];
    } else {
      result += '=';
    }
  }

  return result;
}

// ================== TIME HELPERS =================
void initTime() {
  Serial.println("[TIME] Configuring NTP...");
  configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER);

  struct tm timeinfo;
  int retry = 0;
  while (!getLocalTime(&timeinfo) && retry < 10) {
    Serial.println("[TIME] Waiting for time...");
    delay(1000);
    retry++;
  }

  if (!getLocalTime(&timeinfo)) {
    Serial.println("[TIME] Failed to obtain time");
  } else {
    Serial.print("[TIME] Current time: ");
    Serial.println(&timeinfo, "%Y-%m-%d %H:%M:%S");
  }
}

time_t getNowEpochSeconds() {
  time_t now;
  time(&now);
  return now;
}

// ================== CAMERA INIT ==================
bool initCamera() {
  Serial.println("[CAMERA] Initializing camera...");

  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0       = Y2_GPIO_NUM;
  config.pin_d1       = Y3_GPIO_NUM;
  config.pin_d2       = Y4_GPIO_NUM;
  config.pin_d3       = Y5_GPIO_NUM;
  config.pin_d4       = Y6_GPIO_NUM;
  config.pin_d5       = Y7_GPIO_NUM;
  config.pin_d6       = Y8_GPIO_NUM;
  config.pin_d7       = Y9_GPIO_NUM;
  config.pin_xclk     = XCLK_GPIO_NUM;
  config.pin_pclk     = PCLK_GPIO_NUM;
  config.pin_vsync    = VSYNC_GPIO_NUM;
  config.pin_href     = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn     = PWDN_GPIO_NUM;
  config.pin_reset    = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  if (psramFound()) {
    Serial.println("[CAMERA] PSRAM found, using higher resolution");
    config.frame_size   = FRAMESIZE_VGA;
    config.jpeg_quality = 10;
    config.fb_count     = 2;
  } else {
    Serial.println("[CAMERA] PSRAM not found, using lower resolution");
    config.frame_size   = FRAMESIZE_QVGA;
    config.jpeg_quality = 12;
    config.fb_count     = 1;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[CAMERA] Camera init failed with error 0x%x\n", err);
    return false;
  }

  Serial.println("[CAMERA] Camera init success");
  return true;
}

// ========== ULTRASONIC HELPER FUNCTIONS ==========
// Đọc khoảng cách trên chân echo bất kỳ (dùng chung TRIG)
float readDistanceCmOnEcho(int echoPin) {
  // phát xung 10us trên chân TRIG
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  // đo thời gian xung HIGH ở chân ECHO (timeout 30ms ~ 5m)
  long duration = pulseIn(echoPin, HIGH, 30000);
  if (duration == 0) {
    // không nhận được echo
    return -1.0f;
  }

  // tốc độ âm thanh ~0.0343 cm/us, chia 2 do đi và về
  float distance = duration * 0.0343f / 2.0f;
  return distance;
}

// Cảm biến phía trên miệng thùng (phát hiện rác đưa vào)
float readDistanceCm() {
  return readDistanceCmOnEcho(ECHO_ENTRY_PIN);
}

// Kiểm tra cảm biến phía trên miệng thùng, nếu khoảng cách < DIST_THRESHOLD_CM
// và đã đủ 10s cooldown thì chụp ảnh
void handleUltrasonicTrigger() {
  unsigned long now = millis();

  // Nếu đã từng chụp rồi và chưa qua đủ thời gian chờ thì bỏ qua
  if (lastCaptureMs != 0 && (now - lastCaptureMs) < CAPTURE_INTERVAL_MS) {
    return;
  }

  float distance = readDistanceCm();
  if (distance < 0) {
    // không đọc được khoảng cách
    return;
  }

  // Serial.printf("[HC-SR04] Distance (ENTRY): %.2f cm\n", distance);

  if (distance <= DIST_THRESHOLD_CM) {
    if (!webSocket.isConnected()) {
      Serial.println("[HC-SR04] Cảm biến kích hoạt nhưng WebSocket chưa kết nối, bỏ qua.");
      return;
    }
    Serial.println("[HC-SR04] Object detected closer than 10cm -> capturing image...");
    lastCaptureMs = now;

    delay(1500); // cho rác vào vị trí ổn định một chút

    captureAndSendImage();
  }
}

// ================== SERVO CONTROL =================
void moveLidToAngle(int targetAngle) {
  Serial.printf("[SERVO] Move lid to %d deg\n", targetAngle);
  lidServo.write(targetAngle);
  delay(SERVO_HOLD_MS);
  lidServo.write(SERVO_CENTER_ANGLE);  // đưa nắp về giữa
  delay(300);
}

// Giữ nguyên tên hàm để không phải đổi logic phía server
void rotateClockwise() {
  // CW: nghiêng về phía thùng hữu cơ
  Serial.println("[SERVO] ROTATE_CW -> open ORGANIC bin");
  moveLidToAngle(SERVO_ORGANIC_ANGLE);
}

void rotateCounterClockwise() {
  // CCW: nghiêng về phía thùng vô cơ
  Serial.println("[SERVO] ROTATE_CCW -> open INORGANIC bin");
  moveLidToAngle(SERVO_INORGANIC_ANGLE);
}

void updateBinFullState() {
  // 0 = đo thùng hữu cơ, 1 = đo thùng vô cơ
  static uint8_t currentBin = 0;

  unsigned long now = millis();

  if (currentBin == 0) {
    // ===== ĐO THÙNG HỮU CƠ =====
    float distOrg = readDistanceCmOnEcho(ECHO_ORGANIC_PIN);
    Serial.print("[BIN] Organic dist = ");
    Serial.println(distOrg);

    if (distOrg > 0 && distOrg <= BIN_FULL_THRESHOLD_CM) {
      if (organicNearSince == 0) {
        organicNearSince = now;   // bắt đầu thấy "gần"
      } else if (!organicFull && (now - organicNearSince >= BIN_FULL_HOLD_MS)) {
        organicFull = true;
        sendBinFullStatus(1);
      }
    } else {
      // không còn gần nữa → reset
      organicNearSince = 0;
      organicFull = false;
    }

    // Lần sau chuyển sang đo thùng vô cơ
    currentBin = 1;

  } else {
    // ===== ĐO THÙNG VÔ CƠ =====
    float distInorg = readDistanceCmOnEcho(ECHO_INORG_PIN);
    Serial.print("[BIN] Inorganic dist = ");
    Serial.println(distInorg);

    if (distInorg > 0 && distInorg <= BIN_FULL_THRESHOLD_CM) {
      if (inorganicNearSince == 0) {
        inorganicNearSince = now;
      } else if (!inorganicFull && (now - inorganicNearSince >= BIN_FULL_HOLD_MS)) {
        inorganicFull = true;
        sendBinFullStatus(2);
      }
    } else {
      inorganicNearSince = 0;
      inorganicFull = false;
    }

    // Lần sau quay lại đo thùng hữu cơ
    currentBin = 0;
  }
}


void updateLcdStatus() {
  // chỉ update LCD khi trạng thái thay đổi để tránh nhấp nháy
  if (lastOrganicFull == organicFull && lastInorganicFull == inorganicFull) {
    return;
  }
  lastOrganicFull = organicFull;
  lastInorganicFull = inorganicFull;

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Huu co: ");
  lcd.print(organicFull ? "FULL" : "READY");
  lcd.setCursor(0, 1);
  lcd.print("Vo co : ");
  lcd.print(inorganicFull ? "FULL" : "READY");
}

// ================== CAPTURE & SEND =================
void captureAndSendImage() {
  if (!webSocket.isConnected()) {
    Serial.println("[CAPTURE] WebSocket not connected, skip capture");
    return;
  }

  Serial.println("[CAPTURE] Capturing image...");
  camera_fb_t* fb = esp_camera_fb_get();
  if (!fb) {
    Serial.println("[CAPTURE] Camera capture failed");
    return;
  }

  String imageBase64 = base64Encode(fb->buf, fb->len);
  esp_camera_fb_return(fb);

  time_t nowTime = getNowEpochSeconds();
  String filename = "esp32cam_" + String((unsigned long)nowTime) + "_" + String(imageCounter++) + ".jpg";

  // Build JSON giống format bạn đang dùng
  String message = "{\"type\":\"esp32:image\",\"payload\":{";
  message += "\"filename\":\"" + filename + "\",";
  message += "\"contentType\":\"image/jpeg\",";
  message += "\"data\":\"" + imageBase64 + "\",";
  message += "\"receivedAt\":" + String((unsigned long)nowTime);
  message += "}}";

  Serial.printf("[SEND] Sending image: filename=%s, len(base64)=%d\n", filename.c_str(), imageBase64.length());
  webSocket.sendTXT(message);
}

// ================== COMMAND PARSER =================
String extractCommand(const String& msg) {
  StaticJsonDocument<1024> doc;
  DeserializationError err = deserializeJson(doc, msg);
  if (err) {
    Serial.print(F("[JSON] deserializeJson() failed: "));
    Serial.println(err.f_str());
    return "";
  }

  JsonVariant payload = doc["payload"];
  if (payload.isNull()) {
    Serial.println(F("[JSON] payload is null"));
    return "";
  }

  // Case mới: { "type":"server:data", "payload":{"data":"ROTATE_CW", "receivedAt":...} }
  if (payload.containsKey("data")) {
    JsonVariant dataField = payload["data"];
    if (dataField.is<const char*>()) {
      String cmd = dataField.as<String>();
      cmd.trim();
      return cmd;
    }
  }

  // Case cũ: payload là object có key "0","1","2",... ghép thành chuỗi
  if (!payload.is<JsonObject>()) {
    Serial.println(F("[JSON] payload is not an object"));
    return "";
  }

  JsonObject payloadObj = payload.as<JsonObject>();
  String command;

  for (int i = 0;; i++) {
    String key = String(i); // "0", "1", "2", ...
    if (!payloadObj.containsKey(key)) {
      break; // hết ký tự
    }

    const char* ch = payloadObj[key];
    if (ch != nullptr && ch[0] != '\0') {
      command += ch[0];
    }
  }

  command.trim();
  return command;
}

// ================== WEBSOCKET EVENT HANDLER =================
void webSocketEvent(WStype_t t, uint8_t* payload, size_t length) {
  switch (t) {
    case WStype_CONNECTED:
      Serial.printf("[WS] Connected to: %s\n", (const char*)payload);
      webSocket.sendTXT("{\"type\":\"esp32:ping\",\"payload\":{\"message\":\"hello\"}}");
      break;

    case WStype_TEXT: {
      String msg = String((char*)payload);
      Serial.printf("[WS] Received: %s\n", msg.c_str());

      String command = extractCommand(msg);
      Serial.print("[WS] Command = ");
      Serial.println(command);

      if (command == "ROTATE_CW") {
        rotateClockwise();
      } else if (command == "ROTATE_CCW") {
        rotateCounterClockwise();
      } else {
        Serial.println("[WS] Unknown command");
      }

      break;
    }

    case WStype_DISCONNECTED:
      Serial.printf("[WS] Disconnected. Reason: ");
      if (length > 0) {
        Serial.write(payload, length);
      } else {
        Serial.print("(no reason)");
      }
      Serial.println();
      break;

    default:
      Serial.printf("[WS] Event type: %d, length: %d\n", t, (int)length);
      break;
  }
}

// 1 là hữu cơ, 2 là vô cơ
void sendBinFullStatus(int type){
  time_t nowTime = getNowEpochSeconds();

  String binType = type == 1 ? "ORGANIC" : "INORGANIC";

  // Build JSON giống format bạn đang dùng
  String message = "{\"type\":\"esp32:data\",\"payload\":{";
  message += "\"binType\":\"" + binType + "\",";
  message += "\"receivedAt\":" + String((unsigned long)nowTime);
  message += "}}";

  Serial.printf("[SEND] Bin %s is full, sending to server", binType);
  webSocket.sendTXT(message);
}

// ================== WIFI & WS INIT =================
void initWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.printf("[WIFI] Connecting to %s", WIFI_SSID);
  int retry = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(1000);
    Serial.print(".");
    retry++;
    if (retry > 20) {
      Serial.println("\n[WIFI] Failed to connect, restarting...");
      ESP.restart();
    }
  }

  Serial.println("\n[WIFI] Connected!");
  Serial.print("[WIFI] IP: ");
  Serial.println(WiFi.localIP());
}

void initWebSocket() {
  Serial.printf("[WS] Initialize to wss://%s%s\n", WS_HOST, WS_PATH);
  webSocket.beginSSL(WS_HOST, WS_PORT, WS_PATH);
  webSocket.onEvent(webSocketEvent);
  webSocket.setReconnectInterval(5000);  // 5s reconnect
  webSocket.enableHeartbeat(15000, 3000, 2);

  Serial.println("[WS] WebSocket setup done, waiting for connection...");
}

// ================== SETUP & LOOP =================
void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
  Serial.begin(115200);
  delay(10000);
  Serial.println();
  Serial.println("===== ESP32-CAM WebSocket Image Sender (Servo + 3 Ultrasonic + LCD) =====");
  Serial.setDebugOutput(true); // log chi tiết WiFi / TLS

  lidServo.attach(SERVO_PIN);
  lidServo.write(SERVO_CENTER_ANGLE);
  Serial.println("[SERVO] Init done, lid at center");

  if (!initCamera()) {
    Serial.println("[SETUP] Camera init failed. Restarting...");
    delay(2000);
    ESP.restart();
  }

  initWiFi();
  initTime();
  initWebSocket();

  // Khởi tạo chân cho 3 cảm biến HC-SR04
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_ENTRY_PIN, INPUT);
  pinMode(ECHO_ORGANIC_PIN, INPUT);
  // pinMode(ECHO_INORG_PIN, INPUT);
  digitalWrite(TRIG_PIN, LOW);

  // Khởi tạo I2C & LCD 1602
  Serial.println("[SETUP] Initializing LCD...");
  // lcd = new LiquidCrystal_I2C(LCD_I2C_ADDR, LCD_COLS, LCD_ROWS);
  Wire.begin(LCD_SDA_PIN, LCD_SCL_PIN);
  lcd.init();
  lcd.backlight();
  lcd.clear();

  lastOrganicFull   = true;
  lastInorganicFull = true;

  lastCaptureMs = 0; // chưa chụp lần nào
}

void loop() {
  // xử lý WebSocket (connect, reconnect, nhận tin…)
  webSocket.loop();

  // Kiểm tra cảm biến siêu âm phía trên miệng thùng và chụp ảnh nếu đủ điều kiện
  handleUltrasonicTrigger();

  delay(100);

  static unsigned long lastBinCheck = 0;
  unsigned long now = millis();

  // Cứ mỗi 300ms gọi 1 lần, và mỗi lần chỉ đo 1 cảm biến
  if (now - lastBinCheck >= 300) {
    updateBinFullState();
    lastBinCheck = now;
  }

  // Cập nhật thông tin đầy / chưa đầy lên LCD
  updateLcdStatus();
}
