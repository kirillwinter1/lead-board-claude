package com.leadboard.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SECURITY_AUDIT.md §9 — session-cookie {@code Secure} must default to {@code true}
 * (fail-closed). A previous YAML placeholder ({@code ${APP_SESSION_COOKIE_SECURE:false}})
 * silently overrode this Java default, so the LEAD_SESSION cookie was sent over plaintext
 * HTTP unless an operator explicitly set the env var. Guard the Java-side default here;
 * application.yml/application-prod.yml are covered by manual/ops review since Spring Boot
 * config properties aren't easily unit-testable without a full context.
 */
class AppPropertiesTest {

    @Test
    void sessionCookieSecureDefaultsToTrue() {
        AppProperties.Session session = new AppProperties.Session();

        assertTrue(session.isCookieSecure(), "cookie-secure must default to true (fail-closed)");
    }
}
