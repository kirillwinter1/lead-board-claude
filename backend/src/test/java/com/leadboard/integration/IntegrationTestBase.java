package com.leadboard.integration;

import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamEntity;
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
    protected StatusChangelogRepository changelogRepository;

    @BeforeEach
    void cleanUp() {
        changelogRepository.deleteAll();
        issueRepository.deleteAll();
        teamRepository.deleteAll();
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
