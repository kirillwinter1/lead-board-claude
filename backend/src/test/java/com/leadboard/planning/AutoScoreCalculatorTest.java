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

    // ==================== Status Factor Tests ====================

    @Test
    void statusInProgressGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("In Progress");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("20"), factors.get("status"));
    }

    @Test
    void statusInProgressRussianGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("В работе");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("20"), factors.get("status"));
    }

    @Test
    void statusBacklogGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Backlog");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("status"));
    }

    @Test
    void statusDoneGivesZeroScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setStatus("Done");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(BigDecimal.ZERO, factors.get("status"));
    }

    // ==================== Progress Factor Tests ====================

    @Test
    void progressHalfwayGivesHalfScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setOriginalEstimateSeconds(100L * 3600); // 100 часов
        epic.setTimeSpentSeconds(50L * 3600); // 50 часов залогировано

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("7.50"), factors.get("progress"));
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
        epic.setTimeSpentSeconds(150L * 3600); // Перерасход

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("15.00"), factors.get("progress"));
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
        epic.setDueDate(LocalDate.now().minusDays(5)); // Просрочен 5 дней

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

    // ==================== Priority Factor Tests ====================

    @Test
    void priorityHighestGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("Highest");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("15"), factors.get("priority"));
    }

    @Test
    void priorityBlockerGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("Blocker");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("15"), factors.get("priority"));
    }

    @Test
    void priorityHighGives10Score() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("High");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("10"), factors.get("priority"));
    }

    @Test
    void priorityMediumGives5Score() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("Medium");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("5"), factors.get("priority"));
    }

    @Test
    void priorityLowGives2Score() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setPriority("Low");

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("2"), factors.get("priority"));
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

    // ==================== Size Factor Tests ====================

    @Test
    void sizeSmallEpicGivesHighScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setRoughEstimateSaDays(new BigDecimal("1"));
        epic.setRoughEstimateDevDays(new BigDecimal("2"));
        epic.setRoughEstimateQaDays(new BigDecimal("1"));
        // Итого 4 дня - маленький эпик

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("size");
        assertTrue(score.compareTo(new BigDecimal("6")) > 0);
    }

    @Test
    void sizeLargeEpicGivesLowScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setRoughEstimateSaDays(new BigDecimal("10"));
        epic.setRoughEstimateDevDays(new BigDecimal("40"));
        epic.setRoughEstimateQaDays(new BigDecimal("10"));
        // Итого 60 дней - большой эпик

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("size");
        assertTrue(score.compareTo(new BigDecimal("3")) < 0);
    }

    @Test
    void sizeNoEstimateGivesMiddleScore() {
        JiraIssueEntity epic = createBasicEpic();
        // Нет оценок

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("5"), factors.get("size"));
    }

    @Test
    void sizeFallsBackToOriginalEstimate() {
        JiraIssueEntity epic = createBasicEpic();
        // Нет rough estimate, но есть original estimate
        epic.setOriginalEstimateSeconds(16L * 3600); // 16 часов = 2 дня

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("size");
        assertTrue(score.compareTo(new BigDecimal("7")) > 0);
    }

    // ==================== Age Factor Tests ====================

    @Test
    void ageNewEpicGivesLowScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setJiraCreatedAt(OffsetDateTime.now().minusDays(1));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("age");
        // Для 1 дня: 10 * log(2) / log(365) ≈ 1.18
        assertTrue(score.compareTo(new BigDecimal("2")) < 0);
    }

    @Test
    void ageOldEpicGivesHighScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setJiraCreatedAt(OffsetDateTime.now().minusDays(180));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("age");
        assertTrue(score.compareTo(new BigDecimal("7")) > 0);
    }

    @Test
    void ageVeryOldEpicGivesMaxScore() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setJiraCreatedAt(OffsetDateTime.now().minusDays(365));

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        BigDecimal score = factors.get("age");
        assertTrue(score.compareTo(new BigDecimal("9")) > 0);
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
        epic.setManualPriorityBoost(10); // No longer capped

        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);

        assertEquals(new BigDecimal("10"), factors.get("manualBoost"));
    }

    @Test
    void manualBoostAllowsNegativeValues() {
        JiraIssueEntity epic = createBasicEpic();
        epic.setManualPriorityBoost(-2); // Negative allowed for drag-down

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
        epic.setStatus("In Progress"); // +20
        epic.setPriority("High");       // +10
        epic.setManualPriorityBoost(3); // +3

        BigDecimal score = calculator.calculate(epic);

        // Минимум 33 (статус + приоритет + boost) + size (5 при отсутствии оценки)
        assertTrue(score.compareTo(new BigDecimal("30")) > 0);
    }

    @Test
    void highPriorityEpicScoresHigherThanLowPriority() {
        JiraIssueEntity highPriority = createBasicEpic();
        highPriority.setStatus("In Progress");
        highPriority.setPriority("Highest");
        highPriority.setDueDate(LocalDate.now().plusDays(7));

        JiraIssueEntity lowPriority = createBasicEpic();
        lowPriority.setStatus("Backlog");
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
        almostDone.setTimeSpentSeconds(90L * 3600); // 90% готово

        JiraIssueEntity justStarted = createBasicEpic();
        justStarted.setOriginalEstimateSeconds(100L * 3600);
        justStarted.setTimeSpentSeconds(10L * 3600); // 10% готово

        BigDecimal almostDoneScore = calculator.calculate(almostDone);
        BigDecimal justStartedScore = calculator.calculate(justStarted);

        assertTrue(almostDoneScore.compareTo(justStartedScore) > 0);
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createBasicEpic() {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("TEST-123");
        epic.setIssueId("12345");
        epic.setProjectKey("TEST");
        epic.setSummary("Test Epic");
        epic.setStatus("Backlog");
        epic.setIssueType("Epic");
        epic.setSubtask(false);
        epic.setCreatedAt(OffsetDateTime.now().minusDays(30));
        epic.setUpdatedAt(OffsetDateTime.now());
        return epic;
    }
}
