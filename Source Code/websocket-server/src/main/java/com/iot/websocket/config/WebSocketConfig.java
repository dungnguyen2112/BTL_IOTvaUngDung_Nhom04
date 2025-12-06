package com.iot.websocket.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.context.annotation.Bean;
import com.iot.websocket.handler.IoTWebSocketHandler;

@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final IoTWebSocketHandler ioTWebSocketHandler;
    
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    public WebSocketConfig(IoTWebSocketHandler ioTWebSocketHandler) {
        this.ioTWebSocketHandler = ioTWebSocketHandler;
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(2097152); // 2MB
        container.setMaxBinaryMessageBufferSize(2097152); // 2MB
        container.setMaxSessionIdleTimeout(86400000L); // 24 hours
        container.setAsyncSendTimeout(600000L); // 10 minutes
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = parseOrigins(allowedOrigins);
        
        log.info("Registering WebSocket handler at /ws with allowed origins: {}", 
                 String.join(", ", origins));
        
        registry.addHandler(ioTWebSocketHandler, "/ws")
                .setAllowedOrigins(origins);
    }

    private String[] parseOrigins(String originsStr) {
        if (originsStr == null || originsStr.trim().isEmpty() || "*".equals(originsStr.trim())) {
            return new String[]{"*"};
        }
        
        return originsStr.split(",");
    }
}
