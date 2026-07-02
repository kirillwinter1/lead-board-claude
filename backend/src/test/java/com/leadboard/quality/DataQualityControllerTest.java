package com.leadboard.quality;

import com.leadboard.config.AppProperties;
import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.quality.fix.FixService;
import com.leadboard.status.StatusAgeService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.auth.SessionRepository;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataQualityController.class)
@AutoConfigureMockMvc(addFilters = false)
class DataQualityControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private JiraIssueRepository issueRepository;
    @MockBean private JiraConfigResolver jiraConfigResolver;
    @MockBean private DataQualityService dataQualityService;
    @MockBean private WorkflowConfigService workflowConfigService;
    @MockBean private StatusAgeService statusAgeService;
    @MockBean private FixService fixService;

    // Security / infrastructure beans pulled in by the slice
    @MockBean private SessionRepository sessionRepository;
    @MockBean private AppProperties appProperties;
    @MockBean private TenantUserRepository tenantUserRepository;
    @MockBean private TenantRepository tenantRepository;
    @MockBean private com.leadboard.config.ObservabilityMetrics observabilityMetrics;

    @Test
    void reportMarksFixableViolations() throws Exception {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("LB-1");
        epic.setIssueType("Epic");
        epic.setSummary("Epic");
        epic.setStatus("Planned");

        when(jiraConfigResolver.getActiveProjectKeys()).thenReturn(List.of("LB"));
        when(jiraConfigResolver.getBaseUrl()).thenReturn("http://jira");
        when(issueRepository.findByProjectKeyIn(any())).thenReturn(List.of(epic));
        when(statusAgeService.compute(any())).thenReturn(Map.of());
        when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        when(workflowConfigService.isStoryOrBug(anyString())).thenReturn(false);
        when(dataQualityService.loadOpenFlagsByEpicKey(any())).thenReturn(Map.of());
        when(dataQualityService.checkEpic(eq(epic), any(), any(), any()))
                .thenReturn(List.of(DataQualityViolation.of(DataQualityRule.EPIC_NO_TEAM)));
        when(fixService.isFixable(DataQualityRule.EPIC_NO_TEAM)).thenReturn(true);

        mockMvc.perform(get("/api/data-quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.violations[0].violations[0].rule").value("EPIC_NO_TEAM"))
                .andExpect(jsonPath("$.violations[0].violations[0].fixable").value(true));
    }
}
