package com.leadboard.config.controller;

import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.config.ObservabilityMetrics;
import com.leadboard.config.entity.JiraProjectEntity;
import com.leadboard.config.service.JiraProjectService;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers the F84 code-review fix adding {@code @Valid} to {@link JiraProjectController#update}
 * (previously a raw {@code Map<String, Object>} body with no validation at all).
 */
@WebMvcTest(JiraProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class JiraProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JiraProjectService jiraProjectService;

    @MockBean
    private SessionRepository sessionRepository;
    @MockBean
    private AppProperties appProperties;
    @MockBean
    private TenantUserRepository tenantUserRepository;
    @MockBean
    private TenantRepository tenantRepository;
    @MockBean
    private ObservabilityMetrics observabilityMetrics;

    @Test
    void update_blankDisplayName_isBadRequest() throws Exception {
        mockMvc.perform(put("/api/admin/jira-projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jiraProjectService);
    }

    @Test
    void update_validRequest_delegatesToService() throws Exception {
        JiraProjectEntity entity = new JiraProjectEntity();
        entity.setId(1L);
        entity.setProjectKey("ABC");
        entity.setDisplayName("New name");
        entity.setActive(true);
        when(jiraProjectService.update(eq(1L), eq("New name"), eq(true), isNull()))
                .thenReturn(entity);

        mockMvc.perform(put("/api/admin/jira-projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"New name\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectKey").value("ABC"));
    }

    @Test
    void update_nullDisplayName_leavesItUnchanged() throws Exception {
        JiraProjectEntity entity = new JiraProjectEntity();
        entity.setId(1L);
        entity.setProjectKey("ABC");
        when(jiraProjectService.update(eq(1L), isNull(), eq(false), isNull()))
                .thenReturn(entity);

        mockMvc.perform(put("/api/admin/jira-projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        verify(jiraProjectService).update(1L, null, false, null);
    }
}
