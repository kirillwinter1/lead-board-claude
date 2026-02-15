package com.leadboard.sync;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.service.MappingAutoDetectService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraIssue;
import com.leadboard.jira.JiraSearchResponse;
import com.leadboard.metrics.service.FlagChangelogService;
import com.leadboard.metrics.service.StatusChangelogService;
import com.leadboard.planning.AutoScoreService;
import com.leadboard.planning.IssueOrderService;
import com.leadboard.planning.StoryAutoScoreService;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SyncServiceTest {

    @Mock
    private JiraClient jiraClient;

    @Mock
    private JiraProperties jiraProperties;

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private JiraSyncStateRepository syncStateRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private AutoScoreService autoScoreService;

    @Mock
    private StoryAutoScoreService storyAutoScoreService;

    @Mock
    private StatusChangelogService statusChangelogService;

    @Mock
    private FlagChangelogService flagChangelogService;

    @Mock
    private IssueOrderService issueOrderService;

    @Mock
    private WorkflowConfigService workflowConfigService;

    @Mock
    private MappingAutoDetectService autoDetectService;

    private SyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new SyncService(
                jiraClient,
                jiraProperties,
                issueRepository,
                syncStateRepository,
                teamRepository,
                autoScoreService,
                storyAutoScoreService,
                statusChangelogService,
                flagChangelogService,
                issueOrderService,
                workflowConfigService,
                autoDetectService
        );

        // Common setup
        when(jiraProperties.getTeamFieldId()).thenReturn(null);
    }

    // ==================== Regression Tests ====================

    @Nested
    @DisplayName("Regression: FK constraint bug (2026-02-01)")
    class RegressionFkConstraintTests {

        @Test
        @DisplayName("should save issue BEFORE recording changelog for new issues")
        void shouldSaveIssueBeforeChangelogForNewIssue() {
            // Given: new issue from Jira (not in DB)
            String projectKey = "LB";
            JiraIssue jiraIssue = createJiraIssue("LB-123", "New Epic", "Новое", "Epic");
            JiraSearchResponse response = createSearchResponse(List.of(jiraIssue), true);

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(createSyncState(projectKey)));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);
            when(issueRepository.findByIssueKey("LB-123")).thenReturn(Optional.empty()); // NEW issue
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            syncService.syncProject(projectKey);

            // Then: verify order - save MUST happen before changelog
            InOrder inOrder = inOrder(issueRepository, statusChangelogService);
            inOrder.verify(issueRepository).save(any(JiraIssueEntity.class));
            // For new issues, changelog is only called if there's a status change from null
        }

        @Test
        @DisplayName("should save issue BEFORE recording changelog for existing issues with status change")
        void shouldSaveIssueBeforeChangelogForExistingIssue() {
            // Given: existing issue with status change
            String projectKey = "LB";
            JiraIssue jiraIssue = createJiraIssue("LB-100", "Existing Epic", "В работе", "Epic");
            JiraSearchResponse response = createSearchResponse(List.of(jiraIssue), true);

            JiraIssueEntity existingEntity = createExistingEntity("LB-100", "Новое"); // old status

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(createSyncState(projectKey)));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);
            when(issueRepository.findByIssueKey("LB-100")).thenReturn(Optional.of(existingEntity));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            syncService.syncProject(projectKey);

            // Then: verify order - save MUST happen before changelog
            InOrder inOrder = inOrder(issueRepository, statusChangelogService);
            inOrder.verify(issueRepository).save(any(JiraIssueEntity.class));
            inOrder.verify(statusChangelogService).detectAndRecordStatusChange(any(), any());
        }
    }

    // ==================== Sync New Issue Tests ====================

    @Nested
    @DisplayName("syncProject() - new issues")
    class SyncNewIssueTests {

        @Test
        @DisplayName("should create new entity when issue not in DB")
        void shouldCreateNewEntityWhenIssueNotInDb() {
            // Given
            String projectKey = "LB";
            JiraIssue jiraIssue = createJiraIssue("LB-999", "Brand New Epic", "Новое", "Epic");
            JiraSearchResponse response = createSearchResponse(List.of(jiraIssue), true);

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(createSyncState(projectKey)));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);
            when(issueRepository.findByIssueKey("LB-999")).thenReturn(Optional.empty());
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            syncService.syncProject(projectKey);

            // Then
            ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
            verify(issueRepository).save(captor.capture());

            JiraIssueEntity saved = captor.getValue();
            assertEquals("LB-999", saved.getIssueKey());
            assertEquals("Brand New Epic", saved.getSummary());
            assertEquals("Новое", saved.getStatus());
            assertEquals("Epic", saved.getIssueType());
        }

        @Test
        @DisplayName("should map team field to team_id")
        void shouldMapTeamFieldToTeamId() {
            // Given
            String projectKey = "LB";
            String teamFieldId = "customfield_12345";
            JiraIssue jiraIssue = createJiraIssueWithTeam("LB-100", "Epic with Team", "Новое", "Epic", teamFieldId, "Команда А");
            JiraSearchResponse response = createSearchResponse(List.of(jiraIssue), true);

            TeamEntity team = new TeamEntity();
            team.setId(5L);
            team.setName("Команда А");

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(jiraProperties.getTeamFieldId()).thenReturn(teamFieldId);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(createSyncState(projectKey)));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);
            when(issueRepository.findByIssueKey("LB-100")).thenReturn(Optional.empty());
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(teamRepository.findByJiraTeamValue("Команда А")).thenReturn(Optional.of(team));

            // When
            syncService.syncProject(projectKey);

            // Then
            ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
            verify(issueRepository).save(captor.capture());

            assertEquals(5L, captor.getValue().getTeamId());
            assertEquals("Команда А", captor.getValue().getTeamFieldValue());
        }
    }

    // ==================== Sync Existing Issue Tests ====================

    @Nested
    @DisplayName("syncProject() - existing issues")
    class SyncExistingIssueTests {

        @Test
        @DisplayName("should preserve rough estimates on sync")
        void shouldPreserveRoughEstimatesOnSync() {
            // Given
            String projectKey = "LB";
            JiraIssue jiraIssue = createJiraIssue("LB-50", "Updated Summary", "В работе", "Epic");
            JiraSearchResponse response = createSearchResponse(List.of(jiraIssue), true);

            JiraIssueEntity existingEntity = createExistingEntity("LB-50", "Новое");
            existingEntity.setRoughEstimate("SA", BigDecimal.valueOf(5));
            existingEntity.setRoughEstimate("DEV", BigDecimal.valueOf(10));
            existingEntity.setRoughEstimate("QA", BigDecimal.valueOf(3));

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(createSyncState(projectKey)));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);
            when(issueRepository.findByIssueKey("LB-50")).thenReturn(Optional.of(existingEntity));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            syncService.syncProject(projectKey);

            // Then
            ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
            verify(issueRepository).save(captor.capture());

            JiraIssueEntity saved = captor.getValue();
            assertEquals(BigDecimal.valueOf(5), saved.getRoughEstimate("SA"));
            assertEquals(BigDecimal.valueOf(10), saved.getRoughEstimate("DEV"));
            assertEquals(BigDecimal.valueOf(3), saved.getRoughEstimate("QA"));
        }

        @Test
        @DisplayName("should preserve autoScore on sync")
        void shouldPreserveAutoScoreOnSync() {
            // Given
            String projectKey = "LB";
            JiraIssue jiraIssue = createJiraIssue("LB-60", "Summary", "Новое", "Epic");
            JiraSearchResponse response = createSearchResponse(List.of(jiraIssue), true);

            JiraIssueEntity existingEntity = createExistingEntity("LB-60", "Новое");
            existingEntity.setAutoScore(BigDecimal.valueOf(85.5));
            existingEntity.setAutoScoreCalculatedAt(OffsetDateTime.now().minusHours(1));

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(createSyncState(projectKey)));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);
            when(issueRepository.findByIssueKey("LB-60")).thenReturn(Optional.of(existingEntity));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            syncService.syncProject(projectKey);

            // Then
            ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
            verify(issueRepository).save(captor.capture());

            assertEquals(BigDecimal.valueOf(85.5), captor.getValue().getAutoScore());
        }

        @Test
        @DisplayName("should update fields from Jira")
        void shouldUpdateFieldsFromJira() {
            // Given
            String projectKey = "LB";
            JiraIssue jiraIssue = createJiraIssue("LB-70", "New Summary from Jira", "В разработке", "Epic");
            JiraSearchResponse response = createSearchResponse(List.of(jiraIssue), true);

            JiraIssueEntity existingEntity = createExistingEntity("LB-70", "Новое");
            existingEntity.setSummary("Old Summary");

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(createSyncState(projectKey)));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);
            when(issueRepository.findByIssueKey("LB-70")).thenReturn(Optional.of(existingEntity));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            syncService.syncProject(projectKey);

            // Then
            ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
            verify(issueRepository).save(captor.capture());

            JiraIssueEntity saved = captor.getValue();
            assertEquals("New Summary from Jira", saved.getSummary());
            assertEquals("В разработке", saved.getStatus());
        }
    }

    // ==================== Incremental Sync Tests ====================

    @Nested
    @DisplayName("Incremental sync")
    class IncrementalSyncTests {

        @Test
        @DisplayName("should use lastSyncCompletedAt for incremental sync")
        void shouldUseLastSyncTimeForIncrementalSync() {
            // Given
            String projectKey = "LB";
            JiraSyncStateEntity syncState = createSyncState(projectKey);
            syncState.setLastSyncCompletedAt(OffsetDateTime.now().minusHours(2));

            JiraSearchResponse response = createSearchResponse(List.of(), true);

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(syncState));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);

            // When
            syncService.syncProject(projectKey);

            // Then: JQL should contain "updated >="
            ArgumentCaptor<String> jqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jiraClient).search(jqlCaptor.capture(), anyInt(), any());

            String jql = jqlCaptor.getValue();
            assertTrue(jql.contains("updated >="), "JQL should contain 'updated >=' for incremental sync");
        }

        @Test
        @DisplayName("should do full sync when no previous sync")
        void shouldDoFullSyncWhenNoPreviousSync() {
            // Given
            String projectKey = "LB";
            JiraSyncStateEntity syncState = createSyncState(projectKey);
            syncState.setLastSyncCompletedAt(null); // No previous sync

            JiraSearchResponse response = createSearchResponse(List.of(), true);

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(syncState));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);

            // When
            syncService.syncProject(projectKey);

            // Then: JQL should NOT contain "updated >="
            ArgumentCaptor<String> jqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jiraClient).search(jqlCaptor.capture(), anyInt(), any());

            String jql = jqlCaptor.getValue();
            assertFalse(jql.contains("updated >="), "JQL should NOT contain 'updated >=' for full sync");
            assertTrue(jql.contains("project = LB"), "JQL should contain project filter");
        }
    }

    // ==================== Sync State Tests ====================

    @Nested
    @DisplayName("Sync state management")
    class SyncStateTests {

        @Test
        @DisplayName("should mark sync as completed on success")
        void shouldMarkSyncAsCompletedOnSuccess() {
            // Given
            String projectKey = "LB";
            JiraSyncStateEntity syncState = createSyncState(projectKey);
            JiraSearchResponse response = createSearchResponse(List.of(), true);

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(syncState));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);
            when(syncStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            syncService.syncProject(projectKey);

            // Then
            ArgumentCaptor<JiraSyncStateEntity> captor = ArgumentCaptor.forClass(JiraSyncStateEntity.class);
            verify(syncStateRepository, atLeast(2)).save(captor.capture());

            List<JiraSyncStateEntity> savedStates = captor.getAllValues();
            JiraSyncStateEntity finalState = savedStates.get(savedStates.size() - 1);

            assertFalse(finalState.isSyncInProgress());
            assertNotNull(finalState.getLastSyncCompletedAt());
            assertNull(finalState.getLastError());
        }

        @Test
        @DisplayName("should record error on sync failure")
        void shouldRecordErrorOnSyncFailure() {
            // Given
            String projectKey = "LB";
            JiraSyncStateEntity syncState = createSyncState(projectKey);

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(syncState));
            when(syncStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jiraClient.search(anyString(), anyInt(), any())).thenThrow(new RuntimeException("Jira API error"));

            // When
            syncService.syncProject(projectKey);

            // Then
            ArgumentCaptor<JiraSyncStateEntity> captor = ArgumentCaptor.forClass(JiraSyncStateEntity.class);
            verify(syncStateRepository, atLeast(2)).save(captor.capture());

            List<JiraSyncStateEntity> savedStates = captor.getAllValues();
            JiraSyncStateEntity finalState = savedStates.get(savedStates.size() - 1);

            assertFalse(finalState.isSyncInProgress());
            assertNotNull(finalState.getLastError());
            assertTrue(finalState.getLastError().contains("Jira API error"));
        }

        @Test
        @DisplayName("should not start sync if already in progress")
        void shouldNotStartSyncIfAlreadyInProgress() {
            // Given
            String projectKey = "LB";
            JiraSyncStateEntity syncState = createSyncState(projectKey);
            syncState.setSyncInProgress(true);

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(syncState));

            // When
            syncService.syncProject(projectKey);

            // Then: JiraClient should not be called
            verify(jiraClient, never()).search(anyString(), anyInt(), any());
        }
    }

    // ==================== AutoScore Recalculation Tests ====================

    @Nested
    @DisplayName("AutoScore recalculation after sync")
    class AutoScoreRecalculationTests {

        @Test
        @DisplayName("should recalculate autoScore after successful sync")
        void shouldRecalculateAutoScoreAfterSync() {
            // Given
            String projectKey = "LB";
            JiraSyncStateEntity syncState = createSyncState(projectKey);
            JiraSearchResponse response = createSearchResponse(List.of(), true);

            when(jiraProperties.getProjectKey()).thenReturn(projectKey);
            when(syncStateRepository.findByProjectKey(projectKey)).thenReturn(Optional.of(syncState));
            when(jiraClient.search(anyString(), anyInt(), any())).thenReturn(response);
            when(syncStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            syncService.syncProject(projectKey);

            // Then
            verify(autoScoreService).recalculateAll();
            verify(storyAutoScoreService).recalculateAll();
        }
    }

    // ==================== Helper Methods ====================

    private JiraIssue createJiraIssue(String key, String summary, String status, String issueType) {
        JiraIssue issue = new JiraIssue();
        issue.setId("id-" + key);
        issue.setKey(key);

        JiraIssue.JiraFields fields = new JiraIssue.JiraFields();
        fields.setSummary(summary);

        JiraIssue.JiraStatus jiraStatus = new JiraIssue.JiraStatus();
        jiraStatus.setName(status);
        fields.setStatus(jiraStatus);

        JiraIssue.JiraIssueType jiraIssueType = new JiraIssue.JiraIssueType();
        jiraIssueType.setName(issueType);
        jiraIssueType.setSubtask(false);
        fields.setIssuetype(jiraIssueType);

        issue.setFields(fields);
        return issue;
    }

    private JiraIssue createJiraIssueWithTeam(String key, String summary, String status, String issueType,
                                               String teamFieldId, String teamValue) {
        JiraIssue issue = createJiraIssue(key, summary, status, issueType);
        issue.getFields().setCustomField(teamFieldId, teamValue);
        return issue;
    }

    private JiraSearchResponse createSearchResponse(List<JiraIssue> issues, boolean isLast) {
        JiraSearchResponse response = new JiraSearchResponse();
        response.setIssues(issues);
        response.setLast(isLast);
        response.setNextPageToken(null);
        return response;
    }

    private JiraSyncStateEntity createSyncState(String projectKey) {
        JiraSyncStateEntity state = new JiraSyncStateEntity();
        state.setProjectKey(projectKey);
        state.setSyncInProgress(false);
        state.setLastSyncIssuesCount(0);
        return state;
    }

    private JiraIssueEntity createExistingEntity(String key, String status) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setIssueId("id-" + key);
        entity.setStatus(status);
        entity.setUpdatedAt(OffsetDateTime.now().minusDays(1));
        return entity;
    }
}
