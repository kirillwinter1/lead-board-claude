package com.leadboard.poker.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class PokerWebSocketConfig implements WebSocketConfigurer {

    private final PokerWebSocketHandler pokerWebSocketHandler;

    public PokerWebSocketConfig(PokerWebSocketHandler pokerWebSocketHandler) {
        this.pokerWebSocketHandler = pokerWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pokerWebSocketHandler, "/ws/poker/{roomCode}")
                .setAllowedOrigins("*");
    }
}
