package com.leadboard.config.service;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.entity.ProjectConfigurationEntity;
import com.leadboard.config.entity.StatusMappingEntity;
import com.leadboard.config.repository.IssueTypeMappingRepository;
import com.leadboard.config.repository.LinkTypeMappingRepository;
import com.leadboard.config.repository.ProjectConfigurationRepository;
import com.leadboard.config.repository.StatusMappingRepository;
import com.leadboard.config.repository.WorkflowRoleRepository;
import com.leadboard.status.StatusCategory;
import com.leadboard.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug reproduction: the WorkflowConfigService cache is a single slot shared by all
 * tenants (one set of maps + currentlyLoadedTenantId), not per-tenant as the class
 * comment ("Per-tenant cache: tenantId → loaded flag") and the BUG-60 javadoc on
 * ensureLoaded() promise. When tenant B triggers ensureLoaded(), the shared maps are
 * cleared and refilled with B's config — a thread still working under tenant A's
 * context (e.g. a long-running sync) silently reads B's mappings from that point on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowConfigService per-tenant cache isolation")
class WorkflowConfigTenantCacheTest {

    @Mock private ProjectConfigurationRepository configRepo;
    @Mock private WorkflowRoleRepository roleRepo;
    @Mock private IssueTypeMappingRepository issueTypeRepo;
    @Mock private StatusMappingRepository statusMappingRepo;
    @Mock private LinkTypeMappingRepository linkTypeRepo;
    @Mock private JiraConfigResolver jiraConfigResolver;

    private WorkflowConfigService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowConfigService(
                configRepo, roleRepo, issueTypeRepo, statusMappingRepo, linkTypeRepo,
                new ObjectMapper(), jiraConfigResolver);

        // Default-only config path: no project keys, one default configuration (id=1).
        // Both tenant schemas legitimately have config id=1 (BIGSERIAL per schema).
        when(jiraConfigResolver.getAllProjectKeys()).thenReturn(List.of());
        ProjectConfigurationEntity config = mock(ProjectConfigurationEntity.class);
        when(config.getId()).thenReturn(1L);
        when(config.isDefault()).thenReturn(true);
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config));
        when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of());
        when(linkTypeRepo.findByConfigId(1L)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("tenant A keeps reading its own status config after tenant B triggers a reload")
    void tenantAConfigSurvivesTenantBReload() {
        // Tenant A: status "Verification" is a DONE status for epics.
        TenantContext.setTenant(1L, "tenant_a");
        when(statusMappingRepo.findByConfigId(1L))
                .thenReturn(List.of(epicMapping("Verification", StatusCategory.DONE)));
        service.ensureLoaded();
        assertEquals(StatusCategory.DONE, service.categorizeEpic("Verification"),
                "sanity: tenant A's own mapping must resolve to DONE");

        // Tenant B interleaves (any HTTP request while A's sync is running): same
        // config id in its own schema, but "Verification" means IN_PROGRESS there.
        TenantContext.setTenant(2L, "tenant_b");
        when(statusMappingRepo.findByConfigId(1L))
                .thenReturn(List.of(epicMapping("Verification", StatusCategory.IN_PROGRESS)));
        service.ensureLoaded();

        // Tenant A's thread continues under its own context and reads the config again
        // (real callers: DataQualityService, RetrospectiveTimelineService — categorizeEpic
        // performs no ensureLoaded()).
        TenantContext.setTenant(1L, "tenant_a");
        assertEquals(StatusCategory.DONE, service.categorizeEpic("Verification"),
                "tenant A must see its own config, not tenant B's reload");
    }

    private StatusMappingEntity epicMapping(String statusName, StatusCategory category) {
        StatusMappingEntity m = new StatusMappingEntity();
        m.setJiraStatusName(statusName);
        m.setIssueCategory(BoardCategory.EPIC);
        m.setStatusCategory(category);
        m.setSortOrder(0);
        m.setScoreWeight(0);
        return m;
    }
}
