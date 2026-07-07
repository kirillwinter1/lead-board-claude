package com.leadboard.team;

import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import com.leadboard.team.dto.MyWorkResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MyWorkController.class)
@AutoConfigureMockMvc(addFilters = false)
class MyWorkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MyWorkService myWorkService;

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
}
