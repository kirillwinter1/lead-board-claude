package com.leadboard.jira;

import com.leadboard.auth.OAuthService;
import com.leadboard.config.JiraConfigResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Covers the auth-selection branch in {@link JiraClient#search}, which is part of the
 * CRITICAL cross-tenant OAuth token fix (see ai-ru/SECURITY_AUDIT.md, item 1).
 *
 * JiraClient must NEVER silently proceed with "no credentials" — when
 * {@link OAuthService} has no token for the current tenant (post-fix behavior), it must
 * attempt the per-tenant Basic Auth path (via {@link JiraConfigResolver}) rather than
 * falling back to some other tenant's OAuth token. We assert this by observing the
 * "Jira base URL is not configured and OAuth is not available" failure, which can only be
 * thrown from the Basic Auth branch — proving the OAuth branch was correctly skipped and
 * no network call with a stray token was attempted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraClientTest {

    @Mock
    private JiraConfigResolver configResolver;

    @Mock
    private OAuthService oauthService;

    private JiraClient jiraClient;

    @BeforeEach
    void setUp() {
        jiraClient = new JiraClient(configResolver, oauthService, WebClient.builder());
    }

    @Test
    @DisplayName("search() falls back to Basic Auth (never a stray token) when there is no OAuth access token for the tenant")
    void shouldFallBackToBasicAuthWhenNoAccessToken() {
        when(oauthService.getValidAccessToken()).thenReturn(null);
        when(oauthService.getCloudIdForCurrentUser()).thenReturn(null);
        when(configResolver.getBaseUrl()).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> jiraClient.search("project = TEST", 0, 50));

        assertTrue(ex.getMessage().contains("OAuth is not available"));
        verify(oauthService, atLeastOnce()).getValidAccessToken();
        verify(oauthService, atLeastOnce()).getCloudIdForCurrentUser();
    }

    @Test
    @DisplayName("search() falls back to Basic Auth when there is no cloudId for the tenant (even if an access token exists)")
    void shouldFallBackToBasicAuthWhenNoCloudId() {
        when(oauthService.getValidAccessToken()).thenReturn("some-other-tenant-would-never-reach-here");
        when(oauthService.getCloudIdForCurrentUser()).thenReturn(null);
        when(configResolver.getBaseUrl()).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> jiraClient.search("project = TEST", 0, 50));

        assertTrue(ex.getMessage().contains("OAuth is not available"));
    }
}
