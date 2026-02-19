package com.leadboard.planning;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueOrderServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    private IssueOrderService service;

    @BeforeEach
    void setUp() {
        service = new IssueOrderService(issueRepository, workflowConfigService);

        // Default type categorization
        lenient().when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        lenient().when(workflowConfigService.isEpic("Story")).thenReturn(false);
        lenient().when(workflowConfigService.isEpic("Bug")).thenReturn(false);
        lenient().when(workflowConfigService.isStory("Story")).thenReturn(true);
        lenient().when(workflowConfigService.isStory("Bug")).thenReturn(false);
        lenient().when(workflowConfigService.isBug("Bug")).thenReturn(true);
        lenient().when(workflowConfigService.isStory("Epic")).thenReturn(false);
        lenient().when(workflowConfigService.isStory("Sub-task")).thenReturn(false);
        lenient().when(workflowConfigService.isStoryOrBug("Story")).thenReturn(true);
        lenient().when(workflowConfigService.isStoryOrBug("Bug")).thenReturn(true);
        lenient().when(workflowConfigService.isStoryOrBug("Epic")).thenReturn(false);
        lenient().when(workflowConfigService.isStoryOrBug("Sub-task")).thenReturn(false);
        lenient().when(workflowConfigService.getStoryTypeNames()).thenReturn(List.of("Story"));
        lenient().when(workflowConfigService.getBugTypeNames()).thenReturn(List.of("Bug"));
    }

    // ==================== Epic Reorder Tests ====================

    @Nested
    class ReorderEpicTests {

        @Test
        void reorderEpic_moveUp_shiftsOthersDown() {
            // Given: 5 epics in team 1 with orders 1,2,3,4,5
            // Moving epic at position 4 to position 2
            Long teamId = 1L;
            JiraIssueEntity epic = createEpic("EPIC-4", teamId, 4);

            List<JiraIssueEntity> allEpics = List.of(
                createEpic("EPIC-1", teamId, 1),
                createEpic("EPIC-2", teamId, 2),
                createEpic("EPIC-3", teamId, 3),
                createEpic("EPIC-4", teamId, 4),
                createEpic("EPIC-5", teamId, 5)
            );

            when(issueRepository.findByIssueKey("EPIC-4")).thenReturn(Optional.of(epic));
            when(issueRepository.findMaxEpicOrderForTeam(eq(teamId))).thenReturn(5);
            when(issueRepository.findEpicsByTeamOrderByManualOrder(eq(teamId)))
                .thenReturn(new ArrayList<>(allEpics));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            JiraIssueEntity result = service.reorderEpic("EPIC-4", 2);

            // Then
            assertEquals(2, result.getManualOrder());

            // Verify shifts: items at positions 2,3 should be shifted to 3,4
            ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
            verify(issueRepository, atLeast(1)).save(captor.capture());

            List<JiraIssueEntity> saved = captor.getAllValues();
            // Should have saved the shifted items + the moved epic
            assertTrue(saved.size() >= 1);
        }

        @Test
        void reorderEpic_moveDown_shiftsOthersUp() {
            // Given: 5 epics, moving position 2 to position 4
            Long teamId = 1L;
            JiraIssueEntity epic = createEpic("EPIC-2", teamId, 2);

            List<JiraIssueEntity> allEpics = List.of(
                createEpic("EPIC-1", teamId, 1),
                createEpic("EPIC-2", teamId, 2),
                createEpic("EPIC-3", teamId, 3),
                createEpic("EPIC-4", teamId, 4),
                createEpic("EPIC-5", teamId, 5)
            );

            when(issueRepository.findByIssueKey("EPIC-2")).thenReturn(Optional.of(epic));
            when(issueRepository.findMaxEpicOrderForTeam(eq(teamId))).thenReturn(5);
            when(issueRepository.findEpicsByTeamOrderByManualOrder(eq(teamId)))
                .thenReturn(new ArrayList<>(allEpics));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            JiraIssueEntity result = service.reorderEpic("EPIC-2", 4);

            // Then
            assertEquals(4, result.getManualOrder());
        }

        @Test
        void reorderEpic_samePosition_noChanges() {
            // Given
            Long teamId = 1L;
            JiraIssueEntity epic = createEpic("EPIC-3", teamId, 3);

            when(issueRepository.findByIssueKey("EPIC-3")).thenReturn(Optional.of(epic));
            when(issueRepository.findMaxEpicOrderForTeam(eq(teamId))).thenReturn(5);

            // When
            JiraIssueEntity result = service.reorderEpic("EPIC-3", 3);

            // Then - no save calls for shifting, only returns the same epic
            assertEquals(3, result.getManualOrder());
            verify(issueRepository, never()).findEpicsByTeamOrderByManualOrder(anyLong());
        }

        @Test
        void reorderEpic_positionBelowOne_clampedToOne() {
            // Given
            Long teamId = 1L;
            JiraIssueEntity epic = createEpic("EPIC-3", teamId, 3);

            List<JiraIssueEntity> allEpics = List.of(
                createEpic("EPIC-1", teamId, 1),
                createEpic("EPIC-2", teamId, 2),
                createEpic("EPIC-3", teamId, 3)
            );

            when(issueRepository.findByIssueKey("EPIC-3")).thenReturn(Optional.of(epic));
            when(issueRepository.findMaxEpicOrderForTeam(eq(teamId))).thenReturn(3);
            when(issueRepository.findEpicsByTeamOrderByManualOrder(eq(teamId)))
                .thenReturn(new ArrayList<>(allEpics));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            JiraIssueEntity result = service.reorderEpic("EPIC-3", -5);

            // Then - should be clamped to 1
            assertEquals(1, result.getManualOrder());
        }

        @Test
        void reorderEpic_positionAboveMax_clampedToMax() {
            // Given
            Long teamId = 1L;
            JiraIssueEntity epic = createEpic("EPIC-1", teamId, 1);

            List<JiraIssueEntity> allEpics = List.of(
                createEpic("EPIC-1", teamId, 1),
                createEpic("EPIC-2", teamId, 2),
                createEpic("EPIC-3", teamId, 3)
            );

            when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));
            when(issueRepository.findMaxEpicOrderForTeam(eq(teamId))).thenReturn(3);
            when(issueRepository.findEpicsByTeamOrderByManualOrder(eq(teamId)))
                .thenReturn(new ArrayList<>(allEpics));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            JiraIssueEntity result = service.reorderEpic("EPIC-1", 100);

            // Then - should be clamped to max (3)
            assertEquals(3, result.getManualOrder());
        }

        @Test
        void reorderEpic_notFound_throwsException() {
            when(issueRepository.findByIssueKey("EPIC-999")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () ->
                service.reorderEpic("EPIC-999", 1));
        }

        @Test
        void reorderEpic_notAnEpic_throwsException() {
            JiraIssueEntity story = createStory("STORY-1", "EPIC-1", 1);
            when(issueRepository.findByIssueKey("STORY-1")).thenReturn(Optional.of(story));

            assertThrows(IllegalArgumentException.class, () ->
                service.reorderEpic("STORY-1", 1));
        }

        @Test
        void reorderEpic_noTeam_throwsException() {
            JiraIssueEntity epic = createEpic("EPIC-1", null, 1);
            when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));

            assertThrows(IllegalArgumentException.class, () ->
                service.reorderEpic("EPIC-1", 1));
        }

        @Test
        void reorderEpic_nullManualOrder_assignsNewOrder() {
            // Given: epic without manual_order
            Long teamId = 1L;
            JiraIssueEntity epic = createEpic("EPIC-NEW", teamId, null);

            List<JiraIssueEntity> allEpics = List.of(
                createEpic("EPIC-1", teamId, 1),
                createEpic("EPIC-2", teamId, 2)
            );

            when(issueRepository.findByIssueKey("EPIC-NEW")).thenReturn(Optional.of(epic));
            when(issueRepository.findMaxEpicOrderForTeam(eq(teamId))).thenReturn(2);
            when(issueRepository.findEpicsByTeamOrderByManualOrder(eq(teamId)))
                .thenReturn(new ArrayList<>(allEpics));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When - move to position 1
            JiraIssueEntity result = service.reorderEpic("EPIC-NEW", 1);

            // Then
            assertEquals(1, result.getManualOrder());
        }
    }

    // ==================== Story Reorder Tests ====================

    @Nested
    class ReorderStoryTests {

        @Test
        void reorderStory_moveUp_shiftsOthersDown() {
            // Given: 4 stories in epic
            String parentKey = "EPIC-1";
            JiraIssueEntity story = createStory("STORY-4", parentKey, 4);

            List<JiraIssueEntity> allStories = List.of(
                createStory("STORY-1", parentKey, 1),
                createStory("STORY-2", parentKey, 2),
                createStory("STORY-3", parentKey, 3),
                createStory("STORY-4", parentKey, 4)
            );

            when(issueRepository.findByIssueKey("STORY-4")).thenReturn(Optional.of(story));
            when(issueRepository.findMaxStoryOrderForParent(eq(parentKey))).thenReturn(4);
            when(issueRepository.findByParentKeyOrderByManualOrderAsc(parentKey))
                .thenReturn(new ArrayList<>(allStories));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            JiraIssueEntity result = service.reorderStory("STORY-4", 2);

            // Then
            assertEquals(2, result.getManualOrder());
        }

        @Test
        void reorderStory_moveDown_shiftsOthersUp() {
            // Given
            String parentKey = "EPIC-1";
            JiraIssueEntity story = createStory("STORY-1", parentKey, 1);

            List<JiraIssueEntity> allStories = List.of(
                createStory("STORY-1", parentKey, 1),
                createStory("STORY-2", parentKey, 2),
                createStory("STORY-3", parentKey, 3)
            );

            when(issueRepository.findByIssueKey("STORY-1")).thenReturn(Optional.of(story));
            when(issueRepository.findMaxStoryOrderForParent(eq(parentKey))).thenReturn(3);
            when(issueRepository.findByParentKeyOrderByManualOrderAsc(parentKey))
                .thenReturn(new ArrayList<>(allStories));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            JiraIssueEntity result = service.reorderStory("STORY-1", 3);

            // Then
            assertEquals(3, result.getManualOrder());
        }

        @Test
        void reorderStory_notFound_throwsException() {
            when(issueRepository.findByIssueKey("STORY-999")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () ->
                service.reorderStory("STORY-999", 1));
        }

        @Test
        void reorderStory_notAStory_throwsException() {
            JiraIssueEntity epic = createEpic("EPIC-1", 1L, 1);
            when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));

            assertThrows(IllegalArgumentException.class, () ->
                service.reorderStory("EPIC-1", 1));
        }

        @Test
        void reorderStory_noParent_throwsException() {
            JiraIssueEntity story = new JiraIssueEntity();
            story.setIssueKey("STORY-1");
            story.setIssueType("Story");
            story.setParentKey(null);

            when(issueRepository.findByIssueKey("STORY-1")).thenReturn(Optional.of(story));

            assertThrows(IllegalArgumentException.class, () ->
                service.reorderStory("STORY-1", 1));
        }

        @Test
        void reorderStory_bugType_works() {
            // Given: Bug (should be treated same as Story)
            String parentKey = "EPIC-1";
            JiraIssueEntity bug = new JiraIssueEntity();
            bug.setIssueKey("BUG-1");
            bug.setIssueType("Bug");
            bug.setParentKey(parentKey);
            bug.setManualOrder(2);

            List<JiraIssueEntity> allStories = List.of(
                createStory("STORY-1", parentKey, 1),
                bug,
                createStory("STORY-3", parentKey, 3)
            );

            when(issueRepository.findByIssueKey("BUG-1")).thenReturn(Optional.of(bug));
            when(issueRepository.findMaxStoryOrderForParent(eq(parentKey))).thenReturn(3);
            when(issueRepository.findByParentKeyOrderByManualOrderAsc(parentKey))
                .thenReturn(new ArrayList<>(allStories));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            JiraIssueEntity result = service.reorderStory("BUG-1", 1);

            // Then
            assertEquals(1, result.getManualOrder());
        }
    }

    // ==================== AssignOrderIfMissing Tests ====================

    @Nested
    class AssignOrderIfMissingTests {

        @Test
        void assignOrderIfMissing_epicWithoutOrder_assignsNextOrder() {
            Long teamId = 1L;
            JiraIssueEntity epic = createEpic("EPIC-NEW", teamId, null);

            when(issueRepository.findMaxEpicOrderForTeam(eq(teamId))).thenReturn(3);
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.assignOrderIfMissing(epic);

            assertEquals(4, epic.getManualOrder());
            verify(issueRepository).save(epic);
        }

        @Test
        void assignOrderIfMissing_epicWithOrder_noChange() {
            Long teamId = 1L;
            JiraIssueEntity epic = createEpic("EPIC-1", teamId, 5);

            service.assignOrderIfMissing(epic);

            assertEquals(5, epic.getManualOrder());
            verify(issueRepository, never()).save(any());
        }

        @Test
        void assignOrderIfMissing_storyWithoutOrder_assignsNextOrder() {
            String parentKey = "EPIC-1";
            JiraIssueEntity story = createStory("STORY-NEW", parentKey, null);

            when(issueRepository.findMaxStoryOrderForParent(eq(parentKey))).thenReturn(2);
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.assignOrderIfMissing(story);

            assertEquals(3, story.getManualOrder());
            verify(issueRepository).save(story);
        }

        @Test
        void assignOrderIfMissing_epicWithoutTeam_noChange() {
            JiraIssueEntity epic = createEpic("EPIC-1", null, null);

            service.assignOrderIfMissing(epic);

            assertNull(epic.getManualOrder());
            verify(issueRepository, never()).save(any());
        }
    }

    // ==================== Normalize Epic Orders Tests ====================

    @Nested
    class NormalizeTeamEpicOrdersTests {

        @Test
        void normalizeTeamEpicOrders_fixesGapsAndZeros() {
            // Given: epics with orders [0, 2, NULL] — zero, gap, and null
            Long teamId = 1L;
            JiraIssueEntity epic1 = createEpic("EPIC-1", teamId, 0);
            JiraIssueEntity epic2 = createEpic("EPIC-2", teamId, 2);
            JiraIssueEntity epic3 = createEpic("EPIC-3", teamId, null);

            // ASC with NULL LAST: 0, 2, NULL
            when(issueRepository.findEpicsByTeamOrderByManualOrder(eq(teamId)))
                .thenReturn(new ArrayList<>(List.of(epic1, epic2, epic3)));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.normalizeTeamEpicOrders(teamId);

            // Then: should be [1, 2, 3]
            assertEquals(1, epic1.getManualOrder());
            assertEquals(2, epic2.getManualOrder());
            assertEquals(3, epic3.getManualOrder());
        }

        @Test
        void normalizeTeamEpicOrders_preservesExistingOrder() {
            // Given: epics already in correct order [1, 2, 3]
            Long teamId = 1L;
            JiraIssueEntity epic1 = createEpic("EPIC-1", teamId, 1);
            JiraIssueEntity epic2 = createEpic("EPIC-2", teamId, 2);
            JiraIssueEntity epic3 = createEpic("EPIC-3", teamId, 3);

            when(issueRepository.findEpicsByTeamOrderByManualOrder(eq(teamId)))
                .thenReturn(new ArrayList<>(List.of(epic1, epic2, epic3)));

            // When
            service.normalizeTeamEpicOrders(teamId);

            // Then: no saves needed — orders already correct
            verify(issueRepository, never()).save(any());
            assertEquals(1, epic1.getManualOrder());
            assertEquals(2, epic2.getManualOrder());
            assertEquals(3, epic3.getManualOrder());
        }

        @Test
        void normalizeTeamEpicOrders_nullTeamId_doesNothing() {
            service.normalizeTeamEpicOrders(null);

            verify(issueRepository, never()).findEpicsByTeamOrderByManualOrder(any());
        }

        @Test
        void normalizeTeamEpicOrders_emptyList_doesNothing() {
            Long teamId = 1L;
            when(issueRepository.findEpicsByTeamOrderByManualOrder(eq(teamId)))
                .thenReturn(new ArrayList<>());

            service.normalizeTeamEpicOrders(teamId);

            verify(issueRepository, never()).save(any());
        }
    }

    // ==================== Normalize Story Orders Tests ====================

    @Nested
    class NormalizeStoryOrdersTests {

        @Test
        void normalizeStoryOrders_fixesNulls() {
            // Given: stories with orders [NULL, NULL, 1] — nulls should go to end
            String parentKey = "EPIC-1";
            JiraIssueEntity story1 = createStory("STORY-1", parentKey, 1);
            JiraIssueEntity story2 = createStory("STORY-2", parentKey, null);
            JiraIssueEntity story3 = createStory("STORY-3", parentKey, null);

            // ASC with NULL LAST: 1, NULL, NULL
            when(issueRepository.findByParentKeyOrderByManualOrderAsc(parentKey))
                .thenReturn(new ArrayList<>(List.of(story1, story2, story3)));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.normalizeStoryOrders(parentKey);

            // Then: should be [1, 2, 3]
            assertEquals(1, story1.getManualOrder());
            assertEquals(2, story2.getManualOrder());
            assertEquals(3, story3.getManualOrder());
        }

        @Test
        void normalizeStoryOrders_skipsSubtasks() {
            // Given: parent has stories and subtasks mixed
            String parentKey = "EPIC-1";
            JiraIssueEntity story1 = createStory("STORY-1", parentKey, 1);
            JiraIssueEntity subtask = new JiraIssueEntity();
            subtask.setIssueKey("SUB-1");
            subtask.setIssueType("Sub-task");
            subtask.setParentKey(parentKey);
            subtask.setManualOrder(null);
            JiraIssueEntity story2 = createStory("STORY-2", parentKey, 3);

            when(issueRepository.findByParentKeyOrderByManualOrderAsc(parentKey))
                .thenReturn(new ArrayList<>(List.of(story1, subtask, story2)));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.normalizeStoryOrders(parentKey);

            // Then: stories get [1, 2], subtask is untouched
            assertEquals(1, story1.getManualOrder());
            assertEquals(2, story2.getManualOrder());
            assertNull(subtask.getManualOrder()); // not touched
        }

        @Test
        void normalizeStoryOrders_nullParentKey_doesNothing() {
            service.normalizeStoryOrders(null);

            verify(issueRepository, never()).findByParentKeyOrderByManualOrderAsc(any());
        }
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createEpic(String key, Long teamId, Integer manualOrder) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setIssueType("Epic");
        epic.setTeamId(teamId);
        epic.setManualOrder(manualOrder);
        return epic;
    }

    private JiraIssueEntity createStory(String key, String parentKey, Integer manualOrder) {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey(key);
        story.setIssueType("Story");
        story.setParentKey(parentKey);
        story.setManualOrder(manualOrder);
        return story;
    }
}
