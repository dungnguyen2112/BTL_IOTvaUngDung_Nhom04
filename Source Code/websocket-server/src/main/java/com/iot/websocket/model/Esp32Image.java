package com.iot.websocket.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Esp32Image {
    private String filename;
    private String contentType;
    private String data; // Base64 encoded image
    private Long receivedAt;
    private Integer size;
}
