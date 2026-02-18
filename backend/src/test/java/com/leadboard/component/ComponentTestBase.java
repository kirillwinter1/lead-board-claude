package com.leadboard.component;

import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for Component tests.
 * Uses Testcontainers PostgreSQL with singleton pattern for full compatibility with production.
 * The container is shared across all test classes to improve performance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("component")
@Import(TestSecurityConfig.class)
public abstract class ComponentTestBase {

    // Singleton container - started once, shared by all tests
    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("leadboard_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Override .env values for tests
        registry.add("jira.project-key", () -> "TEST");
        registry.add("jira.base-url", () -> "https://test.atlassian.net");
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JiraIssueRepository issueRepository;

    @Autowired
    protected TeamRepository teamRepository;

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
        issueRepository.deleteAll();
        teamRepository.deleteAll();
        seedWorkflowConfig();
    }

    protected void seedWorkflowConfig() {
        var existingConfig = projectConfigRepo.findByIsDefaultTrue();
        Long configId;
        if (existingConfig.isEmpty()) {
            // Create default config for tests (Flyway is disabled, so no migration data)
            ProjectConfigurationEntity config = new ProjectConfigurationEntity();
            config.setName("Test Config");
            config.setProjectKey("TEST");
            config.setDefault(true);
            config = projectConfigRepo.save(config);
            configId = config.getId();
        } else {
            configId = existingConfig.get().getId();
        }
        if (!workflowRoleRepo.findByConfigIdOrderBySortOrderAsc(configId).isEmpty()) return;

        // Roles
        saveRole(configId, "SA", "System Analyst", "#3b82f6", 1, false);
        saveRole(configId, "DEV", "Developer", "#10b981", 2, true);
        saveRole(configId, "QA", "QA Engineer", "#f59e0b", 3, false);

        // Issue types
        saveIssueType(configId, "Эпик", BoardCategory.EPIC, null);
        saveIssueType(configId, "История", BoardCategory.STORY, null);
        saveIssueType(configId, "Bug", BoardCategory.STORY, null);
        saveIssueType(configId, "Аналитика", BoardCategory.SUBTASK, "SA");
        saveIssueType(configId, "Разработка", BoardCategory.SUBTASK, "DEV");
        saveIssueType(configId, "Тестирование", BoardCategory.SUBTASK, "QA");

        // Statuses
        saveStatus(configId, "Новое", BoardCategory.EPIC, StatusCategory.NEW, null, 0, -5);
        saveStatus(configId, "В работе", BoardCategory.EPIC, StatusCategory.IN_PROGRESS, null, 30, 25);
        saveStatus(configId, "Done", BoardCategory.EPIC, StatusCategory.DONE, null, 50, 0);
        saveStatus(configId, "Новое", BoardCategory.STORY, StatusCategory.NEW, null, 0, 0);
        saveStatus(configId, "В работе", BoardCategory.STORY, StatusCategory.IN_PROGRESS, null, 20, 0);
        saveStatus(configId, "Done", BoardCategory.STORY, StatusCategory.DONE, null, 50, 0);
        saveStatus(configId, "Новое", BoardCategory.SUBTASK, StatusCategory.NEW, null, 0, 0);
        saveStatus(configId, "В работе", BoardCategory.SUBTASK, StatusCategory.IN_PROGRESS, null, 10, 0);
        saveStatus(configId, "Done", BoardCategory.SUBTASK, StatusCategory.DONE, null, 50, 0);

        saveLinkType(configId, "Blocks", LinkCategory.BLOCKS);

        workflowConfigService.clearCache();
    }

    private void saveRole(Long configId, String code, String displayName, String color, int order, boolean isDefault) {
        WorkflowRoleEntity e = new WorkflowRoleEntity();
        e.setConfigId(configId); e.setCode(code); e.setDisplayName(displayName);
        e.setColor(color); e.setSortOrder(order); e.setDefault(isDefault);
        workflowRoleRepo.save(e);
    }

    private void saveIssueType(Long configId, String name, BoardCategory cat, String roleCode) {
        IssueTypeMappingEntity e = new IssueTypeMappingEntity();
        e.setConfigId(configId); e.setJiraTypeName(name); e.setBoardCategory(cat); e.setWorkflowRoleCode(roleCode);
        issueTypeMappingRepo.save(e);
    }

    private void saveStatus(Long configId, String statusName, BoardCategory issueCat, StatusCategory statusCat,
                             String roleCode, int sortOrder, int scoreWeight) {
        StatusMappingEntity e = new StatusMappingEntity();
        e.setConfigId(configId); e.setJiraStatusName(statusName); e.setIssueCategory(issueCat);
        e.setStatusCategory(statusCat); e.setWorkflowRoleCode(roleCode);
        e.setSortOrder(sortOrder); e.setScoreWeight(scoreWeight);
        statusMappingRepo.save(e);
    }

    private void saveLinkType(Long configId, String name, LinkCategory cat) {
        LinkTypeMappingEntity e = new LinkTypeMappingEntity();
        e.setConfigId(configId); e.setJiraLinkTypeName(name); e.setLinkCategory(cat);
        linkTypeMappingRepo.save(e);
    }

    protected TeamEntity createTeam(String name) {
        TeamEntity team = new TeamEntity();
        team.setName(name);
        team.setJiraTeamValue(name);
        team.setActive(true);
        return teamRepository.save(team);
    }

    protected JiraIssueEntity createEpic(String key, String summary, Long teamId) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setIssueId("id-" + key);
        epic.setSummary(summary);
        epic.setIssueType("Эпик");
        epic.setBoardCategory("EPIC");
        epic.setStatus("Новое");
        epic.setTeamId(teamId);
        epic.setProjectKey("TEST");
        epic.setSubtask(false);
        return issueRepository.save(epic);
    }

    protected JiraIssueEntity createEpic(String key, String summary, String status, Long teamId) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setIssueId("id-" + key);
        epic.setSummary(summary);
        epic.setIssueType("Эпик");
        epic.setBoardCategory("EPIC");
        epic.setStatus(status);
        epic.setTeamId(teamId);
        epic.setProjectKey("TEST");
        epic.setSubtask(false);
        return issueRepository.save(epic);
    }

    protected JiraIssueEntity createStory(String key, String summary, String parentKey, Long teamId) {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey(key);
        story.setIssueId("id-" + key);
        story.setSummary(summary);
        story.setIssueType("История");
        story.setBoardCategory("STORY");
        story.setStatus("Новое");
        story.setParentKey(parentKey);
        story.setTeamId(teamId);
        story.setProjectKey("TEST");
        story.setSubtask(false);
        return issueRepository.save(story);
    }

    protected JiraIssueEntity createSubtask(String key, String summary, String parentKey, String subtaskType, Long teamId) {
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setIssueKey(key);
        subtask.setIssueId("id-" + key);
        subtask.setSummary(summary);
        subtask.setIssueType(subtaskType);
        subtask.setBoardCategory("SUBTASK");
        subtask.setStatus("Новое");
        subtask.setParentKey(parentKey);
        subtask.setTeamId(teamId);
        subtask.setProjectKey("TEST");
        subtask.setSubtask(true);
        return issueRepository.save(subtask);
    }

    protected JiraIssueEntity createSubtaskWithTime(String key, String summary, String parentKey,
                                                     String subtaskType, Long teamId,
                                                     Long estimateSeconds, Long loggedSeconds) {
        JiraIssueEntity subtask = createSubtask(key, summary, parentKey, subtaskType, teamId);
        subtask.setOriginalEstimateSeconds(estimateSeconds);
        subtask.setTimeSpentSeconds(loggedSeconds);
        return issueRepository.save(subtask);
    }
}
