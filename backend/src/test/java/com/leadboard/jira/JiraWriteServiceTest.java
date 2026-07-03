package com.leadboard.jira;

import com.leadboard.auth.OAuthService;
import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraWriteServiceTest {

    @Mock private JiraClient jiraClient;
    @Mock private OAuthService oauthService;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private JiraConfigResolver configResolver;

    private JiraWriteService service;

    @BeforeEach
    void setUp() {
        service = new JiraWriteService(jiraClient, oauthService, workflowConfigService, configResolver);
    }

    private void withOAuth() {
        when(oauthService.getValidAccessToken()).thenReturn("token");
        when(oauthService.getCloudIdForCurrentUser()).thenReturn("cloud");
    }

    private void withoutOAuth() {
        when(oauthService.getValidAccessToken()).thenReturn(null);
        when(oauthService.getCloudIdForCurrentUser()).thenReturn(null);
    }

    private JiraTransition inProgressTransition() {
        return new JiraTransition("31", "Start",
                new JiraTransition.TransitionTarget("3", "In Progress",
                        new JiraTransition.StatusCategoryInfo("indeterminate", "In Progress")));
    }

    @Nested
    @DisplayName("hasUserCreds()")
    class HasUserCreds {
        @Test
        void trueWhenTokenPresent() {
            withOAuth();
            assertTrue(service.hasUserCreds());
        }

        @Test
        void falseWhenNoToken() {
            withoutOAuth();
            assertFalse(service.hasUserCreds());
        }
    }

    @Nested
    @DisplayName("transitionWithFallback()")
    class TransitionFallback {
        @Test
        @DisplayName("uses OAuth path when the user has a token")
        void oauthPath() {
            withOAuth();
            when(jiraClient.getTransitions(eq("LB-1"), anyString(), anyString()))
                    .thenReturn(List.of(inProgressTransition()));

            String result = service.transitionWithFallback("LB-1", "In Progress");

            assertEquals("In Progress", result);
            verify(jiraClient).transitionIssue(eq("LB-1"), eq("31"), anyString(), anyString());
            verify(jiraClient, never()).transitionIssueBasicAuth(anyString(), anyString());
        }

        @Test
        @DisplayName("falls back to BasicAuth twins when no token")
        void basicAuthPath() {
            withoutOAuth();
            when(jiraClient.getTransitionsBasicAuth("LB-2"))
                    .thenReturn(List.of(inProgressTransition()));

            String result = service.transitionWithFallback("LB-2", "In Progress");

            assertEquals("In Progress", result);
            verify(jiraClient).transitionIssueBasicAuth("LB-2", "31");
            verify(jiraClient, never()).transitionIssue(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("throws with the available statuses when no transition matches")
        void noMatch() {
            withoutOAuth();
            when(jiraClient.getTransitionsBasicAuth("LB-3"))
                    .thenReturn(List.of(inProgressTransition()));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.transitionWithFallback("LB-3", "Done"));
            assertTrue(ex.getMessage().contains("In Progress"));
        }
    }

    @Nested
    @DisplayName("listTransitionsWithFallback()")
    class ListTransitionsFallback {
        @Test
        @DisplayName("reads via OAuth when the user has a token, without writing")
        void oauthPath() {
            withOAuth();
            when(jiraClient.getTransitions(eq("LB-1"), anyString(), anyString()))
                    .thenReturn(List.of(inProgressTransition()));

            List<JiraTransition> result = service.listTransitionsWithFallback("LB-1");

            assertEquals(1, result.size());
            assertEquals("In Progress", result.get(0).to().name());
            verify(jiraClient, never()).getTransitionsBasicAuth(anyString());
            verify(jiraClient, never()).transitionIssue(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("falls back to BasicAuth read when no token")
        void basicAuthPath() {
            withoutOAuth();
            when(jiraClient.getTransitionsBasicAuth("LB-2"))
                    .thenReturn(List.of(inProgressTransition()));

            List<JiraTransition> result = service.listTransitionsWithFallback("LB-2");

            assertEquals(1, result.size());
            verify(jiraClient).getTransitionsBasicAuth("LB-2");
            verify(jiraClient, never()).getTransitions(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("assignWithFallback()")
    class AssignFallback {
        @Test
        void oauthPath() {
            withOAuth();
            service.assignWithFallback("LB-1", "acc-1");
            verify(jiraClient).assignIssue(eq("LB-1"), eq("acc-1"), anyString(), anyString());
            verify(jiraClient, never()).assignIssueBasicAuth(anyString(), anyString());
        }

        @Test
        void basicAuthPath() {
            withoutOAuth();
            service.assignWithFallback("LB-1", "acc-1");
            verify(jiraClient).assignIssueBasicAuth("LB-1", "acc-1");
            verify(jiraClient, never()).assignIssue(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("logWorkWithFallback()")
    class LogWorkFallback {
        @Test
        void oauthPath() {
            withOAuth();
            LocalDate date = LocalDate.of(2026, 1, 15);
            service.logWorkWithFallback("LB-1", 3600, date);
            verify(jiraClient).addWorklog(eq("LB-1"), eq(3600), eq(date), anyString(), anyString());
            verify(jiraClient, never()).addWorklogBasicAuth(anyString(), anyInt(), any());
        }

        @Test
        void basicAuthPath() {
            withoutOAuth();
            LocalDate date = LocalDate.of(2026, 1, 15);
            service.logWorkWithFallback("LB-1", 3600, date);
            verify(jiraClient).addWorklogBasicAuth("LB-1", 3600, date);
            verify(jiraClient, never()).addWorklog(anyString(), anyInt(), any(), anyString(), anyString());
        }
    }
}
