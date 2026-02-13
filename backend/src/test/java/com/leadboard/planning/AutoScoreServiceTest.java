package com.leadboard.planning;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoScoreServiceTest {

    @Mock
    private AutoScoreCalculator calculator;

    @Mock
    private JiraIssueRepository issueRepository;

    private AutoScoreService autoScoreService;

    @BeforeEach
    void setUp() {
        autoScoreService = new AutoScoreService(calculator, issueRepository);
    }

    // ==================== recalculateAll() Tests ====================

    @Nested
    @DisplayName("recalculateAll()")
    class RecalculateAllTests {

        @Test
        @DisplayName("should recalculate AutoScore for all epics")
        void shouldRecalculateAllEpics() {
            JiraIssueEntity epic1 = createEpic("LB-1", "Epic 1");
            JiraIssueEntity epic2 = createEpic("LB-2", "Epic 2");

            when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic1, epic2));
            when(calculator.calculate(epic1)).thenReturn(BigDecimal.valueOf(75));
            when(calculator.calculate(epic2)).thenReturn(BigDecimal.valueOf(50));

            int count = autoScoreService.recalculateAll();

            assertEquals(2, count);
            verify(issueRepository, times(2)).save(any(JiraIssueEntity.class));
        }

        @Test
        @DisplayName("should update autoScoreCalculatedAt timestamp")
        void shouldUpdateAutoScoreCalculatedAt() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic");
            epic.setAutoScoreCalculatedAt(null);

            when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic));
            when(calculator.calculate(epic)).thenReturn(BigDecimal.valueOf(80));

            autoScoreService.recalculateAll();

            ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
            verify(issueRepository).save(captor.capture());

            assertNotNull(captor.getValue().getAutoScoreCalculatedAt());
        }

        @Test
        @DisplayName("should handle empty epic list")
        void shouldHandleEmptyEpicList() {
            when(issueRepository.findByBoardCategory("EPIC")).thenReturn(Collections.emptyList());

            int count = autoScoreService.recalculateAll();

            assertEquals(0, count);
            verify(issueRepository, never()).save(any(JiraIssueEntity.class));
        }

        @Test
        @DisplayName("should handle both English and Russian epic types via board_category")
        void shouldHandleBothEpicTypesViaBoardCategory() {
            JiraIssueEntity englishEpic = createEpic("LB-1", "English Epic");
            englishEpic.setIssueType("Epic");

            JiraIssueEntity russianEpic = createEpic("LB-2", "Russian Epic");
            russianEpic.setIssueType("Эпик");

            when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(englishEpic, russianEpic));
            when(calculator.calculate(any())).thenReturn(BigDecimal.valueOf(60));

            int count = autoScoreService.recalculateAll();

            assertEquals(2, count);
        }
    }

    // ==================== recalculateForTeam() Tests ====================

    @Nested
    @DisplayName("recalculateForTeam()")
    class RecalculateForTeamTests {

        @Test
        @DisplayName("should recalculate only team epics")
        void shouldRecalculateOnlyTeamEpics() {
            JiraIssueEntity epic = createEpic("LB-1", "Team Epic");
            epic.setTeamId(1L);

            when(issueRepository.findByBoardCategoryAndTeamId("EPIC", 1L)).thenReturn(List.of(epic));
            when(calculator.calculate(epic)).thenReturn(BigDecimal.valueOf(90));

            int count = autoScoreService.recalculateForTeam(1L);

            assertEquals(1, count);
            verify(issueRepository).save(epic);
        }

        @Test
        @DisplayName("should return correct count for team")
        void shouldReturnUpdatedCount() {
            JiraIssueEntity epic1 = createEpic("LB-1", "Epic 1");
            JiraIssueEntity epic2 = createEpic("LB-2", "Epic 2");
            JiraIssueEntity epic3 = createEpic("LB-3", "Epic 3");

            when(issueRepository.findByBoardCategoryAndTeamId("EPIC", 1L))
                    .thenReturn(List.of(epic1, epic2, epic3));
            when(calculator.calculate(any())).thenReturn(BigDecimal.valueOf(50));

            int count = autoScoreService.recalculateForTeam(1L);

            assertEquals(3, count);
        }

        @Test
        @DisplayName("should return zero for team with no epics")
        void shouldReturnZeroForTeamWithNoEpics() {
            when(issueRepository.findByBoardCategoryAndTeamId("EPIC", 99L)).thenReturn(Collections.emptyList());

            int count = autoScoreService.recalculateForTeam(99L);

            assertEquals(0, count);
        }
    }

    // ==================== recalculateForEpic() Tests ====================

    @Nested
    @DisplayName("recalculateForEpic()")
    class RecalculateForEpicTests {

        @Test
        @DisplayName("should delegate to calculator")
        void shouldDelegateToCalculator() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(calculator.calculate(epic)).thenReturn(BigDecimal.valueOf(85));

            BigDecimal score = autoScoreService.recalculateForEpic("LB-1");

            assertEquals(BigDecimal.valueOf(85), score);
            verify(calculator).calculate(epic);
        }

        @Test
        @DisplayName("should save result to repository")
        void shouldSaveResult() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic");

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(calculator.calculate(epic)).thenReturn(BigDecimal.valueOf(70));

            autoScoreService.recalculateForEpic("LB-1");

            ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
            verify(issueRepository).save(captor.capture());

            assertEquals(BigDecimal.valueOf(70), captor.getValue().getAutoScore());
            assertNotNull(captor.getValue().getAutoScoreCalculatedAt());
        }

        @Test
        @DisplayName("should return null when epic not found")
        void shouldReturnNullWhenNotFound() {
            when(issueRepository.findByIssueKey("LB-999")).thenReturn(Optional.empty());

            BigDecimal score = autoScoreService.recalculateForEpic("LB-999");

            assertNull(score);
            verify(calculator, never()).calculate(any());
            verify(issueRepository, never()).save(any());
        }
    }

    // ==================== getScoreDetails() Tests ====================

    @Nested
    @DisplayName("getScoreDetails()")
    class GetScoreDetailsTests {

        @Test
        @DisplayName("should return score details for existing epic")
        void shouldReturnScoreDetails() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic");
            epic.setAutoScoreCalculatedAt(OffsetDateTime.now());

            Map<String, BigDecimal> factors = Map.of(
                    "priority", BigDecimal.valueOf(20),
                    "dueDate", BigDecimal.valueOf(30),
                    "progress", BigDecimal.valueOf(25)
            );

            when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
            when(calculator.calculateFactors(epic)).thenReturn(factors);

            AutoScoreService.AutoScoreDetails details = autoScoreService.getScoreDetails("LB-1");

            assertNotNull(details);
            assertEquals("LB-1", details.epicKey());
            assertEquals(BigDecimal.valueOf(75), details.totalScore());
            assertEquals(3, details.factors().size());
        }

        @Test
        @DisplayName("should return null when epic not found")
        void shouldReturnNullWhenEpicNotFound() {
            when(issueRepository.findByIssueKey("LB-999")).thenReturn(Optional.empty());

            AutoScoreService.AutoScoreDetails details = autoScoreService.getScoreDetails("LB-999");

            assertNull(details);
        }
    }

    // ==================== getEpicsByPriority() Tests ====================

    @Nested
    @DisplayName("getEpicsByPriority()")
    class GetEpicsByPriorityTests {

        @Test
        @DisplayName("should return epics sorted by AutoScore")
        void shouldReturnEpicsSortedByScore() {
            JiraIssueEntity epic1 = createEpic("LB-1", "Epic 1");
            JiraIssueEntity epic2 = createEpic("LB-2", "Epic 2");

            when(issueRepository.findByBoardCategoryAndTeamIdOrderByAutoScoreDesc("EPIC", 1L))
                    .thenReturn(List.of(epic2, epic1));

            List<JiraIssueEntity> epics = autoScoreService.getEpicsByPriority(1L);

            assertEquals(2, epics.size());
            assertEquals("LB-2", epics.get(0).getIssueKey());
            assertEquals("LB-1", epics.get(1).getIssueKey());
        }
    }

    // ==================== getEpicsByPriorityAndStatus() Tests ====================

    @Nested
    @DisplayName("getEpicsByPriorityAndStatus()")
    class GetEpicsByPriorityAndStatusTests {

        @Test
        @DisplayName("should filter by status and sort by AutoScore")
        void shouldFilterByStatusAndSortByScore() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic");
            epic.setStatus("Developing");

            when(issueRepository.findByBoardCategoryAndTeamIdAndStatusInOrderByAutoScoreDesc(
                    "EPIC", 1L, List.of("Developing", "В разработке")))
                    .thenReturn(List.of(epic));

            List<JiraIssueEntity> epics = autoScoreService.getEpicsByPriorityAndStatus(
                    1L, List.of("Developing", "В разработке"));

            assertEquals(1, epics.size());
            assertEquals("Developing", epics.get(0).getStatus());
        }
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createEpic(String key, String summary) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setIssueId("id-" + key);
        entity.setSummary(summary);
        entity.setIssueType("Epic");
        entity.setBoardCategory("EPIC");
        entity.setProjectKey("LB");
        entity.setSubtask(false);
        return entity;
    }
}
