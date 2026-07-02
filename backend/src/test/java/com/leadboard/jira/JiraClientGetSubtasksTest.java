package com.leadboard.jira;

import com.leadboard.auth.OAuthService;
import com.leadboard.config.JiraConfigResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SECURITY_AUDIT.md #4 (HIGH) — JQL injection via existingStoryKey in Planning Poker.
 *
 * {@link JiraClient#getSubtasks(String)} used to build JQL with raw string concatenation
 * ({@code "parent = " + parentKey}), so a crafted {@code parentKey} such as
 * {@code "X OR project = SECRET"} became part of the executed query, and
 * {@code PokerJiraService#updateSubtaskEstimates} would then write estimates onto
 * whatever issues that injected JQL returned. getSubtasks() must validate its input
 * independently of any caller (defense-in-depth on top of the AddStoryRequest DTO
 * validation) and refuse to build/execute JQL for anything that isn't a well-formed
 * Jira issue key.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraClientGetSubtasksTest {

    @Mock
    private JiraConfigResolver configResolver;

    @Mock
    private OAuthService oauthService;

    /** Captures the JQL that getSubtasks() would have sent to Jira, without any network I/O. */
    private final class JqlCapturingJiraClient extends JiraClient {
        final List<String> capturedJql = new ArrayList<>();

        JqlCapturingJiraClient() {
            super(configResolver, oauthService, WebClient.builder());
        }

        @Override
        public JiraSearchResponse search(String jql, int maxResults, String nextPageToken) {
            capturedJql.add(jql);
            JiraSearchResponse response = new JiraSearchResponse();
            response.setIssues(List.of());
            return response;
        }
    }

    private JqlCapturingJiraClient jiraClient;

    @BeforeEach
    void setUp() {
        jiraClient = new JqlCapturingJiraClient();
    }

    @Test
    @DisplayName("rejects a classic JQL injection payload without building or executing any query")
    void rejectsJqlInjectionPayload() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jiraClient.getSubtasks("X OR project = SECRET"));

        assertTrue(ex.getMessage().toLowerCase().contains("invalid"));
        assertTrue(jiraClient.capturedJql.isEmpty(), "no JQL should have been sent to Jira");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "X OR project = SECRET",
            "ABC-123 OR 1=1",
            "ABC-123\" OR \"1\"=\"1",
            "'; DROP TABLE poker_story;--",
            "abc-123",
            "ABC123",
            "-123",
            "ABC-",
    })
    @DisplayName("rejects malformed / injected parent keys before building JQL")
    void rejectsMalformedOrInjectedKeys(String malicious) {
        assertThrows(IllegalArgumentException.class, () -> jiraClient.getSubtasks(malicious));
        assertTrue(jiraClient.capturedJql.isEmpty(), "no JQL should have been sent to Jira for: " + malicious);
    }

    @Test
    @DisplayName("rejects a null parent key")
    void rejectsNullKey() {
        assertThrows(IllegalArgumentException.class, () -> jiraClient.getSubtasks(null));
        assertTrue(jiraClient.capturedJql.isEmpty());
    }

    @Test
    @DisplayName("builds a quoted JQL clause and succeeds for a well-formed issue key")
    void allowsValidIssueKey() {
        List<Map<String, Object>> result = jiraClient.getSubtasks("ABC-123");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(List.of("parent = \"ABC-123\""), jiraClient.capturedJql);
    }
}
