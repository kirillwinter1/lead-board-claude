package com.leadboard.config.service;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.status.StatusCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for getDefaultRoleCode() and determinePhase() — verifying no hardcoded role codes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowConfigService: getDefaultRoleCode & determinePhase")
class WorkflowConfigServicePhaseTest {

    @Mock private ProjectConfigurationRepository configRepo;
    @Mock private WorkflowRoleRepository roleRepo;
    @Mock private IssueTypeMappingRepository issueTypeRepo;
    @Mock private StatusMappingRepository statusMappingRepo;
    @Mock private LinkTypeMappingRepository linkTypeRepo;
    @Mock private JiraConfigResolver jiraConfigResolver;

    private WorkflowConfigService service;

    private ProjectConfigurationEntity defaultConfig;

    @BeforeEach
    void setUp() {
        when(jiraConfigResolver.getProjectKey()).thenReturn("TEST");
        when(jiraConfigResolver.getAllProjectKeys()).thenReturn(List.of("TEST"));

        defaultConfig = new ProjectConfigurationEntity();
        defaultConfig.setId(1L);
        defaultConfig.setProjectKey("TEST");
        defaultConfig.setDefault(true);
        when(configRepo.findByProjectKey("TEST")).thenReturn(Optional.of(defaultConfig));
        when(configRepo.findAllByProjectKeyIn(List.of("TEST"))).thenReturn(new ArrayList<>(List.of(defaultConfig)));

        when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of());
        when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of());
        when(linkTypeRepo.findByConfigId(1L)).thenReturn(List.of());
    }

    private WorkflowRoleEntity createRole(String code, String displayName, int sortOrder, boolean isDefault) {
        WorkflowRoleEntity role = new WorkflowRoleEntity();
        role.setCode(code);
        role.setDisplayName(displayName);
        role.setSortOrder(sortOrder);
        role.setDefault(isDefault);
        role.setConfigId(1L);
        return role;
    }

    private IssueTypeMappingEntity createTypeMapping(String typeName, BoardCategory category, String roleCode) {
        IssueTypeMappingEntity m = new IssueTypeMappingEntity();
        m.setConfigId(1L);
        m.setJiraTypeName(typeName);
        m.setBoardCategory(category);
        m.setWorkflowRoleCode(roleCode);
        return m;
    }

    private StatusMappingEntity createStatusMapping(BoardCategory issueCategory, String statusName,
                                                     StatusCategory statusCategory, String roleCode) {
        StatusMappingEntity sm = new StatusMappingEntity();
        sm.setConfigId(1L);
        sm.setIssueCategory(issueCategory);
        sm.setJiraStatusName(statusName);
        sm.setStatusCategory(statusCategory);
        sm.setWorkflowRoleCode(roleCode);
        sm.setSortOrder(0);
        sm.setScoreWeight(0);
        return sm;
    }

    private void initService(List<WorkflowRoleEntity> roles) {
        when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(roles);
        service = new WorkflowConfigService(
                configRepo, roleRepo, issueTypeRepo, statusMappingRepo,
                linkTypeRepo, new ObjectMapper(), jiraConfigResolver);
        // @PostConstruct is not invoked outside Spring — trigger load manually
        service.init();
    }

    // ==================== getDefaultRoleCode ====================

    @Nested
    @DisplayName("getDefaultRoleCode()")
    class GetDefaultRoleCode {

        @Test
        @DisplayName("returns the role marked isDefault=true")
        void returnsExplicitDefault() {
            initService(List.of(
                    createRole("ANALYSIS", "Analyst", 1, false),
                    createRole("IMPL", "Implementation", 2, true),
                    createRole("VERIFY", "Verification", 3, false)
            ));

            assertEquals("IMPL", service.getDefaultRoleCode());
        }

        @Test
        @DisplayName("returns first role by sort order when no role is marked default")
        void returnsFirstBySortOrderWhenNoDefault() {
            initService(List.of(
                    createRole("ANALYSIS", "Analyst", 1, false),
                    createRole("IMPL", "Implementation", 2, false),
                    createRole("VERIFY", "Verification", 3, false)
            ));

            assertEquals("ANALYSIS", service.getDefaultRoleCode());
        }

        @Test
        @DisplayName("returns null when no roles are configured")
        void returnsNullWhenNoRoles() {
            initService(List.of());

            assertNull(service.getDefaultRoleCode());
        }

        @Test
        @DisplayName("works with non-standard role codes (no hardcoded DEV)")
        void worksWithCustomRoleCodes() {
            initService(List.of(
                    createRole("FRONTEND", "Frontend Dev", 1, true)
            ));

            assertEquals("FRONTEND", service.getDefaultRoleCode());
        }
    }

    // ==================== determinePhase ====================

    @Nested
    @DisplayName("determinePhase()")
    class DeterminePhase {

        @Test
        @DisplayName("returns role from status mapping when status is mapped")
        void returnsRoleFromStatusMapping() {
            when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of(
                    createStatusMapping(BoardCategory.STORY, "In Analysis", StatusCategory.IN_PROGRESS, "ANALYSIS"),
                    createStatusMapping(BoardCategory.STORY, "In Development", StatusCategory.IN_PROGRESS, "IMPL"),
                    createStatusMapping(BoardCategory.STORY, "In Testing", StatusCategory.IN_PROGRESS, "VERIFY")
            ));
            initService(List.of(
                    createRole("ANALYSIS", "Analyst", 1, false),
                    createRole("IMPL", "Implementation", 2, true),
                    createRole("VERIFY", "Verification", 3, false)
            ));

            assertEquals("ANALYSIS", service.determinePhase("In Analysis", null));
            assertEquals("IMPL", service.determinePhase("In Development", null));
            assertEquals("VERIFY", service.determinePhase("In Testing", null));
        }

        @Test
        @DisplayName("returns role from type mapping for subtasks")
        void returnsRoleFromTypeMapping() {
            when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of(
                    createTypeMapping("Analysis Task", BoardCategory.SUBTASK, "ANALYSIS"),
                    createTypeMapping("Dev Task", BoardCategory.SUBTASK, "IMPL")
            ));
            initService(List.of(
                    createRole("ANALYSIS", "Analyst", 1, false),
                    createRole("IMPL", "Implementation", 2, true)
            ));

            assertEquals("ANALYSIS", service.determinePhase(null, "Analysis Task"));
            assertEquals("IMPL", service.determinePhase(null, "Dev Task"));
        }

        @Test
        @DisplayName("uses role displayName for fuzzy status matching")
        void usesRoleDisplayNameForFuzzyStatusMatch() {
            // No explicit status mappings — should fall back to fuzzy matching
            initService(List.of(
                    createRole("ANALYSIS", "Analyst", 1, false),
                    createRole("IMPL", "Developer", 2, true),
                    createRole("VERIFY", "Tester", 3, false)
            ));

            // Status "Code Analysis Phase" contains "Analyst" (displayName) — should match ANALYSIS
            assertEquals("ANALYSIS", service.determinePhase("Analyst Review", null));
            // Status "Developer Review" contains "Developer" (displayName) — should match IMPL
            assertEquals("IMPL", service.determinePhase("Developer Review", null));
            // Status "Tester Validation" contains "Tester" (displayName) — should match VERIFY
            assertEquals("VERIFY", service.determinePhase("Tester Validation", null));
        }

        @Test
        @DisplayName("uses role code for fuzzy status matching")
        void usesRoleCodeForFuzzyStatusMatch() {
            initService(List.of(
                    createRole("SA", "System Analyst", 1, false),
                    createRole("DEV", "Developer", 2, true),
                    createRole("QA", "Quality Assurance", 3, false)
            ));

            // Status contains role code "SA"
            assertEquals("SA", service.determinePhase("In SA Review", null));
            // Status contains role code "QA"
            assertEquals("QA", service.determinePhase("QA Testing", null));
        }

        @Test
        @DisplayName("uses role displayName for fuzzy issue type matching")
        void usesRoleDisplayNameForFuzzyTypeMatch() {
            initService(List.of(
                    createRole("ANALYSIS", "Аналитик", 1, false),
                    createRole("IMPL", "Разработчик", 2, true),
                    createRole("VERIFY", "Тестировщик", 3, false)
            ));

            // Issue type contains displayName (Cyrillic)
            assertEquals("ANALYSIS", service.determinePhase(null, "Задача Аналитика"));
            assertEquals("VERIFY", service.determinePhase(null, "Задача Тестировщика"));
        }

        @Test
        @DisplayName("bugs default to last role in pipeline")
        void bugsDefaultToLastPipelineRole() {
            when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of(
                    createTypeMapping("Bug", BoardCategory.BUG, null)
            ));
            initService(List.of(
                    createRole("ANALYSIS", "Analyst", 1, false),
                    createRole("IMPL", "Developer", 2, true),
                    createRole("VERIFY", "Tester", 3, false)
            ));

            // Bug issue type should fall back to last role (VERIFY)
            assertEquals("VERIFY", service.determinePhase(null, "Bug"));
        }

        @Test
        @DisplayName("returns default role when nothing matches")
        void returnsDefaultRoleWhenNothingMatches() {
            initService(List.of(
                    createRole("ANALYSIS", "Analyst", 1, false),
                    createRole("IMPL", "Developer", 2, true)
            ));

            // Completely unrelated status and type — should return default role (IMPL, marked isDefault)
            assertEquals("IMPL", service.determinePhase("Unknown Status", "Unknown Type"));
        }

        @Test
        @DisplayName("returns null when no roles configured and nothing matches")
        void returnsNullWhenNoRolesConfigured() {
            initService(List.of());

            assertNull(service.determinePhase("Unknown Status", "Unknown Type"));
        }

        @Test
        @DisplayName("returns default role code when both status and type are null")
        void returnsDefaultWhenBothNull() {
            initService(List.of(
                    createRole("IMPL", "Developer", 1, true)
            ));

            assertEquals("IMPL", service.determinePhase(null, null));
        }

        @Test
        @DisplayName("case-insensitive status matching in status mappings")
        void caseInsensitiveStatusMatching() {
            when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of(
                    createStatusMapping(BoardCategory.STORY, "In Review", StatusCategory.IN_PROGRESS, "REVIEW_ROLE")
            ));
            initService(List.of(
                    createRole("REVIEW_ROLE", "Reviewer", 1, true)
            ));

            assertEquals("REVIEW_ROLE", service.determinePhase("in review", null));
        }
    }
}
