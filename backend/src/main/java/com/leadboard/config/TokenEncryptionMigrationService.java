package com.leadboard.config;

import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * One-time migration: encrypts existing plaintext tokens in the database.
 * Uses JDBC to bypass JPA converters and read raw column values.
 * Idempotent — already-encrypted values are skipped.
 */
@Service
public class TokenEncryptionMigrationService {

    private static final Logger log = LoggerFactory.getLogger(TokenEncryptionMigrationService.class);

    private final EncryptionService encryptionService;
    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;

    public TokenEncryptionMigrationService(EncryptionService encryptionService,
                                           JdbcTemplate jdbcTemplate,
                                           TenantRepository tenantRepository) {
        this.encryptionService = encryptionService;
        this.jdbcTemplate = jdbcTemplate;
        this.tenantRepository = tenantRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateTokens() {
        if (!encryptionService.isEnabled()) {
            log.debug("Token encryption disabled — skipping migration");
            return;
        }

        int oauthCount = migrateOAuthTokens();
        int tenantCount = migrateTenantJiraTokens();

        if (oauthCount + tenantCount > 0) {
            log.info("Token encryption migration complete: {} OAuth tokens, {} tenant API tokens encrypted",
                    oauthCount, tenantCount);
        } else {
            log.debug("Token encryption migration: no plaintext tokens found");
        }
    }

    private int migrateOAuthTokens() {
        int count = 0;

        List<TokenRow> rows = jdbcTemplate.query(
                "SELECT id, access_token, refresh_token FROM public.oauth_tokens",
                (rs, rowNum) -> new TokenRow(
                        rs.getLong("id"),
                        rs.getString("access_token"),
                        rs.getString("refresh_token")
                )
        );

        for (TokenRow row : rows) {
            boolean updated = false;

            String encAccess = row.accessToken;
            if (row.accessToken != null && !encryptionService.isLikelyEncrypted(row.accessToken)) {
                encAccess = encryptionService.encrypt(row.accessToken);
                updated = true;
            }

            String encRefresh = row.refreshToken;
            if (row.refreshToken != null && !encryptionService.isLikelyEncrypted(row.refreshToken)) {
                encRefresh = encryptionService.encrypt(row.refreshToken);
                updated = true;
            }

            if (updated) {
                jdbcTemplate.update(
                        "UPDATE public.oauth_tokens SET access_token = ?, refresh_token = ? WHERE id = ?",
                        encAccess, encRefresh, row.id
                );
                count++;
            }
        }

        return count;
    }

    private int migrateTenantJiraTokens() {
        int count = 0;

        List<TenantEntity> tenants;
        try {
            tenants = tenantRepository.findAllActive();
        } catch (Exception e) {
            log.debug("No tenants table or no active tenants — skipping tenant token migration");
            return 0;
        }

        for (TenantEntity tenant : tenants) {
            String schema = tenant.getSchemaName();
            try {
                List<TenantTokenRow> rows = jdbcTemplate.query(
                        "SELECT id, jira_api_token FROM " + sanitizeSchema(schema) + ".tenant_jira_config",
                        (rs, rowNum) -> new TenantTokenRow(
                                rs.getLong("id"),
                                rs.getString("jira_api_token")
                        )
                );

                for (TenantTokenRow row : rows) {
                    if (row.token != null && !encryptionService.isLikelyEncrypted(row.token)) {
                        String encrypted = encryptionService.encrypt(row.token);
                        jdbcTemplate.update(
                                "UPDATE " + sanitizeSchema(schema) + ".tenant_jira_config SET jira_api_token = ? WHERE id = ?",
                                encrypted, row.id
                        );
                        count++;
                    }
                }
            } catch (Exception e) {
                log.debug("Could not migrate tokens for tenant schema {}: {}", schema, e.getMessage());
            }
        }

        return count;
    }

    /**
     * Sanitize schema name to prevent SQL injection.
     * Only allows alphanumeric and underscore characters.
     */
    private String sanitizeSchema(String schema) {
        if (schema == null || !schema.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        return schema;
    }

    private record TokenRow(long id, String accessToken, String refreshToken) {}
    private record TenantTokenRow(long id, String token) {}
}
