package com.leadboard.planning;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.rice.RiceAssessmentService;
import com.leadboard.rice.dto.RiceAssessmentDto;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoScoreCalculatorTest {

    @Mock private JiraIssueRepository issueRepository;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private RiceAssessmentService riceAssessmentService;

    private AutoScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        // WorkflowConfigService returns 0 by default for getStatusScoreWeight,
        // so the fallback substring matching will be used in tests
        calculator = new AutoScoreCalculator(issueRepository, workflowConfigService, riceAssessmentService);
    }

    // ==================== Status Factor Tests (Updated 2026-01-26) ====================

    @Test
    void statusDevelopingGivesHighScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Developing");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("25"), factors.get("status"));
    }

    @Test
    void statusInProgressGivesHighScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("In Progress");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("25"), factors.get("status"));
    }

    @Test
    void statusInProgressRussianGivesHighScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("В работе");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("25"), factors.get("status"));
    }

    @Test
    void statusAcceptanceGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Acceptance");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("30"), factors.get("status"));
    }

    @Test
    void statusE2ETestingGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("E2E Testing");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("30"), factors.get("status"));
    }

    @Test
    void statusPlannedGivesMediumScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Запланировано");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("15"), factors.get("status"));
    }

    @Test
    void statusRoughEstimateGivesLowScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Rough Estimate");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("10"), factors.get("status"));
    }

    @Test
    void statusRequirementsGivesLowScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Requirements");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("5"), factors.get("status"));
    }

    @Test
    void statusNewGivesNegativeScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Новое");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("-5"), factors.get("status"));
    }

    @Test
    void statusBacklogGivesNegativeScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Backlog");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("-5"), factors.get("status"));
    }

    @Test
    void statusDoneGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Done");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("status"));
    }

    // ==================== Progress Factor Tests (weight = 10) ====================

    @Test
    void progressHalfwayGivesHalfScore() {
        JiraIssueEntity epic = createBasicEpic();
        JiraIssueEntity story = createStory("TEST-200", "TEST-123");
        JiraIssueEntity subtask = createSubtask("TEST-300", "TEST-200", 100L * 3600, 50L * 3600);

        when(issueRepository.findByParentKey("TEST-123")).thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("TEST-200"))).thenReturn(List.of(subtask));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("5.00"), factors.get("progress")); // 50% of 10
    }

    @Test
    void progressZeroGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        JiraIssueEntity story = createStory("TEST-200", "TEST-123");
        JiraIssueEntity subtask = createSubtask("TEST-300", "TEST-200", 100L * 3600, 0L);

        when(issueRepository.findByParentKey("TEST-123")).thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("TEST-200"))).thenReturn(List.of(subtask));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("0.00"), factors.get("progress"));
    }

    @Test
    void progressOverHundredPercentCappedAtMax() {
        JiraIssueEntity epic = createBasicEpic();
        JiraIssueEntity story = createStory("TEST-200", "TEST-123");
        JiraIssueEntity subtask = createSubtask("TEST-300", "TEST-200", 100L * 3600, 150L * 3600);

        when(issueRepository.findByParentKey("TEST-123")).thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("TEST-200"))).thenReturn(List.of(subtask));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("10.00"), factors.get("progress")); // max 10
    }

    @Test
    void progressNoSubtasksGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();

        when(issueRepository.findByParentKey("TEST-123")).thenReturn(Collections.emptyList());

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("progress"));
    }

    @Test
    void progressNoEstimateOnSubtasksGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        JiraIssueEntity story = createStory("TEST-200", "TEST-123");
        JiraIssueEntity subtask = createSubtask("TEST-300", "TEST-200", 0L, 50L * 3600);

        when(issueRepository.findByParentKey("TEST-123")).thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("TEST-200"))).thenReturn(List.of(subtask));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("progress"));
    }

    // ==================== Due Date Factor Tests ====================

    @Test
    void dueDateOverdueGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setDueDate(LocalDate.now().minusDays(5)); // 5 days overdue

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("25"), factors.get("dueDate"));
    }

    @Test
    void dueDateTomorrowGivesHighScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setDueDate(LocalDate.now().plusDays(1));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("dueDate");
        assertTrue(score.compareTo(new BigDecimal("20")) >= 0);
        assertTrue(score.compareTo(new BigDecimal("25")) <= 0);
    }

    @Test
    void dueDateInTwoWeeksGivesMediumScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setDueDate(LocalDate.now().plusDays(14));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("dueDate");
        assertTrue(score.compareTo(new BigDecimal("15")) >= 0);
        assertTrue(score.compareTo(new BigDecimal("20")) <= 0);
    }

    @Test
    void dueDateInMonthGivesLowScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setDueDate(LocalDate.now().plusDays(30));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("dueDate");
        assertTrue(score.compareTo(new BigDecimal("10")) >= 0);
        assertTrue(score.compareTo(new BigDecimal("15")) <= 0);
    }

    @Test
    void dueDateFarAwayGivesLowScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setDueDate(LocalDate.now().plusDays(60));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("dueDate");
        assertTrue(score.compareTo(new BigDecimal("10")) < 0);
    }

    @Test
    void dueDateNullGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setDueDate(null);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("dueDate"));
    }

    // ==================== Priority Factor Tests (weight = 20) ====================

    @Test
    void priorityHighestGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("Highest");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("20"), factors.get("priority")); // Updated from 15 to 20
    }

    @Test
    void priorityBlockerGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("Blocker");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("20"), factors.get("priority")); // Updated from 15 to 20
    }

    @Test
    void priorityHighGives15Score() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("High");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("15"), factors.get("priority")); // Updated from 10 to 15
    }

    @Test
    void priorityMediumGives10Score() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("Medium");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("10"), factors.get("priority")); // Updated from 5 to 10
    }

    @Test
    void priorityLowGives5Score() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("Low");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("5"), factors.get("priority")); // Updated from 2 to 5
    }

    @Test
    void priorityLowestGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("Lowest");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("priority"));
    }

    @Test
    void priorityNullGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority(null);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("priority"));
    }

    // ==================== Size Factor Tests (weight = 5, no estimate = -5) ====================

    @Test
    void sizeSmallEpicGivesHighScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setRoughEstimate("SA", new BigDecimal("1"));
        epic.setRoughEstimate("DEV", new BigDecimal("2"));
        epic.setRoughEstimate("QA", new BigDecimal("1"));
        // Total 4 days - small epic

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("size");
        assertTrue(score.compareTo(new BigDecimal("3")) > 0); // Adjusted for max 5
    }

    @Test
    void sizeLargeEpicGivesLowScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setRoughEstimate("SA", new BigDecimal("10"));
        epic.setRoughEstimate("DEV", new BigDecimal("40"));
        epic.setRoughEstimate("QA", new BigDecimal("10"));
        // Total 60 days - large epic

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("size");
        assertTrue(score.compareTo(new BigDecimal("2")) < 0); // Adjusted for max 5
    }

    @Test
    void sizeNoEstimateGivesNegativeScore() {
        JiraIssueEntity epic = createBasicEpic();
        // No estimates - should be penalized

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("-5"), factors.get("size")); // Changed from 5 to -5
    }

    @Test
    void sizeFallsBackToSubtaskEstimates() {
        JiraIssueEntity epic = createBasicEpic();
        // No rough estimate, but subtasks have estimates
        JiraIssueEntity story = createStory("TEST-200", "TEST-123");
        JiraIssueEntity subtask = createSubtask("TEST-300", "TEST-200", 16L * 3600, 0L); // 16 hours = 2 days

        when(issueRepository.findByParentKey("TEST-123")).thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("TEST-200"))).thenReturn(List.of(subtask));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("size");
        assertTrue(score.compareTo(new BigDecimal("3")) > 0); // Adjusted for max 5
    }

    // ==================== Age Factor Tests (weight = 5) ====================

    @Test
    void ageNewEpicGivesLowScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setJiraCreatedAt(OffsetDateTime.now().minusDays(1));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("age");
        // For 1 day: 5 * log(2) / log(365) ~ 0.59
        assertTrue(score.compareTo(new BigDecimal("1")) < 0);
    }

    @Test
    void ageOldEpicGivesHighScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setJiraCreatedAt(OffsetDateTime.now().minusDays(180));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("age");
        assertTrue(score.compareTo(new BigDecimal("3.5")) > 0); // Adjusted for max 5
    }

    @Test
    void ageVeryOldEpicGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setJiraCreatedAt(OffsetDateTime.now().minusDays(365));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("age");
        assertTrue(score.compareTo(new BigDecimal("4.5")) > 0); // Adjusted for max 5
    }

    @Test
    void ageNullGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setJiraCreatedAt(null);
        epic.setCreatedAt(null);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("age"));
    }

    // ==================== Flagged Penalty Tests ====================

    @Test
    void flaggedEpicGetsMinusHundredPenalty() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setFlagged(true);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("-100"), factors.get("flagged"));
    }

    @Test
    void notFlaggedEpicGetsZeroPenalty() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setFlagged(false);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("flagged"));
    }

    @Test
    void flaggedNullGetsZeroPenalty() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setFlagged(null);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("flagged"));
    }

    @Test
    void flaggedEpicScoresLowerThanUnflagged() {
        JiraIssueEntity flagged = createBasicEpic();
        flagged.setStatus("Developing");
        flagged.setPriority("Highest");
        flagged.setFlagged(true);

        JiraIssueEntity unflagged = createBasicEpic();
        unflagged.setStatus("Новое");
        unflagged.setPriority("Low");
        unflagged.setFlagged(false);

        BigDecimal flaggedScore = calculator.calculate(flagged);
        BigDecimal unflaggedScore = calculator.calculate(unflagged);

        assertTrue(flaggedScore.compareTo(unflaggedScore) < 0,
                "Flagged epic (%s) should score lower than unflagged (%s)".formatted(flaggedScore, unflaggedScore));
    }

    // ==================== Combined Score Tests ====================

    @Test
    void calculateReturnsSumOfFactors() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Developing");    // +25
        epic.setPriority("High");        // +15

        BigDecimal score = calculator.calculate(epic);

        // 25 + 15 + size(-5) + age(~2) = ~37
        assertTrue(score.compareTo(new BigDecimal("32")) > 0);
    }

    @Test
    void highPriorityEpicScoresHigherThanLowPriority() {
        JiraIssueEntity highPriority = createBasicEpic();
        highPriority.setStatus("Developing");
        highPriority.setPriority("Highest");
        highPriority.setDueDate(LocalDate.now().plusDays(7));

        JiraIssueEntity lowPriority = createBasicEpic();
        lowPriority.setStatus("Новое"); // -5 for status
        lowPriority.setPriority("Low");
        lowPriority.setDueDate(LocalDate.now().plusDays(60));

        BigDecimal highScore = calculator.calculate(highPriority);
        BigDecimal lowScore = calculator.calculate(lowPriority);

        assertTrue(highScore.compareTo(lowScore) > 0);
    }

    @Test
    void overdueEpicScoresHigherThanFutureEpic() {
        JiraIssueEntity overdue = createBasicEpic();
        overdue.setDueDate(LocalDate.now().minusDays(5));

        JiraIssueEntity future = createBasicEpic();
        future.setDueDate(LocalDate.now().plusDays(30));

        BigDecimal overdueScore = calculator.calculate(overdue);
        BigDecimal futureScore = calculator.calculate(future);

        assertTrue(overdueScore.compareTo(futureScore) > 0);
    }

    @Test
    void almostDoneEpicScoresHigherThanJustStarted() {
        JiraIssueEntity almostDone = createBasicEpic();
        almostDone.setIssueKey("EPIC-1");
        JiraIssueEntity story1 = createStory("STORY-1", "EPIC-1");
        JiraIssueEntity subtask1 = createSubtask("SUB-1", "STORY-1", 100L * 3600, 90L * 3600);

        when(issueRepository.findByParentKey("EPIC-1")).thenReturn(List.of(story1));
        when(issueRepository.findByParentKeyIn(List.of("STORY-1"))).thenReturn(List.of(subtask1));

        JiraIssueEntity justStarted = createBasicEpic();
        justStarted.setIssueKey("EPIC-2");
        JiraIssueEntity story2 = createStory("STORY-2", "EPIC-2");
        JiraIssueEntity subtask2 = createSubtask("SUB-2", "STORY-2", 100L * 3600, 10L * 3600);

        when(issueRepository.findByParentKey("EPIC-2")).thenReturn(List.of(story2));
        when(issueRepository.findByParentKeyIn(List.of("STORY-2"))).thenReturn(List.of(subtask2));

        BigDecimal almostDoneScore = calculator.calculate(almostDone);
        BigDecimal justStartedScore = calculator.calculate(justStarted);

        assertTrue(almostDoneScore.compareTo(justStartedScore) > 0);
    }

    @Test
    void developingEpicScoresHigherThanNewEpic() {
        JiraIssueEntity developing = createBasicEpic();
        developing.setStatus("Developing");

        JiraIssueEntity newEpic = createBasicEpic();
        newEpic.setStatus("Новое");

        BigDecimal developingScore = calculator.calculate(developing);
        BigDecimal newScore = calculator.calculate(newEpic);

        // Developing: +25, New: -5 -> difference of 30 points
        assertTrue(developingScore.compareTo(newScore) > 0);
        assertTrue(developingScore.subtract(newScore).compareTo(new BigDecimal("25")) >= 0);
    }

    @Test
    void epicWithEstimateScoresHigherThanWithout() {
        JiraIssueEntity withEstimate = createBasicEpic();
        withEstimate.setRoughEstimate("SA", new BigDecimal("2"));
        withEstimate.setRoughEstimate("DEV", new BigDecimal("5"));
        withEstimate.setRoughEstimate("QA", new BigDecimal("2"));

        JiraIssueEntity withoutEstimate = createBasicEpic();
        // No estimates

        BigDecimal withScore = calculator.calculate(withEstimate);
        BigDecimal withoutScore = calculator.calculate(withoutEstimate);

        // With estimate gets positive size score, without gets -5
        assertTrue(withScore.compareTo(withoutScore) > 0);
    }

    // ==================== RICE Boost Tests ====================

    @Test
    void riceBoost_zeroWhenNoAssessment() {
        JiraIssueEntity epic = createBasicEpic();

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(0, BigDecimal.ZERO.compareTo(factors.get("riceBoost")));
    }

    @Test
    void riceBoost_calculatesFromOwnRice() {
        JiraIssueEntity epic = createBasicEpic();

        // Epic has its own RICE assessment with normalized score of 80
        RiceAssessmentDto riceDto = new RiceAssessmentDto(
                1L, "TEST-123", 1L, "Business", null,
                new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("0.8"),
                null, null, new BigDecimal("2"),
                new BigDecimal("20.00"), new BigDecimal("80.00"),
                List.of()
        );
        when(riceAssessmentService.getAssessment("TEST-123")).thenReturn(riceDto);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        // 80 / 100 * 15 = 12.00
        assertEquals(0, new BigDecimal("12.00").compareTo(factors.get("riceBoost")));
    }

    @Test
    void riceBoost_inheritsFromProjectParent() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setParentKey("PROJ-1");

        // Parent is a PROJECT
        JiraIssueEntity project = new JiraIssueEntity();
        project.setIssueKey("PROJ-1");
        project.setBoardCategory("PROJECT");
        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));

        // Project has RICE with normalized 60
        RiceAssessmentDto projectRice = new RiceAssessmentDto(
                1L, "PROJ-1", 1L, "Business", null,
                new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("0.8"),
                null, null, new BigDecimal("2"),
                new BigDecimal("20.00"), new BigDecimal("60.00"),
                List.of()
        );
        when(riceAssessmentService.getAssessment("PROJ-1")).thenReturn(projectRice);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        // 60 / 100 * 15 = 9.00
        assertEquals(0, new BigDecimal("9.00").compareTo(factors.get("riceBoost")));
    }

    @Test
    void riceBoost_fallsBackToOwnWhenProjectHasNoRice() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setParentKey("PROJ-1");

        // Parent is a PROJECT but has no RICE
        JiraIssueEntity project = new JiraIssueEntity();
        project.setIssueKey("PROJ-1");
        project.setBoardCategory("PROJECT");
        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));
        when(riceAssessmentService.getAssessment("PROJ-1")).thenReturn(null);

        // Epic has its own RICE
        RiceAssessmentDto epicRice = new RiceAssessmentDto(
                2L, "TEST-123", 1L, "Business", null,
                new BigDecimal("3"), new BigDecimal("5"), new BigDecimal("1.0"),
                null, null, new BigDecimal("1"),
                new BigDecimal("15.00"), new BigDecimal("40.00"),
                List.of()
        );
        when(riceAssessmentService.getAssessment("TEST-123")).thenReturn(epicRice);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        // 40 / 100 * 15 = 6.00
        assertEquals(0, new BigDecimal("6.00").compareTo(factors.get("riceBoost")));
    }

    @Test
    void riceBoost_preloadBatchMode() {
        JiraIssueEntity epic1 = createBasicEpic();
        epic1.setIssueKey("EPIC-1");
        epic1.setParentKey("PROJ-1");

        JiraIssueEntity epic2 = createBasicEpic();
        epic2.setIssueKey("EPIC-2");

        // PROJ-1 is a PROJECT
        JiraIssueEntity project = new JiraIssueEntity();
        project.setIssueKey("PROJ-1");
        project.setBoardCategory("PROJECT");
        when(issueRepository.findByIssueKeyIn(List.of("PROJ-1"))).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));

        // Batch RICE assessments
        RiceAssessmentDto projectRice = new RiceAssessmentDto(
                1L, "PROJ-1", 1L, "Business", null,
                new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("0.8"),
                null, null, new BigDecimal("2"),
                new BigDecimal("20.00"), new BigDecimal("70.00"),
                List.of()
        );
        RiceAssessmentDto epic2Rice = new RiceAssessmentDto(
                2L, "EPIC-2", 1L, "Business", null,
                new BigDecimal("3"), new BigDecimal("5"), new BigDecimal("1.0"),
                null, null, new BigDecimal("1"),
                new BigDecimal("15.00"), new BigDecimal("50.00"),
                List.of()
        );
        when(riceAssessmentService.getAssessments(anyCollection()))
                .thenReturn(Map.of("PROJ-1", projectRice, "EPIC-2", epic2Rice));

        calculator.preloadRiceData(List.of(epic1, epic2));

        Map<String, BigDecimal> factors1 = calculator.calculateFactors(epic1);
        Map<String, BigDecimal> factors2 = calculator.calculateFactors(epic2);

        // Epic1 inherits PROJ-1 RICE: 70/100*15 = 10.50
        assertEquals(0, new BigDecimal("10.50").compareTo(factors1.get("riceBoost")));
        // Epic2 uses own RICE: 50/100*15 = 7.50
        assertEquals(0, new BigDecimal("7.50").compareTo(factors2.get("riceBoost")));

        calculator.clearRiceData();
    }

    // ==================== Alignment Boost Tests ====================

    @Test
    void alignmentBoost_calculatesFromPreloadedData() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setIssueKey("EPIC-1");

        calculator.preloadAlignmentData(Map.of("EPIC-1", 7));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(0, new BigDecimal("7.00").compareTo(factors.get("alignmentBoost")));

        calculator.clearAlignmentData();
    }

    @Test
    void alignmentBoost_cappedAt10() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setIssueKey("EPIC-1");

        calculator.preloadAlignmentData(Map.of("EPIC-1", 25));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(0, new BigDecimal("10.00").compareTo(factors.get("alignmentBoost")));

        calculator.clearAlignmentData();
    }

    @Test
    void alignmentBoost_zeroWhenNoData() {
        JiraIssueEntity epic = createBasicEpic();

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(0, BigDecimal.ZERO.compareTo(factors.get("alignmentBoost")));
    }

    @Test
    void alignmentBoost_factorAppearsInCalculateFactors() {
        JiraIssueEntity epic = createBasicEpic();

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertTrue(factors.containsKey("alignmentBoost"));
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createBasicEpic() {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("TEST-123");
        epic.setIssueId("12345");
        epic.setProjectKey("TEST");
        epic.setSummary("Test Epic");
        epic.setStatus("Done"); // Neutral status
        epic.setIssueType("Epic");
        epic.setSubtask(false);
        epic.setCreatedAt(OffsetDateTime.now().minusDays(30));
        epic.setUpdatedAt(OffsetDateTime.now());
        return epic;
    }

    private JiraIssueEntity createStory(String key, String parentKey) {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey(key);
        story.setIssueId(key);
        story.setProjectKey("TEST");
        story.setSummary("Test Story");
        story.setStatus("Done");
        story.setIssueType("Story");
        story.setParentKey(parentKey);
        story.setSubtask(false);
        return story;
    }

    private JiraIssueEntity createSubtask(String key, String parentKey, long estimateSeconds, long loggedSeconds) {
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setIssueKey(key);
        subtask.setIssueId(key);
        subtask.setProjectKey("TEST");
        subtask.setSummary("Test Subtask");
        subtask.setStatus("Done");
        subtask.setIssueType("Sub-task");
        subtask.setParentKey(parentKey);
        subtask.setSubtask(true);
        subtask.setOriginalEstimateSeconds(estimateSeconds);
        subtask.setTimeSpentSeconds(loggedSeconds);
        return subtask;
    }
}
