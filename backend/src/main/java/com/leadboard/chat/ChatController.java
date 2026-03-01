package com.leadboard.chat;

import com.leadboard.chat.dto.ChatMessageRequest;
import com.leadboard.chat.dto.ChatSseEvent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatProperties chatProperties;

    public ChatController(ChatService chatService, ChatProperties chatProperties) {
        this.chatService = chatService;
        this.chatProperties = chatProperties;
    }

    @PostMapping(value = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatSseEvent>> sendMessage(@RequestBody ChatMessageRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();

        return chatService.processMessage(sessionId, request.message())
                .map(event -> ServerSentEvent.<ChatSseEvent>builder()
                        .event(event.type())
                        .data(event)
                        .build());
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(java.util.Map.of("enabled", chatProperties.isEnabled()));
    }
}
