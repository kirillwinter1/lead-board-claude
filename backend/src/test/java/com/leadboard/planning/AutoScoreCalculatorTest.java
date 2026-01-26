package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AutoScoreCalculatorTest {

    private AutoScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new AutoScoreCalculator();
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
        epic.setOriginalEstimateSeconds(100L * 3600); // 100 hours
        epic.setTimeSpentSeconds(50L * 3600); // 50 hours logged

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("5.00"), factors.get("progress")); // 50% of 10
    }

    @Test
    void progressZeroGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setOriginalEstimateSeconds(100L * 3600);
        epic.setTimeSpentSeconds(0L);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("0.00"), factors.get("progress"));
    }

    @Test
    void progressOverHundredPercentCappedAtMax() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setOriginalEstimateSeconds(100L * 3600);
        epic.setTimeSpentSeconds(150L * 3600); // Overrun

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("10.00"), factors.get("progress")); // max 10
    }

    @Test
    void progressNoEstimateGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setOriginalEstimateSeconds(null);
        epic.setTimeSpentSeconds(50L * 3600);

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
        epic.setRoughEstimateSaDays(new BigDecimal("1"));
        epic.setRoughEstimateDevDays(new BigDecimal("2"));
        epic.setRoughEstimateQaDays(new BigDecimal("1"));
        // Total 4 days - small epic

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("size");
        assertTrue(score.compareTo(new BigDecimal("3")) > 0); // Adjusted for max 5
    }

    @Test
    void sizeLargeEpicGivesLowScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setRoughEstimateSaDays(new BigDecimal("10"));
        epic.setRoughEstimateDevDays(new BigDecimal("40"));
        epic.setRoughEstimateQaDays(new BigDecimal("10"));
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
    void sizeFallsBackToOriginalEstimate() {
        JiraIssueEntity epic = createBasicEpic();
        // No rough estimate, but has original estimate
        epic.setOriginalEstimateSeconds(16L * 3600); // 16 hours = 2 days

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

    // ==================== Manual Boost Factor Tests ====================

    @Test
    void manualBoostAddsToScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setManualPriorityBoost(3);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("3"), factors.get("manualBoost"));
    }

    @Test
    void manualBoostAllowsHighValues() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setManualPriorityBoost(10);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("10"), factors.get("manualBoost"));
    }

    @Test
    void manualBoostAllowsNegativeValues() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setManualPriorityBoost(-2);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("-2"), factors.get("manualBoost"));
    }

    @Test
    void manualBoostNullGivesZero() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setManualPriorityBoost(null);

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("manualBoost"));
    }

    // ==================== Combined Score Tests ====================

    @Test
    void calculateReturnsSumOfFactors() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Developing");    // +25
        epic.setPriority("High");        // +15
        epic.setManualPriorityBoost(3);  // +3

        BigDecimal score = calculator.calculate(epic);

        // 25 + 15 + 3 + size(-5) + age(~2) = ~40
        assertTrue(score.compareTo(new BigDecimal("35")) > 0);
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
        almostDone.setOriginalEstimateSeconds(100L * 3600);
        almostDone.setTimeSpentSeconds(90L * 3600); // 90% done

        JiraIssueEntity justStarted = createBasicEpic();
        justStarted.setOriginalEstimateSeconds(100L * 3600);
        justStarted.setTimeSpentSeconds(10L * 3600); // 10% done

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
        withEstimate.setRoughEstimateSaDays(new BigDecimal("2"));
        withEstimate.setRoughEstimateDevDays(new BigDecimal("5"));
        withEstimate.setRoughEstimateQaDays(new BigDecimal("2"));

        JiraIssueEntity withoutEstimate = createBasicEpic();
        // No estimates

        BigDecimal withScore = calculator.calculate(withEstimate);
        BigDecimal withoutScore = calculator.calculate(withoutEstimate);

        // With estimate gets positive size score, without gets -5
        assertTrue(withScore.compareTo(withoutScore) > 0);
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
}
