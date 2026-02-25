package com.leadboard.tenant;

import com.leadboard.config.JiraConfigResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * CRUD API for per-tenant Jira configuration.
 * Replaces .env-based config for multi-tenant mode.
 */
@RestController
@RequestMapping("/api/jira-config")
@PreAuthorize("hasRole('ADMIN')")
public class TenantJiraConfigController {

    private static final Logger log = LoggerFactory.getLogger(TenantJiraConfigController.class);

    private final TenantJiraConfigRepository configRepository;
    private final JiraConfigResolver jiraConfigResolver;

    public TenantJiraConfigController(TenantJiraConfigRepository configRepository,
                                       JiraConfigResolver jiraConfigResolver) {
        this.configRepository = configRepository;
        this.jiraConfigResolver = jiraConfigResolver;
    }

    @GetMapping
    public ResponseEntity<?> getConfig() {
        Optional<TenantJiraConfigEntity> configOpt;
        try {
            configOpt = configRepository.findActive();
        } catch (Exception e) {
            // tenant_jira_config table may not exist in public schema
            log.debug("Could not read tenant_jira_config: {}", e.getMessage());
            configOpt = Optional.empty();
        }
        if (configOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "configured", false,
                    "jiraBaseUrl", nullToEmpty(jiraConfigResolver.getBaseUrl()),
                    "jiraEmail", nullToEmpty(jiraConfigResolver.getEmail()),
                    "hasApiToken", jiraConfigResolver.getApiToken() != null && !jiraConfigResolver.getApiToken().isBlank(),
                    "projectKey", nullToEmpty(jiraConfigResolver.getProjectKey()),
                    "teamFieldId", nullToEmpty(jiraConfigResolver.getTeamFieldId()),
                    "organizationId", nullToEmpty(jiraConfigResolver.getOrganizationId()),
                    "syncIntervalSeconds", 300,
                    "manualTeamManagement", jiraConfigResolver.isManualTeamManagement()
            ));
        }

        TenantJiraConfigEntity config = configOpt.get();
        return ResponseEntity.ok(Map.of(
                "configured", true,
                "id", config.getId(),
                "jiraBaseUrl", nullToEmpty(config.getJiraBaseUrl()),
                "jiraEmail", nullToEmpty(config.getJiraEmail()),
                "hasApiToken", config.getJiraApiToken() != null && !config.getJiraApiToken().isBlank(),
                "projectKey", nullToEmpty(config.getProjectKeys()),
                "teamFieldId", nullToEmpty(config.getTeamFieldId()),
                "organizationId", nullToEmpty(config.getOrganizationId()),
                "syncIntervalSeconds", config.getSyncIntervalSeconds(),
                "manualTeamManagement", config.isManualTeamManagement()
        ));
    }

    @PutMapping
    public ResponseEntity<?> saveConfig(@RequestBody JiraConfigRequest request) {
        if (request.jiraBaseUrl() == null || request.jiraBaseUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Jira base URL is required"));
        }
        if (request.projectKey() == null || request.projectKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project key is required"));
        }

        TenantJiraConfigEntity config;
        try {
            config = configRepository.findActive().orElseGet(TenantJiraConfigEntity::new);
        } catch (Exception e) {
            config = new TenantJiraConfigEntity();
        }
        config.setJiraBaseUrl(request.jiraBaseUrl().trim());
        config.setJiraEmail(request.jiraEmail() != null ? request.jiraEmail().trim() : null);
        config.setProjectKeys(request.projectKey().trim());
        config.setTeamFieldId(request.teamFieldId() != null ? request.teamFieldId().trim() : null);
        config.setOrganizationId(request.organizationId() != null ? request.organizationId().trim() : null);
        config.setSyncIntervalSeconds(request.syncIntervalSeconds() > 0 ? request.syncIntervalSeconds() : 300);
        config.setManualTeamManagement(request.manualTeamManagement());
        config.setActive(true);

        // Only update API token if provided (don't overwrite with empty)
        if (request.jiraApiToken() != null && !request.jiraApiToken().isBlank()) {
            config.setJiraApiToken(request.jiraApiToken().trim());
        }

        config = configRepository.save(config);
        log.info("Jira config saved: baseUrl={}, project={}", config.getJiraBaseUrl(), config.getProjectKeys());

        return ResponseEntity.ok(Map.of(
                "id", config.getId(),
                "configured", true,
                "message", "Jira configuration saved"
        ));
    }

    @PostMapping("/test")
    public ResponseEntity<?> testConnection(@RequestBody JiraConfigRequest request) {
        if (request.jiraBaseUrl() == null || request.jiraBaseUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Jira base URL is required"));
        }

        try {
            // Try a lightweight API call to verify credentials
            var webClient = org.springframework.web.reactive.function.client.WebClient.builder().build();
            String auth = request.jiraEmail() + ":" + request.jiraApiToken();
            String encodedAuth = java.util.Base64.getEncoder()
                    .encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            String response = webClient.get()
                    .uri(request.jiraBaseUrl().trim() + "/rest/api/3/myself")
                    .header("Authorization", "Basic " + encodedAuth)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(10));

            if (response != null) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Connection successful"));
            }
            return ResponseEntity.ok(Map.of("success", false, "error", "Empty response from Jira"));
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("401")) {
                return ResponseEntity.ok(Map.of("success", false, "error", "Invalid credentials (401 Unauthorized)"));
            }
            if (msg != null && msg.contains("403")) {
                return ResponseEntity.ok(Map.of("success", false, "error", "Access denied (403 Forbidden)"));
            }
            return ResponseEntity.ok(Map.of("success", false, "error", "Connection failed: " + (msg != null ? msg : "unknown error")));
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record JiraConfigRequest(
            String jiraBaseUrl,
            String jiraEmail,
            String jiraApiToken,
            String projectKey,
            String teamFieldId,
            String organizationId,
            int syncIntervalSeconds,
            boolean manualTeamManagement
    ) {}
}
