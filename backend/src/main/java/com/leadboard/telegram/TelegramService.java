package com.leadboard.telegram;

import com.leadboard.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

@Service
public class TelegramService {
    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot%s/sendMessage";

    private final TelegramProperties properties;
    private final RestTemplate restTemplate;

    public TelegramService(TelegramProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    public boolean sendMessage(String message) {
        if (properties.getBotToken() == null || properties.getChatId() == null) {
            log.warn("Telegram bot token or chat ID not configured");
            return false;
        }

        try {
            String url = String.format(TELEGRAM_API_URL, properties.getBotToken());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                "chat_id", properties.getChatId(),
                "text", message,
                "parse_mode", "HTML"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Telegram message sent successfully");
                return true;
            } else {
                log.error("Failed to send Telegram message: {}", response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending Telegram message", e);
            return false;
        }
    }
}
