package com.leadboard.poker.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSocket
public class PokerWebSocketConfig implements WebSocketConfigurer {

    private final PokerWebSocketHandler pokerWebSocketHandler;

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    public PokerWebSocketConfig(PokerWebSocketHandler pokerWebSocketHandler) {
        this.pokerWebSocketHandler = pokerWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        List<String> origins = new ArrayList<>();
        origins.add("http://localhost:5173");
        origins.add("http://localhost:3000");

        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            for (String origin : corsAllowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    origins.add(trimmed);
                }
            }
        }

        registry.addHandler(pokerWebSocketHandler, "/ws/poker/{roomCode}")
                .setAllowedOrigins(origins.toArray(new String[0]));
    }
}
