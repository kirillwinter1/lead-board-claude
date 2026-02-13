package com.leadboard.planning;

import com.leadboard.config.service.WorkflowConfigService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryAutoScoreServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    private StoryAutoScoreService service;

    @BeforeEach
    void setUp() {
        // WorkflowConfigService returns 0 by default for getStoryStatusSortOrder,
        // so the fallback matchesStatus will be used in tests
        service = new StoryAutoScoreService(issueRepository, workflowConfigService);
    }

    // ==================== Issue Type Factor Tests ====================

    @Test
    void bugGivesHighestIssueTypeScore() {
        JiraIssueEntity story = createBasicStory();
        story.setIssueType("Bug");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("100"), breakdown.get("issueType"));
    }

    @Test
    void bugRussianGivesHighestIssueTypeScore() {
        JiraIssueEntity story = createBasicStory();
        story.setIssueType("Баг");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("100"), breakdown.get("issueType"));
    }

    @Test
    void storyGivesZeroIssueTypeScore() {
        JiraIssueEntity story = createBasicStory();
        story.setIssueType("Story");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(BigDecimal.ZERO, breakdown.get("issueType"));
    }

    // ==================== Status Factor Tests ====================

    @Test
    void statusNewGivesZeroScore() {
        JiraIssueEntity story = createBasicStory();
        story.setStatus("New");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(BigDecimal.ZERO, breakdown.get("status"));
    }

    @Test
    void statusTestReviewGives90Score() {
        JiraIssueEntity story = createBasicStory();
        story.setStatus("Test Review");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("90"), breakdown.get("status"));
    }

    @Test
    void statusDevelopmentGives50Score() {
        JiraIssueEntity story = createBasicStory();
        story.setStatus("Development");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("50"), breakdown.get("status"));
    }

    @Test
    void statusReadyToReleaseGivesMaxScore() {
        JiraIssueEntity story = createBasicStory();
        story.setStatus("Ready to Release");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("100"), breakdown.get("status"));
    }

    @Test
    void statusReadyGives10Score() {
        JiraIssueEntity story = createBasicStory();
        story.setStatus("Ready");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("10"), breakdown.get("status"));
    }

    @Test
    void statusAnalysisGives20Score() {
        JiraIssueEntity story = createBasicStory();
        story.setStatus("Analysis");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("20"), breakdown.get("status"));
    }

    @Test
    void statusWaitingDevGives40Score() {
        JiraIssueEntity story = createBasicStory();
        story.setStatus("Waiting Dev");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("40"), breakdown.get("status"));
    }

    @Test
    void statusTestingGives80Score() {
        JiraIssueEntity story = createBasicStory();
        story.setStatus("Testing");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("80"), breakdown.get("status"));
    }

    @Test
    void statusDoneGivesZeroScore() {
        JiraIssueEntity story = createBasicStory();
        story.setStatus("Done");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(BigDecimal.ZERO, breakdown.get("status"));
    }

    // ==================== Progress Factor Tests ====================

    @Test
    void progressZeroGivesZeroScore() {
        JiraIssueEntity story = createStoryWithSubtasks(100L, 0L);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(0, breakdown.get("progress").compareTo(BigDecimal.ZERO));
    }

    @Test
    void progressHalfwayGivesHalfScore() {
        JiraIssueEntity story = createStoryWithSubtasks(100L * 3600, 50L * 3600);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        // Should be around 15.0
        assertEquals(0, breakdown.get("progress").compareTo(new BigDecimal("15.0")));
    }

    @Test
    void progressFullGivesMaxScore() {
        JiraIssueEntity story = createStoryWithSubtasks(100L * 3600, 100L * 3600);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        // Should be 30.0
        assertEquals(0, breakdown.get("progress").compareTo(new BigDecimal("30.0")));
    }

    @Test
    void progressOverhundredPercentCappedAtMax() {
        JiraIssueEntity story = createStoryWithSubtasks(100L * 3600, 150L * 3600);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        // Should be capped at 30.0
        assertEquals(0, breakdown.get("progress").compareTo(new BigDecimal("30.0")));
    }

    // ==================== Priority Factor Tests ====================

    @Test
    void priorityHighestGivesMaxScore() {
        JiraIssueEntity story = createBasicStory();
        story.setPriority("Highest");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("40"), breakdown.get("priority"));
    }

    @Test
    void priorityHighGives30Score() {
        JiraIssueEntity story = createBasicStory();
        story.setPriority("High");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("30"), breakdown.get("priority"));
    }

    @Test
    void priorityMediumGives20Score() {
        JiraIssueEntity story = createBasicStory();
        story.setPriority("Medium");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("20"), breakdown.get("priority"));
    }

    @Test
    void priorityLowGives10Score() {
        JiraIssueEntity story = createBasicStory();
        story.setPriority("Low");

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("10"), breakdown.get("priority"));
    }

    @Test
    void priorityNullGivesDefaultScore() {
        JiraIssueEntity story = createBasicStory();
        story.setPriority(null);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        // Default priority when not specified is 15 (between Low=10 and Medium=20)
        assertEquals(BigDecimal.valueOf(15), breakdown.get("priority"));
    }

    // ==================== Dependency Factor Tests ====================

    @Test
    void blocksOneStoryGives10Score() {
        JiraIssueEntity story = createBasicStory();
        story.setBlocks(List.of("PROJ-101"));

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("10"), breakdown.get("dependency"));
    }

    @Test
    void blocksTwoStoriesGives20Score() {
        JiraIssueEntity story = createBasicStory();
        story.setBlocks(List.of("PROJ-101", "PROJ-102"));

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("20"), breakdown.get("dependency"));
    }

    @Test
    void isBlockedByOneStoryGivesNegative1000Score() {
        JiraIssueEntity story = createBasicStory();
        story.setIsBlockedBy(List.of("PROJ-101"));

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("-1000"), breakdown.get("dependency"));
    }

    @Test
    void isBlockedByTwoStoriesGivesNegative2000Score() {
        JiraIssueEntity story = createBasicStory();
        story.setIsBlockedBy(List.of("PROJ-101", "PROJ-102"));

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("-2000"), breakdown.get("dependency"));
    }

    @Test
    void blocksAndBlockedByCombineScores() {
        JiraIssueEntity story = createBasicStory();
        story.setBlocks(List.of("PROJ-101")); // +10
        story.setIsBlockedBy(List.of("PROJ-102")); // -1000

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        // +10 - 1000 = -990
        assertEquals(new BigDecimal("-990"), breakdown.get("dependency"));
    }

    @Test
    void noDependenciesGivesZeroScore() {
        JiraIssueEntity story = createBasicStory();
        story.setBlocks(null);
        story.setIsBlockedBy(null);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(BigDecimal.ZERO, breakdown.get("dependency"));
    }

    // ==================== Due Date Factor Tests ====================

    @Test
    void dueDateOverdueGivesMaxScore() {
        JiraIssueEntity story = createBasicStory();
        story.setDueDate(LocalDate.now().minusDays(5));

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("40"), breakdown.get("dueDate"));
    }

    @Test
    void dueDateTomorrowGivesHighScore() {
        JiraIssueEntity story = createBasicStory();
        story.setDueDate(LocalDate.now().plusDays(1));

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        BigDecimal score = breakdown.get("dueDate");
        assertTrue(score.compareTo(new BigDecimal("30")) >= 0);
        assertTrue(score.compareTo(new BigDecimal("40")) <= 0);
    }

    @Test
    void dueDateInTwoWeeksGivesLowScore() {
        JiraIssueEntity story = createBasicStory();
        story.setDueDate(LocalDate.now().plusDays(14));

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        BigDecimal score = breakdown.get("dueDate");
        // 14 days = 10 (< 30 days but >= 14)
        assertEquals(BigDecimal.valueOf(10), score);
    }

    @Test
    void dueDateNullGivesZeroScore() {
        JiraIssueEntity story = createBasicStory();
        story.setDueDate(null);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(BigDecimal.ZERO, breakdown.get("dueDate"));
    }

    // ==================== Estimate Quality Factor Tests ====================

    @Test
    void noEstimatesGivesNegative100Score() {
        JiraIssueEntity story = createBasicStory();
        when(issueRepository.findByParentKey(story.getIssueKey())).thenReturn(List.of());

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("-100"), breakdown.get("estimateQuality"));
    }

    @Test
    void hasEstimatesGivesZeroScore() {
        JiraIssueEntity story = createStoryWithSubtasks(100L * 3600, 0L);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(BigDecimal.ZERO, breakdown.get("estimateQuality"));
    }

    // ==================== Flagged Factor Tests ====================

    @Test
    void flaggedGivesNegative200Score() {
        JiraIssueEntity story = createBasicStory();
        story.setFlagged(true);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(new BigDecimal("-200"), breakdown.get("flagged"));
    }

    @Test
    void notFlaggedGivesZeroScore() {
        JiraIssueEntity story = createBasicStory();
        story.setFlagged(false);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(BigDecimal.ZERO, breakdown.get("flagged"));
    }

    @Test
    void flaggedNullGivesZeroScore() {
        JiraIssueEntity story = createBasicStory();
        story.setFlagged(null);

        Map<String, BigDecimal> breakdown = service.calculateScoreBreakdown(story);

        assertEquals(BigDecimal.ZERO, breakdown.get("flagged"));
    }

    // ==================== Combined Score Tests ====================

    @Test
    void calculateAutoScoreReturnsSumOfFactors() {
        JiraIssueEntity story = createStoryWithSubtasks(100L * 3600, 50L * 3600);
        story.setIssueType("Bug"); // +100
        story.setStatus("Development"); // +50
        story.setPriority("Highest"); // +40

        BigDecimal score = service.calculateAutoScore(story);

        // Bug(100) + Status(50) + Progress(15) + Priority(40) = 205
        assertTrue(score.compareTo(new BigDecimal("200")) > 0);
    }

    @Test
    void bugScoresHigherThanStory() {
        JiraIssueEntity bug = createBasicStory();
        bug.setIssueType("Bug");
        bug.setStatus("New");

        JiraIssueEntity story = createBasicStory();
        story.setIssueType("Story");
        story.setStatus("New");

        BigDecimal bugScore = service.calculateAutoScore(bug);
        BigDecimal storyScore = service.calculateAutoScore(story);

        assertTrue(bugScore.compareTo(storyScore) > 0);
    }

    @Test
    void blockedStoryScoresVeryLow() {
        JiraIssueEntity blocked = createBasicStory();
        blocked.setIsBlockedBy(List.of("PROJ-101"));
        blocked.setPriority("Highest");

        JiraIssueEntity normal = createBasicStory();
        normal.setPriority("Low");

        BigDecimal blockedScore = service.calculateAutoScore(blocked);
        BigDecimal normalScore = service.calculateAutoScore(normal);

        // Blocked story should score much lower due to -1000 penalty
        assertTrue(blockedScore.compareTo(normalScore) < 0);
    }

    @Test
    void flaggedStoryScoresLowerThanNormal() {
        JiraIssueEntity flagged = createBasicStory();
        flagged.setFlagged(true);
        flagged.setPriority("High");

        JiraIssueEntity normal = createBasicStory();
        normal.setFlagged(false);
        normal.setPriority("Medium");

        BigDecimal flaggedScore = service.calculateAutoScore(flagged);
        BigDecimal normalScore = service.calculateAutoScore(normal);

        // Flagged penalty (-200) should push it below medium priority story
        assertTrue(flaggedScore.compareTo(normalScore) < 0);
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createBasicStory() {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-100");
        story.setIssueId("10100");
        story.setProjectKey("PROJ");
        story.setSummary("Test Story");
        story.setStatus("New");
        story.setIssueType("Story");
        story.setSubtask(false);
        story.setCreatedAt(OffsetDateTime.now().minusDays(7));
        story.setUpdatedAt(OffsetDateTime.now());

        // Mock empty subtasks by default
        when(issueRepository.findByParentKey(story.getIssueKey())).thenReturn(List.of());

        return story;
    }

    private JiraIssueEntity createStoryWithSubtasks(long totalEstimateSeconds, long totalLoggedSeconds) {
        JiraIssueEntity story = createBasicStory();

        // Create subtasks with estimates
        JiraIssueEntity subtask1 = new JiraIssueEntity();
        subtask1.setIssueKey("PROJ-100-1");
        subtask1.setSubtask(true);
        subtask1.setOriginalEstimateSeconds(totalEstimateSeconds / 2);
        subtask1.setTimeSpentSeconds(totalLoggedSeconds / 2);

        JiraIssueEntity subtask2 = new JiraIssueEntity();
        subtask2.setIssueKey("PROJ-100-2");
        subtask2.setSubtask(true);
        subtask2.setOriginalEstimateSeconds(totalEstimateSeconds / 2);
        subtask2.setTimeSpentSeconds(totalLoggedSeconds / 2);

        when(issueRepository.findByParentKey(story.getIssueKey())).thenReturn(List.of(subtask1, subtask2));

        return story;
    }
}
