package com.leadboard.config.controller;

import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.config.ObservabilityMetrics;
import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.entity.StatusKind;
import com.leadboard.config.entity.StatusMappingEntity;
import com.leadboard.config.entity.WorkflowRoleEntity;
import com.leadboard.config.repository.IssueTypeMappingRepository;
import com.leadboard.config.repository.StatusMappingRepository;
import com.leadboard.config.repository.WorkflowRoleRepository;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.status.StatusCategory;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F92 — /api/config/workflow/status-styles must serve resolved colors
 * (override > role+kind > category default) plus statusKind.
 */
@WebMvcTest(PublicConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowConfigService workflowConfigService;
    @MockBean
    private IssueTypeMappingRepository issueTypeRepo;
    @MockBean
    private StatusMappingRepository statusMappingRepo;
    @MockBean
    private WorkflowRoleRepository workflowRoleRepo;
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
    void getStatusStyles_derivesColorFromRoleAndKind_whenNoOverride() throws Exception {
        when(workflowConfigService.getAllConfigIds()).thenReturn(List.of(1L));

        StatusMappingEntity mapping = new StatusMappingEntity();
        mapping.setJiraStatusName("In Progress");
        mapping.setIssueCategory(BoardCategory.STORY);
        mapping.setStatusCategory(StatusCategory.IN_PROGRESS);
        mapping.setWorkflowRoleCode("DEV");
        mapping.setColor(null);
        mapping.setStatusKind(StatusKind.WORK);
        when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of(mapping));

        WorkflowRoleEntity devRole = new WorkflowRoleEntity();
        devRole.setCode("DEV");
        devRole.setColor("#10b981");
        when(workflowRoleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of(devRole));

        mockMvc.perform(get("/api/config/workflow/status-styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['In Progress'].color").value("#abe7d3"))
                .andExpect(jsonPath("$['In Progress'].statusKind").value("WORK"))
                .andExpect(jsonPath("$['In Progress'].statusCategory").value("IN_PROGRESS"));
    }

    @Test
    void getStatusStyles_keepsManualOverrideColor() throws Exception {
        when(workflowConfigService.getAllConfigIds()).thenReturn(List.of(1L));

        StatusMappingEntity mapping = new StatusMappingEntity();
        mapping.setJiraStatusName("Blocked");
        mapping.setIssueCategory(BoardCategory.STORY);
        mapping.setStatusCategory(StatusCategory.IN_PROGRESS);
        mapping.setWorkflowRoleCode("DEV");
        mapping.setColor("#ff0000");
        mapping.setStatusKind(StatusKind.WORK);
        when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of(mapping));

        WorkflowRoleEntity devRole = new WorkflowRoleEntity();
        devRole.setCode("DEV");
        devRole.setColor("#10b981");
        when(workflowRoleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of(devRole));

        mockMvc.perform(get("/api/config/workflow/status-styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Blocked'].color").value("#ff0000"))
                .andExpect(jsonPath("$['Blocked'].statusKind").value("WORK"));
    }

    @Test
    void getStatusStyles_nullStatusKind_isSerializedAsJsonNull() throws Exception {
        when(workflowConfigService.getAllConfigIds()).thenReturn(List.of(1L));

        StatusMappingEntity mapping = new StatusMappingEntity();
        mapping.setJiraStatusName("Done");
        mapping.setIssueCategory(BoardCategory.STORY);
        mapping.setStatusCategory(StatusCategory.DONE);
        mapping.setWorkflowRoleCode(null);
        mapping.setColor(null);
        mapping.setStatusKind(null);
        when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of(mapping));
        when(workflowRoleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/config/workflow/status-styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Done'].color").value("#E3FCEF"))
                .andExpect(jsonPath("$['Done'].statusKind").value(Matchers.nullValue()));
    }
}
