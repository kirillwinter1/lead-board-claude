package com.leadboard.jira;

import com.leadboard.auth.OAuthService;
import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link JiraWriteService#logWorkAs} — the F89 write path that logs time using the
 * acting user's OWN OAuth token (via {@link OAuthService#getValidAccessTokenForUser}),
 * rather than {@link JiraWriteService#requireCreds()}'s "last known tenant token" used by
 * the AI chat write path. Worklogs must be attributed to the actual member, not whoever
 * last authenticated.
 */
@ExtendWith(MockitoExtension.class)
class JiraWriteServiceLogWorkAsTest {

    @Mock
    private JiraClient jiraClient;

    @Mock
    private OAuthService oauthService;

    @Mock
    private WorkflowConfigService workflowConfigService;

    @Mock
    private JiraConfigResolver configResolver;

    private JiraWriteService service;

    @BeforeEach
    void setUp() {
        service = new JiraWriteService(jiraClient, oauthService, workflowConfigService, configResolver);
    }

    @Test
    void logWorkAsUsesPersonalTokenAndReturnsWorklogId() {
        when(oauthService.getValidAccessTokenForUser("acc-1"))
                .thenReturn(new OAuthService.TokenInfo("tok", "cloud"));
        when(jiraClient.addWorklogReturningId("SUB-1", 1800, LocalDate.of(2026, 7, 8), "note", "tok", "cloud"))
                .thenReturn("42");

        assertEquals("42", service.logWorkAs("acc-1", "SUB-1", 1800, LocalDate.of(2026, 7, 8), "note"));

        verify(oauthService, never()).getValidAccessToken();
    }

    @Test
    void logWorkAsThrowsWhenUserHasNoToken() {
        when(oauthService.getValidAccessTokenForUser("acc-1")).thenReturn(null);

        assertThrows(JiraWriteService.NoUserTokenException.class,
                () -> service.logWorkAs("acc-1", "SUB-1", 1800, LocalDate.now(), null));

        verifyNoInteractions(jiraClient);
    }
}
