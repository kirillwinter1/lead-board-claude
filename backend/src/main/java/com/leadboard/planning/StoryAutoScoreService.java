package com.leadboard.planning;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for calculating AutoScore for Stories.
 *
 * AutoScore formula:
 * - IssueTypeWeight (Bug=100, Story=0)
 * - StatusWeight (New=0, Ready=10, Analysis=20, ..., Ready to Release=100, step 10)
 * - ProgressWeight (timeSpent/estimate * 30)
 * - PriorityWeight (Highest=40, High=30, Medium=20, Low=10)
 * - DependencyWeight (blocks N = +10*N, is blocked by = -1000)
 * - DueDateWeight (0-40 based on urgency)
 * - EstimateQualityPenalty (no subtasks = -100)
 * - FlaggedPenalty (flagged = -200)
 */
@Service
public class StoryAutoScoreService {

    private static final Logger log = LoggerFactory.getLogger(StoryAutoScoreService.class);

    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;

    public StoryAutoScoreService(JiraIssueRepository issueRepository,
                                  WorkflowConfigService workflowConfigService) {
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
    }

    /**
     * Calculate AutoScore for a story.
     */
    public BigDecimal calculateAutoScore(JiraIssueEntity story) {
        Map<String, BigDecimal> breakdown = calculateScoreBreakdown(story);

        // Sum all components
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : breakdown.values()) {
            total = total.add(value);
        }

        return total;
    }

    /**
     * Calculate AutoScore breakdown for UI tooltip.
     */
    public Map<String, BigDecimal> calculateScoreBreakdown(JiraIssueEntity story) {
        Map<String, BigDecimal> breakdown = new HashMap<>();

        // 1. Issue Type Weight (Bug=100, Story=0)
        breakdown.put("issueType", calculateIssueTypeWeight(story));

        // 2. Status Weight (by workflow, step 10)
        breakdown.put("status", calculateStatusWeight(story));

        // 3. Progress Weight (timeSpent/estimate * 30)
        breakdown.put("progress", calculateProgressWeight(story));

        // 4. Priority Weight (Highest=40, High=30, Medium=20, Low=10)
        breakdown.put("priority", calculatePriorityWeight(story));

        // 5. Dependency Weight (blocks N = +10*N, is blocked by = -1000)
        breakdown.put("dependency", calculateDependencyWeight(story));

        // 6. Due Date Weight (0-40 based on urgency)
        breakdown.put("dueDate", calculateDueDateWeight(story));

        // 7. Estimate Quality Penalty (no subtasks = -100)
        breakdown.put("estimateQuality", calculateEstimateQualityPenalty(story));

        // 8. Flagged Penalty (flagged = -200)
        breakdown.put("flagged", calculateFlaggedPenalty(story));

        return breakdown;
    }

    private BigDecimal calculateIssueTypeWeight(JiraIssueEntity story) {
        if (story.getIssueType() == null) {
            return BigDecimal.ZERO;
        }

        // Bug = 100 (higher priority than stories)
        if (workflowConfigService.isBug(story.getIssueType())) {
            return BigDecimal.valueOf(100);
        }

        // Story = 0 (base)
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateStatusWeight(JiraIssueEntity story) {
        if (story.getStatus() == null) {
            return BigDecimal.ZERO;
        }

        String status = story.getStatus();

        // Use WorkflowConfigService DB-driven score_weight
        int scoreWeight = workflowConfigService.getStoryStatusScoreWeight(status);
        if (scoreWeight > 0) {
            return BigDecimal.valueOf(scoreWeight);
        }

        // Fallback: hardcoded matching for unmapped statuses
        if (matchesStatus(status, "New", "Новое", "Новый")) return BigDecimal.ZERO;
        if (matchesStatus(status, "Ready", "Готово (к работе)", "Готов")) return BigDecimal.valueOf(10);
        if (matchesStatus(status, "Analysis", "Анализ")) return BigDecimal.valueOf(20);
        if (matchesStatus(status, "Analysis Review", "Ревью анализа")) return BigDecimal.valueOf(30);
        if (matchesStatus(status, "Waiting Dev", "Ожидает разработки")) return BigDecimal.valueOf(40);
        if (matchesStatus(status, "Development", "Разработка")) return BigDecimal.valueOf(50);
        if (matchesStatus(status, "Dev Review", "Ревью разработки")) return BigDecimal.valueOf(60);
        if (matchesStatus(status, "Waiting QA", "Ожидает тестирования")) return BigDecimal.valueOf(70);
        if (matchesStatus(status, "Testing", "Тестирование")) return BigDecimal.valueOf(80);
        if (matchesStatus(status, "Test Review", "Ревью тестирования")) return BigDecimal.valueOf(90);
        if (matchesStatus(status, "Ready to Release", "Готов к релизу")) return BigDecimal.valueOf(100);
        if (matchesStatus(status, "Done", "Готово")) return BigDecimal.ZERO;

        // Fallback: check using WorkflowConfigService
        if (workflowConfigService.isDone(status, story.getIssueType())) {
            return BigDecimal.ZERO;
        } else if (workflowConfigService.isInProgress(status, story.getIssueType())) {
            return BigDecimal.valueOf(50);
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal calculateProgressWeight(JiraIssueEntity story) {
        // Progress calculated from subtasks
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());

        if (subtasks.isEmpty()) {
            return BigDecimal.ZERO;
        }

        long totalEstimate = subtasks.stream()
                .mapToLong(st -> st.getOriginalEstimateSeconds() != null ? st.getOriginalEstimateSeconds() : 0)
                .sum();

        long totalSpent = subtasks.stream()
                .mapToLong(st -> st.getTimeSpentSeconds() != null ? st.getTimeSpentSeconds() : 0)
                .sum();

        if (totalEstimate == 0) {
            return BigDecimal.ZERO;
        }

        double progress = (double) totalSpent / totalEstimate;
        return BigDecimal.valueOf(Math.min(progress * 30, 30)); // Max 30
    }

    private BigDecimal calculatePriorityWeight(JiraIssueEntity story) {
        if (story.getPriority() == null) {
            return BigDecimal.valueOf(15); // Default
        }

        String priority = story.getPriority().toLowerCase();

        if (priority.contains("highest")) return BigDecimal.valueOf(40);
        if (priority.contains("high")) return BigDecimal.valueOf(30);
        if (priority.contains("medium")) return BigDecimal.valueOf(20);
        if (priority.contains("low")) return BigDecimal.valueOf(10);

        return BigDecimal.valueOf(15); // Default
    }

    private BigDecimal calculateDependencyWeight(JiraIssueEntity story) {
        BigDecimal weight = BigDecimal.ZERO;

        // Blocks N stories: +10 * N
        List<String> blocks = story.getBlocks();
        if (blocks != null && !blocks.isEmpty()) {
            weight = weight.add(BigDecimal.valueOf(blocks.size() * 10));
        }

        // Is blocked by M stories: -1000 * M (huge penalty - deferred)
        List<String> isBlockedBy = story.getIsBlockedBy();
        if (isBlockedBy != null && !isBlockedBy.isEmpty()) {
            weight = weight.subtract(BigDecimal.valueOf(isBlockedBy.size() * 1000));
        }

        return weight;
    }

    private BigDecimal calculateDueDateWeight(JiraIssueEntity story) {
        LocalDate dueDate = story.getDueDate();
        if (dueDate == null) {
            return BigDecimal.ZERO;
        }

        LocalDate today = LocalDate.now();
        long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);

        // Overdue: +40
        if (daysUntilDue < 0) {
            return BigDecimal.valueOf(40);
        }

        // < 7 days: +30
        if (daysUntilDue < 7) {
            return BigDecimal.valueOf(30);
        }

        // < 14 days: +20
        if (daysUntilDue < 14) {
            return BigDecimal.valueOf(20);
        }

        // < 30 days: +10
        if (daysUntilDue < 30) {
            return BigDecimal.valueOf(10);
        }

        // > 30 days: 0
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateEstimateQualityPenalty(JiraIssueEntity story) {
        // Story without subtasks with estimates: -100
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());

        if (subtasks.isEmpty()) {
            return BigDecimal.valueOf(-100);
        }

        boolean hasEstimates = subtasks.stream()
                .anyMatch(st -> st.getOriginalEstimateSeconds() != null && st.getOriginalEstimateSeconds() > 0);

        return hasEstimates ? BigDecimal.ZERO : BigDecimal.valueOf(-100);
    }

    private BigDecimal calculateFlaggedPenalty(JiraIssueEntity story) {
        // Flagged (work paused): -200
        if (story.getFlagged() != null && story.getFlagged()) {
            return BigDecimal.valueOf(-200);
        }

        return BigDecimal.ZERO;
    }

    private boolean matchesStatus(String status, String... patterns) {
        if (status == null) return false;
        String statusLower = status.toLowerCase();
        for (String pattern : patterns) {
            if (statusLower.equals(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // ==================== Batch Recalculation Methods ====================

    /**
     * Recalculate AutoScore for all stories.
     *
     * @return number of stories updated
     */
    public int recalculateAll() {
        int count = 0;

        List<JiraIssueEntity> stories = issueRepository.findByBoardCategory("STORY");
        List<JiraIssueEntity> bugs = issueRepository.findByBoardCategory("BUG");

        for (JiraIssueEntity story : stories) {
            BigDecimal score = calculateAutoScore(story);
            story.setAutoScore(score);
            story.setAutoScoreCalculatedAt(OffsetDateTime.now());
            issueRepository.save(story);
            count++;
        }

        for (JiraIssueEntity bug : bugs) {
            BigDecimal score = calculateAutoScore(bug);
            bug.setAutoScore(score);
            bug.setAutoScoreCalculatedAt(OffsetDateTime.now());
            issueRepository.save(bug);
            count++;
        }

        log.info("Recalculated AutoScore for {} stories/bugs", count);
        return count;
    }

    /**
     * Recalculate AutoScore for stories with given parent keys.
     * Used after sync to update only affected stories.
     *
     * @param parentKeys set of parent issue keys whose children need recalculation
     * @return number of stories updated
     */
    public int recalculateForParentKeys(Set<String> parentKeys) {
        if (parentKeys == null || parentKeys.isEmpty()) {
            return 0;
        }

        int count = 0;

        for (String parentKey : parentKeys) {
            List<JiraIssueEntity> stories = issueRepository.findByParentKey(parentKey);
            for (JiraIssueEntity story : stories) {
                // Only recalculate for story types, not subtasks
                if (isStoryType(story.getIssueType())) {
                    BigDecimal score = calculateAutoScore(story);
                    story.setAutoScore(score);
                    story.setAutoScoreCalculatedAt(OffsetDateTime.now());
                    issueRepository.save(story);
                    count++;
                }
            }
        }

        log.info("Recalculated AutoScore for {} stories (from {} parent keys)", count, parentKeys.size());
        return count;
    }

    /**
     * Recalculate AutoScore for stories whose subtasks changed.
     * Finds stories that are parents of the given subtask keys.
     *
     * @param subtaskKeys set of subtask keys that were changed
     * @return number of stories updated
     */
    public int recalculateForChangedSubtasks(Set<String> subtaskKeys) {
        if (subtaskKeys == null || subtaskKeys.isEmpty()) {
            return 0;
        }

        // Find parent stories for these subtasks
        Set<String> storyKeys = subtaskKeys.stream()
                .map(key -> issueRepository.findByIssueKey(key))
                .filter(opt -> opt.isPresent())
                .map(opt -> opt.get().getParentKey())
                .filter(parentKey -> parentKey != null)
                .collect(Collectors.toSet());

        if (storyKeys.isEmpty()) {
            return 0;
        }

        int count = 0;

        for (String storyKey : storyKeys) {
            issueRepository.findByIssueKey(storyKey).ifPresent(story -> {
                if (isStoryType(story.getIssueType())) {
                    BigDecimal score = calculateAutoScore(story);
                    story.setAutoScore(score);
                    story.setAutoScoreCalculatedAt(OffsetDateTime.now());
                    issueRepository.save(story);
                }
            });
            count++;
        }

        log.info("Recalculated AutoScore for {} stories (from {} changed subtasks)", count, subtaskKeys.size());
        return count;
    }

    private boolean isStoryType(String issueType) {
        if (issueType == null) return false;
        return workflowConfigService.isStoryOrBug(issueType);
    }
}
