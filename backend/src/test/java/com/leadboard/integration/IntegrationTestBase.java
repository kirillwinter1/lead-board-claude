package com.leadboard.integration;

import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.poker.repository.PokerSessionRepository;
import com.leadboard.poker.repository.PokerStoryRepository;
import com.leadboard.poker.repository.PokerVoteRepository;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamMemberRepository;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Base class for Integration tests.
 * Uses Testcontainers PostgreSQL with Flyway migrations enabled.
 * Tests PostgreSQL-specific features: JSONB, arrays, FK constraints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Import(IntegrationTestSecurityConfig.class)
public abstract class IntegrationTestBase {

    // Singleton container - started once, shared by all tests
    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("leadboard_integration")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("jira.project-key", () -> "TEST");
        registry.add("jira.base-url", () -> "https://test.atlassian.net");
    }

    @Autowired
    protected JiraIssueRepository issueRepository;

    @Autowired
    protected TeamRepository teamRepository;

    @Autowired
    protected TeamMemberRepository teamMemberRepository;

    @Autowired
    protected StatusChangelogRepository changelogRepository;

    @Autowired
    protected PokerVoteRepository pokerVoteRepository;

    @Autowired
    protected PokerStoryRepository pokerStoryRepository;

    @Autowired
    protected PokerSessionRepository pokerSessionRepository;

    @Autowired
    protected ProjectConfigurationRepository projectConfigRepo;

    @Autowired
    protected WorkflowRoleRepository workflowRoleRepo;

    @Autowired
    protected IssueTypeMappingRepository issueTypeMappingRepo;

    @Autowired
    protected StatusMappingRepository statusMappingRepo;

    @Autowired
    protected LinkTypeMappingRepository linkTypeMappingRepo;

    @Autowired
    protected WorkflowConfigService workflowConfigService;

    @BeforeEach
    void cleanUp() {
        // Delete in correct order to respect FK constraints
        pokerVoteRepository.deleteAll();
        pokerStoryRepository.deleteAll();
        pokerSessionRepository.deleteAll();
        changelogRepository.deleteAll();
        issueRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();

        // Seed workflow config (V26 no longer has seed data)
        seedWorkflowConfig();
    }

    /**
     * Seeds minimal workflow configuration for integration tests.
     * Replaces the seed data that was previously in V26.
     */
    protected void seedWorkflowConfig() {
        // Check if already seeded (Flyway creates the singleton row)
        var existingConfig = projectConfigRepo.findByIsDefaultTrue();
        if (existingConfig.isEmpty()) return;

        Long configId = existingConfig.get().getId();

        // Only seed if empty (avoid duplicates across tests)
        if (!workflowRoleRepo.findByConfigIdOrderBySortOrderAsc(configId).isEmpty()) return;

        // Roles
        saveRole(configId, "SA", "System Analyst", "#3b82f6", 1, false);
        saveRole(configId, "DEV", "Developer", "#10b981", 2, true);
        saveRole(configId, "QA", "QA Engineer", "#f59e0b", 3, false);

        // Issue type mappings
        saveIssueType(configId, "Эпик", BoardCategory.EPIC, null);
        saveIssueType(configId, "Epic", BoardCategory.EPIC, null);
        saveIssueType(configId, "История", BoardCategory.STORY, null);
        saveIssueType(configId, "Story", BoardCategory.STORY, null);
        saveIssueType(configId, "Bug", BoardCategory.STORY, null);
        saveIssueType(configId, "Аналитика", BoardCategory.SUBTASK, "SA");
        saveIssueType(configId, "Разработка", BoardCategory.SUBTASK, "DEV");
        saveIssueType(configId, "Тестирование", BoardCategory.SUBTASK, "QA");
        saveIssueType(configId, "Sub-task", BoardCategory.SUBTASK, "DEV");

        // Status mappings — EPIC
        saveStatus(configId, "Новое", BoardCategory.EPIC, StatusCategory.NEW, null, 0, -5);
        saveStatus(configId, "В работе", BoardCategory.EPIC, StatusCategory.IN_PROGRESS, null, 30, 25);
        saveStatus(configId, "Done", BoardCategory.EPIC, StatusCategory.DONE, null, 50, 0);
        saveStatus(configId, "Готово", BoardCategory.EPIC, StatusCategory.DONE, null, 50, 0);

        // Status mappings — STORY
        saveStatus(configId, "Новое", BoardCategory.STORY, StatusCategory.NEW, null, 0, 0);
        saveStatus(configId, "В работе", BoardCategory.STORY, StatusCategory.IN_PROGRESS, null, 20, 0);
        saveStatus(configId, "Done", BoardCategory.STORY, StatusCategory.DONE, null, 50, 0);

        // Status mappings — SUBTASK
        saveStatus(configId, "Новое", BoardCategory.SUBTASK, StatusCategory.NEW, null, 0, 0);
        saveStatus(configId, "В работе", BoardCategory.SUBTASK, StatusCategory.IN_PROGRESS, null, 10, 0);
        saveStatus(configId, "Done", BoardCategory.SUBTASK, StatusCategory.DONE, null, 50, 0);

        // Link types
        saveLinkType(configId, "Blocks", LinkCategory.BLOCKS);

        // Refresh caches
        workflowConfigService.clearCache();
    }

    private void saveRole(Long configId, String code, String displayName, String color, int order, boolean isDefault) {
        WorkflowRoleEntity role = new WorkflowRoleEntity();
        role.setConfigId(configId);
        role.setCode(code);
        role.setDisplayName(displayName);
        role.setColor(color);
        role.setSortOrder(order);
        role.setDefault(isDefault);
        workflowRoleRepo.save(role);
    }

    private void saveIssueType(Long configId, String name, BoardCategory cat, String roleCode) {
        IssueTypeMappingEntity e = new IssueTypeMappingEntity();
        e.setConfigId(configId);
        e.setJiraTypeName(name);
        e.setBoardCategory(cat);
        e.setWorkflowRoleCode(roleCode);
        issueTypeMappingRepo.save(e);
    }

    private void saveStatus(Long configId, String statusName, BoardCategory issueCat, StatusCategory statusCat,
                             String roleCode, int sortOrder, int scoreWeight) {
        StatusMappingEntity e = new StatusMappingEntity();
        e.setConfigId(configId);
        e.setJiraStatusName(statusName);
        e.setIssueCategory(issueCat);
        e.setStatusCategory(statusCat);
        e.setWorkflowRoleCode(roleCode);
        e.setSortOrder(sortOrder);
        e.setScoreWeight(scoreWeight);
        statusMappingRepo.save(e);
    }

    private void saveLinkType(Long configId, String name, LinkCategory cat) {
        LinkTypeMappingEntity e = new LinkTypeMappingEntity();
        e.setConfigId(configId);
        e.setJiraLinkTypeName(name);
        e.setLinkCategory(cat);
        linkTypeMappingRepo.save(e);
    }

    // Helper methods

    protected TeamEntity createTeam(String name) {
        TeamEntity team = new TeamEntity();
        team.setName(name);
        team.setJiraTeamValue(name);
        team.setActive(true);
        return teamRepository.save(team);
    }

    protected JiraIssueEntity createEpic(String key, String summary, String status, Long teamId) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setIssueId("id-" + key);
        epic.setSummary(summary);
        epic.setIssueType("Эпик");
        epic.setStatus(status);
        epic.setTeamId(teamId);
        epic.setProjectKey("TEST");
        epic.setSubtask(false);
        epic.setBoardCategory("EPIC");
        return issueRepository.save(epic);
    }

    protected JiraIssueEntity createEpicWithAutoScore(String key, String summary, String status, Long teamId, BigDecimal autoScore) {
        JiraIssueEntity epic = createEpic(key, summary, status, teamId);
        epic.setAutoScore(autoScore);
        return issueRepository.save(epic);
    }

    protected JiraIssueEntity createStory(String key, String summary, String status, String parentKey, Long teamId) {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey(key);
        story.setIssueId("id-" + key);
        story.setSummary(summary);
        story.setIssueType("История");
        story.setStatus(status);
        story.setParentKey(parentKey);
        story.setTeamId(teamId);
        story.setProjectKey("TEST");
        story.setSubtask(false);
        story.setBoardCategory("STORY");
        return issueRepository.save(story);
    }

    protected JiraIssueEntity createSubtask(String key, String summary, String status,
                                             String parentKey, String subtaskType, Long teamId,
                                             Long estimateSeconds, Long loggedSeconds) {
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setIssueKey(key);
        subtask.setIssueId("id-" + key);
        subtask.setSummary(summary);
        subtask.setIssueType(subtaskType);
        subtask.setStatus(status);
        subtask.setParentKey(parentKey);
        subtask.setTeamId(teamId);
        subtask.setProjectKey("TEST");
        subtask.setSubtask(true);
        subtask.setBoardCategory("SUBTASK");
        subtask.setOriginalEstimateSeconds(estimateSeconds);
        subtask.setTimeSpentSeconds(loggedSeconds);
        return issueRepository.save(subtask);
    }

    protected StatusChangelogEntity createStatusChange(String issueKey, String issueId,
                                                        String fromStatus, String toStatus,
                                                        OffsetDateTime transitionedAt) {
        StatusChangelogEntity log = new StatusChangelogEntity();
        log.setIssueKey(issueKey);
        log.setIssueId(issueId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setTransitionedAt(transitionedAt);
        return changelogRepository.save(log);
    }
}
