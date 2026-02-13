package com.leadboard.config;

import com.leadboard.auth.SessionRepository;
import com.leadboard.config.controller.WorkflowConfigController;
import com.leadboard.config.dto.*;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.config.service.MappingValidationService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.status.StatusCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkflowConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectConfigurationRepository configRepo;
    @MockBean
    private WorkflowRoleRepository roleRepo;
    @MockBean
    private IssueTypeMappingRepository issueTypeRepo;
    @MockBean
    private StatusMappingRepository statusMappingRepo;
    @MockBean
    private LinkTypeMappingRepository linkTypeRepo;
    @MockBean
    private WorkflowConfigService workflowConfigService;
    @MockBean
    private MappingValidationService validationService;
    @MockBean
    private SessionRepository sessionRepository;
    @MockBean
    private AppProperties appProperties;

    @Test
    void getConfig_returnsFullConfiguration() throws Exception {
        ProjectConfigurationEntity config = createConfig(1L, "Default");
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config));

        WorkflowRoleEntity role = new WorkflowRoleEntity();
        role.setId(1L);
        role.setConfigId(1L);
        role.setCode("SA");
        role.setDisplayName("System Analyst");
        role.setColor("#4A90D9");
        role.setSortOrder(1);
        role.setDefault(false);
        role.setCreatedAt(OffsetDateTime.now());
        when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of(role));

        when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of());
        when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of());
        when(linkTypeRepo.findByConfigId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/workflow-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").value(1))
                .andExpect(jsonPath("$.configName").value("Default"))
                .andExpect(jsonPath("$.roles[0].code").value("SA"))
                .andExpect(jsonPath("$.roles[0].displayName").value("System Analyst"));
    }

    @Test
    void getConfig_returns404WhenNoConfig() throws Exception {
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/workflow-config"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRoles_returnsList() throws Exception {
        ProjectConfigurationEntity config = createConfig(1L, "Default");
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config));

        WorkflowRoleEntity role = new WorkflowRoleEntity();
        role.setId(1L);
        role.setConfigId(1L);
        role.setCode("DEV");
        role.setDisplayName("Developer");
        role.setSortOrder(2);
        role.setDefault(true);
        role.setCreatedAt(OffsetDateTime.now());
        when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of(role));

        mockMvc.perform(get("/api/admin/workflow-config/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("DEV"))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    void updateRoles_replacesAndReloadsCache() throws Exception {
        ProjectConfigurationEntity config = createConfig(1L, "Default");
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config));
        when(roleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());

        List<WorkflowRoleDto> roles = List.of(
                new WorkflowRoleDto(null, "SA", "Analyst", "#blue", 1, false),
                new WorkflowRoleDto(null, "DEV", "Developer", "#green", 2, true)
        );

        mockMvc.perform(put("/api/admin/workflow-config/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roles)))
                .andExpect(status().isOk());

        verify(roleRepo).deleteByConfigId(1L);
        verify(roleRepo, times(2)).save(any());
        verify(workflowConfigService).clearCache();
    }

    @Test
    void getIssueTypes_returnsList() throws Exception {
        ProjectConfigurationEntity config = createConfig(1L, "Default");
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config));

        IssueTypeMappingEntity mapping = new IssueTypeMappingEntity();
        mapping.setId(1L);
        mapping.setConfigId(1L);
        mapping.setJiraTypeName("Epic");
        mapping.setBoardCategory(BoardCategory.EPIC);
        mapping.setCreatedAt(OffsetDateTime.now());
        when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of(mapping));

        mockMvc.perform(get("/api/admin/workflow-config/issue-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jiraTypeName").value("Epic"))
                .andExpect(jsonPath("$[0].boardCategory").value("EPIC"));
    }

    @Test
    void validate_returnsValidationResult() throws Exception {
        ProjectConfigurationEntity config = createConfig(1L, "Default");
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config));
        when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of());
        when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of());
        when(linkTypeRepo.findByConfigId(1L)).thenReturn(List.of());

        when(validationService.validate(any(), any(), any(), any()))
                .thenReturn(ValidationResult.withIssues(
                        List.of("At least one workflow role is required"),
                        List.of("No link type mappings configured")));

        mockMvc.perform(post("/api/admin/workflow-config/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]").value("At least one workflow role is required"))
                .andExpect(jsonPath("$.warnings[0]").value("No link type mappings configured"));
    }

    private ProjectConfigurationEntity createConfig(Long id, String name) {
        ProjectConfigurationEntity config = new ProjectConfigurationEntity();
        config.setId(id);
        config.setName(name);
        config.setDefault(true);
        config.setCreatedAt(OffsetDateTime.now());
        config.setUpdatedAt(OffsetDateTime.now());
        return config;
    }
}
