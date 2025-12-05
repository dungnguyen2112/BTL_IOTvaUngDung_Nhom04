package com.iot.websocket.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Esp32Data {
    private Object data; // ESP32 sensor data (can be flexible structure)
    private Long receivedAt;
}
