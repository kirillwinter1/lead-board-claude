package com.leadboard.team;

import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.jira.JiraWriteService;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import com.leadboard.team.dto.MyWorkResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(MyWorkController.class)
@AutoConfigureMockMvc(addFilters = false)
class MyWorkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MyWorkService myWorkService;

    @MockBean
    private MyWorklogService myWorklogService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private SessionRepository sessionRepository;
    @MockBean
    private AppProperties appProperties;
    @MockBean
    private TenantUserRepository tenantUserRepository;
    @MockBean
    private TenantRepository tenantRepository;
    @MockBean
    private com.leadboard.config.ObservabilityMetrics observabilityMetrics;

    private static final MyWorkResponse EMPTY_RESPONSE =
            new MyWorkResponse(true, null, java.util.List.of(), java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), null);

    @Test
    void returnsMyWorkForCurrentUser() throws Exception {
        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getAtlassianAccountId()).thenReturn("acc-1");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        when(myWorkService.getMyWork(eq("acc-1"), eq(LocalDate.parse("2026-04-01")),
                eq(LocalDate.parse("2026-07-01")), isNull())).thenReturn(EMPTY_RESPONSE);

        mockMvc.perform(get("/api/me/work").param("from", "2026-04-01").param("to", "2026-07-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMembership").value(true));
    }

    @Test
    void passesTeamIdFilter() throws Exception {
        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getAtlassianAccountId()).thenReturn("acc-1");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        when(myWorkService.getMyWork(eq("acc-1"), eq(LocalDate.parse("2026-04-01")),
                eq(LocalDate.parse("2026-07-01")), eq(2L))).thenReturn(EMPTY_RESPONSE);

        mockMvc.perform(get("/api/me/work")
                        .param("from", "2026-04-01")
                        .param("to", "2026-07-01")
                        .param("teamId", "2"))
                .andExpect(status().isOk());

        verify(myWorkService).getMyWork(eq("acc-1"), eq(LocalDate.parse("2026-04-01")),
                eq(LocalDate.parse("2026-07-01")), eq(2L));
    }

    @Test
    void returns401WhenAuthMissing() throws Exception {
        when(authorizationService.getCurrentAuth()).thenReturn(null);

        mockMvc.perform(get("/api/me/work").param("from", "2026-04-01").param("to", "2026-07-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logTimeReturns200WithWorklogId() throws Exception {
        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getAtlassianAccountId()).thenReturn("acc-1");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        when(myWorklogService.logTime(eq("acc-1"), eq("LB-1"), eq(LocalDate.parse("2026-07-08")),
                eq(new BigDecimal("2.5")), eq("done")))
                .thenReturn("42");

        mockMvc.perform(post("/api/me/worklog")
                        .contentType(APPLICATION_JSON)
                        .content("{\"issueKey\":\"LB-1\",\"date\":\"2026-07-08\",\"hours\":2.5,\"comment\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.worklogId").value("42"));

        verify(myWorklogService).logTime(eq("acc-1"), eq("LB-1"), eq(LocalDate.parse("2026-07-08")),
                eq(new BigDecimal("2.5")), eq("done"));
    }

    @Test
    void logTimeReturns400OnValidationException() throws Exception {
        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getAtlassianAccountId()).thenReturn("acc-1");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        when(myWorklogService.logTime(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new MyWorklogService.LogTimeValidationException("Hours must be between 0 and 24"));

        mockMvc.perform(post("/api/me/worklog")
                        .contentType(APPLICATION_JSON)
                        .content("{\"issueKey\":\"LB-1\",\"date\":\"2026-07-08\",\"hours\":25,\"comment\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Hours must be between 0 and 24"));
    }

    @Test
    void logTimeReturns403OnForbiddenException() throws Exception {
        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getAtlassianAccountId()).thenReturn("acc-1");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        when(myWorklogService.logTime(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new MyWorklogService.LogTimeForbiddenException("You can log time only on your own tasks"));

        mockMvc.perform(post("/api/me/worklog")
                        .contentType(APPLICATION_JSON)
                        .content("{\"issueKey\":\"LB-1\",\"date\":\"2026-07-08\",\"hours\":2.5,\"comment\":\"\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You can log time only on your own tasks"));
    }

    @Test
    void logTimeReturns409OnNoUserTokenException() throws Exception {
        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getAtlassianAccountId()).thenReturn("acc-1");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        when(myWorklogService.logTime(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new JiraWriteService.NoUserTokenException("no token"));

        mockMvc.perform(post("/api/me/worklog")
                        .contentType(APPLICATION_JSON)
                        .content("{\"issueKey\":\"LB-1\",\"date\":\"2026-07-08\",\"hours\":2.5,\"comment\":\"\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Jira session expired — re-login via Atlassian"));
    }

    @Test
    void logTimeReturns502OnWebClientResponseException() throws Exception {
        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getAtlassianAccountId()).thenReturn("acc-1");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        when(myWorklogService.logTime(anyString(), anyString(), any(), any(), any()))
                .thenThrow(WebClientResponseException.create(500, "err", HttpHeaders.EMPTY, new byte[0], null));

        mockMvc.perform(post("/api/me/worklog")
                        .contentType(APPLICATION_JSON)
                        .content("{\"issueKey\":\"LB-1\",\"date\":\"2026-07-08\",\"hours\":2.5,\"comment\":\"\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Jira API error"));
    }

    @Test
    void logTimeReturns502OnWebClientRequestException() throws Exception {
        // Network-level failure (DNS/timeout) — WebClientRequestException, a sibling of
        // WebClientResponseException under the common WebClientException parent the
        // controller now catches (F90 review fix).
        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getAtlassianAccountId()).thenReturn("acc-1");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        when(myWorklogService.logTime(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new WebClientRequestException(
                        new java.io.IOException("boom"), HttpMethod.POST, URI.create("https://api.atlassian.com/x"), HttpHeaders.EMPTY));

        mockMvc.perform(post("/api/me/worklog")
                        .contentType(APPLICATION_JSON)
                        .content("{\"issueKey\":\"LB-1\",\"date\":\"2026-07-08\",\"hours\":2.5,\"comment\":\"\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Jira API error"));
    }

    @Test
    void logTimeReturns502WithNoRetryWarningOnJiraNoIdException() throws Exception {
        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getAtlassianAccountId()).thenReturn("acc-1");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        when(myWorklogService.logTime(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new MyWorklogService.JiraNoIdException(
                        "Jira did not confirm the worklog — check Jira before retrying"));

        mockMvc.perform(post("/api/me/worklog")
                        .contentType(APPLICATION_JSON)
                        .content("{\"issueKey\":\"LB-1\",\"date\":\"2026-07-08\",\"hours\":2.5,\"comment\":\"\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Jira did not confirm the worklog — check Jira before retrying"));
    }
}
