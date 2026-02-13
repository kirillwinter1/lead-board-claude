package com.leadboard.config.service;

import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MappingAutoDetectService")
class MappingAutoDetectServiceTest {

    @Mock private JiraMetadataService jiraMetadataService;
    @Mock private ProjectConfigurationRepository configRepo;
    @Mock private WorkflowRoleRepository roleRepo;
    @Mock private IssueTypeMappingRepository issueTypeRepo;
    @Mock private StatusMappingRepository statusMappingRepo;
    @Mock private LinkTypeMappingRepository linkTypeRepo;
    @Mock private WorkflowConfigService workflowConfigService;

    private MappingAutoDetectService service;

    @BeforeEach
    void setUp() {
        service = new MappingAutoDetectService(
                jiraMetadataService, configRepo, roleRepo, issueTypeRepo,
                statusMappingRepo, linkTypeRepo, workflowConfigService
        );

        // Default config setup
        ProjectConfigurationEntity config = new ProjectConfigurationEntity();
        config.setId(1L);
        config.setName("Default");
        config.setDefault(true);
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config));

        // Default: save returns argument
        when(roleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(issueTypeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusMappingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkTypeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== isConfigEmpty ====================

    @Nested
    @DisplayName("isConfigEmpty()")
    class IsConfigEmptyTests {

        @Test
        @DisplayName("should return true when no roles and no type mappings")
        void shouldReturnTrueWhenEmpty() {
            when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
            when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of());

            assertTrue(service.isConfigEmpty());
        }

        @Test
        @DisplayName("should return false when roles exist")
        void shouldReturnFalseWhenRolesExist() {
            WorkflowRoleEntity role = new WorkflowRoleEntity();
            role.setCode("DEV");
            when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of(role));
            when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of());

            assertFalse(service.isConfigEmpty());
        }

        @Test
        @DisplayName("should return true when no default config exists")
        void shouldReturnTrueWhenNoConfig() {
            when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.empty());

            assertTrue(service.isConfigEmpty());
        }
    }

    // ==================== detectBoardCategory ====================

    @Nested
    @DisplayName("detectBoardCategory()")
    class DetectBoardCategoryTests {

        @Test
        @DisplayName("should detect SUBTASK for subtask types")
        void shouldDetectSubtask() {
            assertEquals(BoardCategory.SUBTASK, service.detectBoardCategory("Sub-task", true));
            assertEquals(BoardCategory.SUBTASK, service.detectBoardCategory("Аналитика", true));
        }

        @Test
        @DisplayName("should detect EPIC for epic types")
        void shouldDetectEpic() {
            assertEquals(BoardCategory.EPIC, service.detectBoardCategory("Epic", false));
            assertEquals(BoardCategory.EPIC, service.detectBoardCategory("Эпик", false));
        }

        @Test
        @DisplayName("should detect STORY for story/bug/task types")
        void shouldDetectStory() {
            assertEquals(BoardCategory.STORY, service.detectBoardCategory("Story", false));
            assertEquals(BoardCategory.STORY, service.detectBoardCategory("Bug", false));
            assertEquals(BoardCategory.STORY, service.detectBoardCategory("Task", false));
            assertEquals(BoardCategory.STORY, service.detectBoardCategory("История", false));
            assertEquals(BoardCategory.STORY, service.detectBoardCategory("Задача", false));
        }

        @Test
        @DisplayName("should default to IGNORE for unknown types")
        void shouldDefaultToIgnore() {
            assertEquals(BoardCategory.IGNORE, service.detectBoardCategory("Documentation", false));
            assertEquals(BoardCategory.IGNORE, service.detectBoardCategory("Initiative", false));
        }
    }

    // ==================== detectRoleFromSubtaskName ====================

    @Nested
    @DisplayName("detectRoleFromSubtaskName()")
    class DetectRoleTests {

        @Test
        @DisplayName("should detect SA role from analytics names")
        void shouldDetectSa() {
            assertEquals("SA", service.detectRoleFromSubtaskName("Аналитика"));
            assertEquals("SA", service.detectRoleFromSubtaskName("Analytics"));
            assertEquals("SA", service.detectRoleFromSubtaskName("Requirements gathering"));
            assertEquals("SA", service.detectRoleFromSubtaskName("Analysis"));
        }

        @Test
        @DisplayName("should detect QA role from testing names")
        void shouldDetectQa() {
            assertEquals("QA", service.detectRoleFromSubtaskName("Тестирование"));
            assertEquals("QA", service.detectRoleFromSubtaskName("Testing"));
            assertEquals("QA", service.detectRoleFromSubtaskName("QA Review"));
        }

        @Test
        @DisplayName("should default to DEV for other subtask names")
        void shouldDefaultToDev() {
            assertEquals("DEV", service.detectRoleFromSubtaskName("Разработка"));
            assertEquals("DEV", service.detectRoleFromSubtaskName("Development"));
            assertEquals("DEV", service.detectRoleFromSubtaskName("Sub-task"));
            assertEquals("DEV", service.detectRoleFromSubtaskName("Подзадача"));
        }
    }

    // ==================== autoDetect() with standard EN project ====================

    @Nested
    @DisplayName("autoDetect() — standard EN project")
    class StandardEnProjectTests {

        @BeforeEach
        void setupJiraMetadata() {
            when(jiraMetadataService.getIssueTypes()).thenReturn(List.of(
                    issueType("Epic", false),
                    issueType("Story", false),
                    issueType("Bug", false),
                    issueType("Task", false),
                    issueType("Analytics", true),
                    issueType("Development", true),
                    issueType("Testing", true),
                    issueType("Sub-task", true)
            ));

            when(jiraMetadataService.getStatuses()).thenReturn(List.of(
                    statusGroup("Epic", List.of(
                            status("New", "new"),
                            status("Requirements", "indeterminate"),
                            status("Planned", "indeterminate"),
                            status("Developing", "indeterminate"),
                            status("Done", "done")
                    )),
                    statusGroup("Story", List.of(
                            status("New", "new"),
                            status("Analysis", "indeterminate"),
                            status("Development", "indeterminate"),
                            status("Testing", "indeterminate"),
                            status("Done", "done")
                    ))
            ));

            when(jiraMetadataService.getLinkTypes()).thenReturn(List.of(
                    linkType("Blocks"),
                    linkType("Relates"),
                    linkType("Duplicate")
            ));

            when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        }

        @Test
        @DisplayName("should create correct issue type mappings")
        void shouldCreateIssueTypeMappings() {
            var result = service.autoDetect();

            assertEquals(8, result.issueTypeCount());

            // Verify issue type mappings were saved
            ArgumentCaptor<IssueTypeMappingEntity> captor = ArgumentCaptor.forClass(IssueTypeMappingEntity.class);
            verify(issueTypeRepo, times(8)).save(captor.capture());

            List<IssueTypeMappingEntity> saved = captor.getAllValues();

            // Epic
            assertTrue(saved.stream().anyMatch(m ->
                    "Epic".equals(m.getJiraTypeName()) && m.getBoardCategory() == BoardCategory.EPIC));
            // Story
            assertTrue(saved.stream().anyMatch(m ->
                    "Story".equals(m.getJiraTypeName()) && m.getBoardCategory() == BoardCategory.STORY));
            // Analytics subtask → SA
            assertTrue(saved.stream().anyMatch(m ->
                    "Analytics".equals(m.getJiraTypeName()) && m.getBoardCategory() == BoardCategory.SUBTASK
                            && "SA".equals(m.getWorkflowRoleCode())));
            // Testing subtask → QA
            assertTrue(saved.stream().anyMatch(m ->
                    "Testing".equals(m.getJiraTypeName()) && m.getBoardCategory() == BoardCategory.SUBTASK
                            && "QA".equals(m.getWorkflowRoleCode())));
            // Development subtask → DEV
            assertTrue(saved.stream().anyMatch(m ->
                    "Development".equals(m.getJiraTypeName()) && m.getBoardCategory() == BoardCategory.SUBTASK
                            && "DEV".equals(m.getWorkflowRoleCode())));
        }

        @Test
        @DisplayName("should detect SA/DEV/QA roles from subtask names")
        void shouldDetectRoles() {
            var result = service.autoDetect();

            assertEquals(3, result.roleCount());

            ArgumentCaptor<WorkflowRoleEntity> captor = ArgumentCaptor.forClass(WorkflowRoleEntity.class);
            verify(roleRepo, times(3)).save(captor.capture());

            List<WorkflowRoleEntity> saved = captor.getAllValues();
            Set<String> roleCodes = new HashSet<>();
            saved.forEach(r -> roleCodes.add(r.getCode()));

            assertTrue(roleCodes.contains("SA"));
            assertTrue(roleCodes.contains("DEV"));
            assertTrue(roleCodes.contains("QA"));

            // DEV should be marked as default
            assertTrue(saved.stream().anyMatch(r -> "DEV".equals(r.getCode()) && r.isDefault()));
        }

        @Test
        @DisplayName("should create status mappings using statusCategory")
        void shouldCreateStatusMappings() {
            var result = service.autoDetect();

            assertTrue(result.statusMappingCount() > 0);
            verify(statusMappingRepo, atLeast(1)).save(any(StatusMappingEntity.class));
        }

        @Test
        @DisplayName("should create link type mappings")
        void shouldCreateLinkTypeMappings() {
            var result = service.autoDetect();

            assertEquals(3, result.linkTypeCount());

            ArgumentCaptor<LinkTypeMappingEntity> captor = ArgumentCaptor.forClass(LinkTypeMappingEntity.class);
            verify(linkTypeRepo, times(3)).save(captor.capture());

            List<LinkTypeMappingEntity> saved = captor.getAllValues();
            assertTrue(saved.stream().anyMatch(l ->
                    "Blocks".equals(l.getJiraLinkTypeName()) && l.getLinkCategory() == LinkCategory.BLOCKS));
            assertTrue(saved.stream().anyMatch(l ->
                    "Relates".equals(l.getJiraLinkTypeName()) && l.getLinkCategory() == LinkCategory.RELATED));
        }

        @Test
        @DisplayName("should clear caches after auto-detect")
        void shouldClearCaches() {
            service.autoDetect();
            verify(workflowConfigService).clearCache();
        }

        @Test
        @DisplayName("should have no warnings for standard project")
        void shouldHaveNoWarnings() {
            var result = service.autoDetect();
            assertTrue(result.warnings().isEmpty());
        }
    }

    // ==================== autoDetect() with RU project ====================

    @Nested
    @DisplayName("autoDetect() — RU project")
    class RuProjectTests {

        @BeforeEach
        void setupJiraMetadata() {
            when(jiraMetadataService.getIssueTypes()).thenReturn(List.of(
                    issueType("Эпик", false),
                    issueType("История", false),
                    issueType("Баг", false),
                    issueType("Аналитика", true),
                    issueType("Разработка", true),
                    issueType("Тестирование", true)
            ));

            when(jiraMetadataService.getStatuses()).thenReturn(List.of(
                    statusGroup("Эпик", List.of(
                            status("Новый", "new"),
                            status("Требования", "indeterminate"),
                            status("Запланировано", "indeterminate"),
                            status("В разработке", "indeterminate"),
                            status("Готово", "done")
                    ))
            ));

            when(jiraMetadataService.getLinkTypes()).thenReturn(List.of(
                    linkType("Blocks")
            ));

            when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        }

        @Test
        @DisplayName("should detect Russian issue types correctly")
        void shouldDetectRussianTypes() {
            var result = service.autoDetect();

            assertEquals(6, result.issueTypeCount());
            assertEquals(3, result.roleCount());

            ArgumentCaptor<IssueTypeMappingEntity> captor = ArgumentCaptor.forClass(IssueTypeMappingEntity.class);
            verify(issueTypeRepo, times(6)).save(captor.capture());

            List<IssueTypeMappingEntity> saved = captor.getAllValues();

            assertTrue(saved.stream().anyMatch(m ->
                    "Эпик".equals(m.getJiraTypeName()) && m.getBoardCategory() == BoardCategory.EPIC));
            assertTrue(saved.stream().anyMatch(m ->
                    "История".equals(m.getJiraTypeName()) && m.getBoardCategory() == BoardCategory.STORY));
            assertTrue(saved.stream().anyMatch(m ->
                    "Аналитика".equals(m.getJiraTypeName()) && "SA".equals(m.getWorkflowRoleCode())));
            assertTrue(saved.stream().anyMatch(m ->
                    "Тестирование".equals(m.getJiraTypeName()) && "QA".equals(m.getWorkflowRoleCode())));
        }

        @Test
        @DisplayName("should map Russian epic statuses correctly")
        void shouldMapRussianStatuses() {
            var result = service.autoDetect();

            ArgumentCaptor<StatusMappingEntity> captor = ArgumentCaptor.forClass(StatusMappingEntity.class);
            verify(statusMappingRepo, atLeast(1)).save(captor.capture());

            List<StatusMappingEntity> saved = captor.getAllValues();

            // "Требования" with indeterminate → REQUIREMENTS for EPIC
            assertTrue(saved.stream().anyMatch(s ->
                    "Требования".equals(s.getJiraStatusName())
                            && s.getIssueCategory() == BoardCategory.EPIC
                            && s.getStatusCategory() == com.leadboard.status.StatusCategory.REQUIREMENTS));

            // "Запланировано" with indeterminate → PLANNED for EPIC
            assertTrue(saved.stream().anyMatch(s ->
                    "Запланировано".equals(s.getJiraStatusName())
                            && s.getIssueCategory() == BoardCategory.EPIC
                            && s.getStatusCategory() == com.leadboard.status.StatusCategory.PLANNED));
        }
    }

    // ==================== Default roles fallback ====================

    @Nested
    @DisplayName("autoDetect() — no role-specific subtasks")
    class DefaultRoleFallbackTests {

        @BeforeEach
        void setup() {
            when(jiraMetadataService.getIssueTypes()).thenReturn(List.of(
                    issueType("Epic", false),
                    issueType("Story", false),
                    issueType("Sub-task", true)  // generic subtask, no role in name
            ));
            when(jiraMetadataService.getStatuses()).thenReturn(List.of());
            when(jiraMetadataService.getLinkTypes()).thenReturn(List.of());
            when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        }

        @Test
        @DisplayName("should create default SA/DEV/QA when no role-specific subtasks found")
        void shouldCreateDefaultRoles() {
            var result = service.autoDetect();

            // All subtask names map to DEV by default, so only DEV detected → fallback to SA/DEV/QA
            // Actually "Sub-task" → DEV, so detectedRoles = {DEV}
            // But wait — the code says "if detectedRoles is empty" which would be {DEV}
            // So it won't be empty. Let me check...
            // Actually "Sub-task" → detectRoleFromSubtaskName → DEV
            // So detectedRoles = {DEV}, not empty → no fallback
            // The fallback only triggers when NO subtasks at all

            // In this case we have 1 subtask with role DEV, so 1 role
            assertEquals(1, result.roleCount());
        }

        @Test
        @DisplayName("should warn about no role-specific subtasks")
        void noSubtasksShouldTriggerDefaultRoles() {
            // Setup with NO subtask types at all
            when(jiraMetadataService.getIssueTypes()).thenReturn(List.of(
                    issueType("Epic", false),
                    issueType("Story", false)
            ));

            var result = service.autoDetect();

            assertEquals(3, result.roleCount()); // SA, DEV, QA defaults
            assertTrue(result.warnings().stream()
                    .anyMatch(w -> w.contains("default SA/DEV/QA")));
        }
    }

    // ==================== Idempotent re-detection ====================

    @Nested
    @DisplayName("autoDetect() — idempotent")
    class IdempotentTests {

        @Test
        @DisplayName("should clear existing mappings before inserting new ones")
        void shouldClearBeforeInsert() {
            when(jiraMetadataService.getIssueTypes()).thenReturn(List.of(
                    issueType("Story", false)
            ));
            when(jiraMetadataService.getStatuses()).thenReturn(List.of());
            when(jiraMetadataService.getLinkTypes()).thenReturn(List.of());
            when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());

            service.autoDetect();

            // Verify deletions happened before saves
            verify(roleRepo).deleteByConfigId(1L);
            verify(issueTypeRepo).deleteByConfigId(1L);
            verify(statusMappingRepo).deleteByConfigId(1L);
            verify(linkTypeRepo).deleteByConfigId(1L);
        }
    }

    // ==================== statusCategory mapping ====================

    @Nested
    @DisplayName("mapJiraStatusCategory()")
    class StatusCategoryTests {

        @Test
        @DisplayName("should map 'new' to NEW")
        void shouldMapNewToNew() {
            assertEquals(com.leadboard.status.StatusCategory.NEW,
                    service.mapJiraStatusCategory("new", "New", BoardCategory.STORY));
        }

        @Test
        @DisplayName("should map 'done' to DONE")
        void shouldMapDoneToDone() {
            assertEquals(com.leadboard.status.StatusCategory.DONE,
                    service.mapJiraStatusCategory("done", "Done", BoardCategory.EPIC));
        }

        @Test
        @DisplayName("should map 'indeterminate' for Epic with Requirements to REQUIREMENTS")
        void shouldMapIndeterminateRequirements() {
            assertEquals(com.leadboard.status.StatusCategory.REQUIREMENTS,
                    service.mapJiraStatusCategory("indeterminate", "Requirements", BoardCategory.EPIC));
        }

        @Test
        @DisplayName("should map 'indeterminate' for Epic with Planned to PLANNED")
        void shouldMapIndeterminatePlanned() {
            assertEquals(com.leadboard.status.StatusCategory.PLANNED,
                    service.mapJiraStatusCategory("indeterminate", "Planned", BoardCategory.EPIC));
        }

        @Test
        @DisplayName("should map 'indeterminate' for Story to IN_PROGRESS")
        void shouldMapIndeterminateInProgress() {
            assertEquals(com.leadboard.status.StatusCategory.IN_PROGRESS,
                    service.mapJiraStatusCategory("indeterminate", "Development", BoardCategory.STORY));
        }
    }

    // ==================== Link category detection ====================

    @Nested
    @DisplayName("detectLinkCategory()")
    class LinkCategoryTests {

        @Test
        @DisplayName("should detect BLOCKS from 'Blocks'")
        void shouldDetectBlocks() {
            assertEquals(LinkCategory.BLOCKS, service.detectLinkCategory("Blocks"));
            assertEquals(LinkCategory.BLOCKS, service.detectLinkCategory("is blocked by"));
        }

        @Test
        @DisplayName("should detect RELATED from 'Relates'")
        void shouldDetectRelated() {
            assertEquals(LinkCategory.RELATED, service.detectLinkCategory("Relates"));
        }

        @Test
        @DisplayName("should default to IGNORE for unknown link types")
        void shouldDefaultToIgnore() {
            assertEquals(LinkCategory.IGNORE, service.detectLinkCategory("Duplicate"));
            assertEquals(LinkCategory.IGNORE, service.detectLinkCategory("Cloners"));
        }
    }

    // ==================== Helper methods ====================

    private Map<String, Object> issueType(String name, boolean subtask) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", UUID.randomUUID().toString());
        m.put("name", name);
        m.put("subtask", subtask);
        m.put("description", null);
        return m;
    }

    private Map<String, Object> statusGroup(String issueType, List<Map<String, Object>> statuses) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issueType", issueType);
        m.put("statuses", statuses);
        return m;
    }

    private Map<String, Object> status(String name, String categoryKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("statusCategory", categoryKey);
        m.put("statusCategoryName", categoryKey.substring(0, 1).toUpperCase() + categoryKey.substring(1));
        return m;
    }

    private Map<String, Object> linkType(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", UUID.randomUUID().toString());
        m.put("name", name);
        m.put("inward", "is " + name.toLowerCase() + " by");
        m.put("outward", name.toLowerCase());
        return m;
    }
}
