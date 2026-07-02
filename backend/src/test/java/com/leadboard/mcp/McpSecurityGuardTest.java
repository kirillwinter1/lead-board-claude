package com.leadboard.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SECURITY_AUDIT.md #10: MCP debug static-token must not be reachable in prod without OAuth.
 */
class McpSecurityGuardTest {

    @Test
    void prodWithMcpEnabledAndOauthDisabled_throws() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> McpSecurityGuard.validate(true, false, new String[]{"prod"}));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("mcp.oauth-enabled"));
    }

    @Test
    void prodWithMcpEnabledAndOauthEnabled_doesNotThrow() {
        assertDoesNotThrow(() -> McpSecurityGuard.validate(true, true, new String[]{"prod"}));
    }

    @Test
    void prodWithMcpDisabled_doesNotThrow() {
        assertDoesNotThrow(() -> McpSecurityGuard.validate(false, false, new String[]{"prod"}));
    }

    @Test
    void devWithMcpEnabledAndOauthDisabled_doesNotThrow() {
        // Debug-token stays usable on local/dev profiles.
        assertDoesNotThrow(() -> McpSecurityGuard.validate(true, false, new String[]{"dev"}));
    }

    @Test
    void noActiveProfileWithMcpEnabledAndOauthDisabled_doesNotThrow() {
        // Default (no explicit profile) is treated as local — only an explicit "prod" profile triggers the guard.
        assertDoesNotThrow(() -> McpSecurityGuard.validate(true, false, new String[]{}));
    }

    @Test
    void prodAmongMultipleActiveProfiles_stillThrows() {
        assertThrows(IllegalStateException.class,
                () -> McpSecurityGuard.validate(true, false, new String[]{"metrics", "prod"}));
    }

    @Test
    void afterPropertiesSetDelegatesToValidateUsingPropsAndEnvironment() {
        McpProperties props = new McpProperties();
        props.setEnabled(true);
        props.setOauthEnabled(false);

        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        McpSecurityGuard guard = new McpSecurityGuard(props, env);
        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }
}
