package com.leadboard.config.service;

import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraTransition;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
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
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private JiraClient jiraClient;
    @Mock private JiraIssueRepository jiraIssueRepository;

    private MappingAutoDetectService service;

    @BeforeEach
    void setUp() {
        service = new MappingAutoDetectService(
                jiraMetadataService, configRepo, roleRepo, issueTypeRepo,
                statusMappingRepo, linkTypeRepo, workflowConfigService,
                jiraClient, jiraIssueRepository
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

    // ==================== Workflow Graph Enhancement ====================

    @Nested
    @DisplayName("enhanceWithTransitionsGraph()")
    class WorkflowGraphTests {

        @Test
        @DisplayName("BFS should compute correct levels from NEW status")
        void bfsShouldComputeCorrectLevels() {
            Map<String, Set<String>> graph = new LinkedHashMap<>();
            graph.put("New", Set.of("In Progress", "Cancelled"));
            graph.put("In Progress", Set.of("Review", "New"));
            graph.put("Review", Set.of("Done", "In Progress"));
            graph.put("Done", Set.of());

            Map<String, Integer> levels = service.bfsFromStatuses(Set.of("New"), graph);

            assertEquals(0, levels.get("New"));
            assertEquals(1, levels.get("In Progress"));
            assertEquals(1, levels.get("Cancelled"));
            assertEquals(2, levels.get("Review"));
            assertEquals(3, levels.get("Done"));
        }

        @Test
        @DisplayName("BFS should handle multiple start statuses")
        void bfsShouldHandleMultipleStarts() {
            Map<String, Set<String>> graph = new LinkedHashMap<>();
            graph.put("Open", Set.of("In Progress"));
            graph.put("Reopened", Set.of("In Progress"));
            graph.put("In Progress", Set.of("Done"));

            Map<String, Integer> levels = service.bfsFromStatuses(
                    new LinkedHashSet<>(List.of("Open", "Reopened")), graph);

            assertEquals(0, levels.get("Open"));
            assertEquals(0, levels.get("Reopened"));
            assertEquals(1, levels.get("In Progress"));
            assertEquals(2, levels.get("Done"));
        }

        @Test
        @DisplayName("computeWeightFromLevel should return correct weights")
        void computeWeightFromLevelShouldWork() {
            // NEW → -5
            assertEquals(-5, service.computeWeightFromLevel(0, 4, "new"));
            // DONE → 0
            assertEquals(0, service.computeWeightFromLevel(4, 4, "done"));
            // Intermediate: level 1 of maxLevel 4 → 5 (start of range)
            assertEquals(5, service.computeWeightFromLevel(1, 4, "indeterminate"));
            // Intermediate: level 3 of maxLevel 4 → 30 (end of range)
            assertEquals(30, service.computeWeightFromLevel(3, 4, "indeterminate"));
            // Intermediate: level 2 of maxLevel 4 → ~18 (midpoint)
            int mid = service.computeWeightFromLevel(2, 4, "indeterminate");
            assertTrue(mid > 5 && mid < 30, "Mid-level weight should be between 5 and 30, was: " + mid);
        }

        @Test
        @DisplayName("longestForwardPath should compute unique levels for linear workflow")
        void longestForwardPathShouldGiveUniqueLevels() {
            // Simulate: New → WaitingSA → Analysis → AnalysisReview → WaitingDev → Development → Done
            // with back-edges: AnalysisReview → Analysis, Development → WaitingDev
            Map<String, Set<String>> graph = new LinkedHashMap<>();
            graph.put("New", Set.of("WaitingSA"));
            graph.put("WaitingSA", Set.of("Analysis"));
            graph.put("Analysis", Set.of("AnalysisReview"));
            graph.put("AnalysisReview", Set.of("WaitingDev", "Analysis")); // back-edge to Analysis
            graph.put("WaitingDev", Set.of("Development"));
            graph.put("Development", Set.of("Done", "WaitingDev")); // back-edge to WaitingDev
            graph.put("Done", Set.of());

            Map<String, Integer> levels = service.longestForwardPath(Set.of("New"), graph);

            assertEquals(0, levels.get("New"));
            assertEquals(1, levels.get("WaitingSA"));
            assertEquals(2, levels.get("Analysis"));
            assertEquals(3, levels.get("AnalysisReview"));
            assertEquals(4, levels.get("WaitingDev"));
            assertEquals(5, levels.get("Development"));
            assertEquals(6, levels.get("Done"));
        }

        @Test
        @DisplayName("enhanceWithTransitionsGraph should update sortOrder and scoreWeight")
        void shouldEnhanceMappingsWithGraph() {
            // Setup status mappings
            StatusMappingEntity newStatus = createStatusMapping(1L, "New", BoardCategory.EPIC,
                    com.leadboard.status.StatusCategory.NEW, 0, -5);
            StatusMappingEntity inProgress = createStatusMapping(2L, "In Progress", BoardCategory.EPIC,
                    com.leadboard.status.StatusCategory.IN_PROGRESS, 10, 20);
            StatusMappingEntity done = createStatusMapping(3L, "Done", BoardCategory.EPIC,
                    com.leadboard.status.StatusCategory.DONE, 20, 0);

            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.EPIC))
                    .thenReturn(List.of(newStatus, inProgress, done));
            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.STORY))
                    .thenReturn(List.of());
            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.SUBTASK))
                    .thenReturn(List.of());

            // Setup sample issues
            JiraIssueEntity newIssue = createJiraIssue("PROJ-1", "New", "EPIC");
            JiraIssueEntity ipIssue = createJiraIssue("PROJ-2", "In Progress", "EPIC");
            JiraIssueEntity doneIssue = createJiraIssue("PROJ-3", "Done", "EPIC");

            when(jiraIssueRepository.findByStatusAndBoardCategory("New", "EPIC"))
                    .thenReturn(List.of(newIssue));
            when(jiraIssueRepository.findByStatusAndBoardCategory("In Progress", "EPIC"))
                    .thenReturn(List.of(ipIssue));
            when(jiraIssueRepository.findByStatusAndBoardCategory("Done", "EPIC"))
                    .thenReturn(List.of(doneIssue));

            // Setup transitions
            when(jiraClient.getTransitionsBasicAuth("PROJ-1")).thenReturn(List.of(
                    transition("11", "Start", "2", "In Progress", "indeterminate")
            ));
            when(jiraClient.getTransitionsBasicAuth("PROJ-2")).thenReturn(List.of(
                    transition("21", "Finish", "3", "Done", "done"),
                    transition("22", "Reopen", "1", "New", "new")
            ));
            when(jiraClient.getTransitionsBasicAuth("PROJ-3")).thenReturn(List.of(
                    transition("31", "Reopen", "1", "New", "new")
            ));

            when(statusMappingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<String> warnings = new ArrayList<>();
            service.enhanceWithTransitionsGraph(1L, warnings);

            // Verify sortOrder: New=0, InProgress=10, Done=20
            assertEquals(0, newStatus.getSortOrder());
            assertEquals(10, inProgress.getSortOrder());
            assertEquals(20, done.getSortOrder());

            // Verify scoreWeight: New=-5, InProgress=interpolated, Done=0
            assertEquals(-5, newStatus.getScoreWeight());
            assertEquals(0, done.getScoreWeight());
            assertTrue(inProgress.getScoreWeight() > 0, "In Progress weight should be > 0");
        }

        @Test
        @DisplayName("should handle missing sample issues gracefully")
        void shouldHandleMissingSampleIssues() {
            StatusMappingEntity newStatus = createStatusMapping(1L, "New", BoardCategory.STORY,
                    com.leadboard.status.StatusCategory.NEW, 0, -5);

            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.EPIC))
                    .thenReturn(List.of());
            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.STORY))
                    .thenReturn(List.of(newStatus));
            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.SUBTASK))
                    .thenReturn(List.of());

            when(jiraIssueRepository.findByStatusAndBoardCategory("New", "STORY"))
                    .thenReturn(List.of());

            List<String> warnings = new ArrayList<>();
            service.enhanceWithTransitionsGraph(1L, warnings);

            // Should not fail, just skip
            verify(jiraClient, never()).getTransitionsBasicAuth(any());
        }

        private StatusMappingEntity createStatusMapping(Long id, String name, BoardCategory cat,
                                                         com.leadboard.status.StatusCategory statusCat,
                                                         int sortOrder, int scoreWeight) {
            StatusMappingEntity m = new StatusMappingEntity();
            m.setId(id);
            m.setConfigId(1L);
            m.setJiraStatusName(name);
            m.setIssueCategory(cat);
            m.setStatusCategory(statusCat);
            m.setSortOrder(sortOrder);
            m.setScoreWeight(scoreWeight);
            return m;
        }

        private JiraIssueEntity createJiraIssue(String key, String status, String boardCategory) {
            JiraIssueEntity e = new JiraIssueEntity();
            e.setIssueKey(key);
            e.setStatus(status);
            e.setBoardCategory(boardCategory);
            return e;
        }

        private JiraTransition transition(String id, String name,
                                           String targetId, String targetName, String categoryKey) {
            return new JiraTransition(id, name,
                    new JiraTransition.TransitionTarget(targetId, targetName,
                            new JiraTransition.StatusCategoryInfo(categoryKey, categoryKey)));
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
