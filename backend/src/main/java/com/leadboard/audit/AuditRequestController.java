package com.leadboard.audit;

import com.leadboard.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/audit-requests")
public class AuditRequestController {
    private static final Logger log = LoggerFactory.getLogger(AuditRequestController.class);

    private final TelegramService telegramService;

    public AuditRequestController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submitAuditRequest(@RequestBody AuditRequest request) {
        log.info("Received audit request from: {}", request.name());

        String message = formatTelegramMessage(request);
        boolean sent = telegramService.sendMessage(message);

        if (sent) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Заявка отправлена"
            ));
        } else {
            log.warn("Failed to send Telegram notification for audit request");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Заявка принята"
            ));
        }
    }

    private String formatTelegramMessage(AuditRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 <b>Новая заявка на разбор</b>\n\n");
        sb.append("👤 <b>Имя:</b> ").append(escapeHtml(request.name())).append("\n");

        if (request.company() != null && !request.company().isBlank()) {
            sb.append("🏢 <b>Компания:</b> ").append(escapeHtml(request.company())).append("\n");
        }

        if (request.role() != null && !request.role().isBlank()) {
            sb.append("💼 <b>Роль:</b> ").append(escapeHtml(request.role())).append("\n");
        }

        sb.append("📱 <b>Контакт:</b> ").append(escapeHtml(request.contact())).append("\n");
        sb.append("\n📅 ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    public record AuditRequest(
        String name,
        String company,
        String role,
        String contact
    ) {}
}
