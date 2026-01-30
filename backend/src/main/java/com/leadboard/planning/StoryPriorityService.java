package com.leadboard.planning;

import com.leadboard.planning.dto.RecalculateResponse;
import com.leadboard.planning.dto.StoriesResponse;
import com.leadboard.planning.dto.StoryWithScore;
import com.leadboard.status.StatusMappingConfig;
import com.leadboard.status.StatusMappingService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing story priority and AutoScore.
 */
@Service
public class StoryPriorityService {

    private static final Logger log = LoggerFactory.getLogger(StoryPriorityService.class);

    private final JiraIssueRepository issueRepository;
    private final StoryAutoScoreService autoScoreService;
    private final StoryDependencyService dependencyService;
    private final StatusMappingService statusMappingService;

    public StoryPriorityService(JiraIssueRepository issueRepository,
                                StoryAutoScoreService autoScoreService,
                                StoryDependencyService dependencyService,
                                StatusMappingService statusMappingService) {
        this.issueRepository = issueRepository;
        this.autoScoreService = autoScoreService;
        this.dependencyService = dependencyService;
        this.statusMappingService = statusMappingService;
    }

    /**
     * Get stories for an epic sorted by AutoScore.
     */
    public StoriesResponse getStoriesWithScore(String epicKey) {
        // Get all stories for this epic
        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epicKey).stream()
                .filter(issue -> !issue.isSubtask()) // Filter out subtasks
                .toList();

        if (stories.isEmpty()) {
            return new StoriesResponse(List.of(), new StoryDependencyService.DependencyGraph(List.of(), List.of()));
        }

        // Get team config (for now, use default - TODO: get from epic's team)
        StatusMappingConfig teamConfig = statusMappingService.getDefaultConfig();

        // Calculate AutoScore for each story
        Map<String, Double> storyScores = new HashMap<>();
        Map<String, Map<String, BigDecimal>> breakdowns = new HashMap<>();

        for (JiraIssueEntity story : stories) {
            BigDecimal score = autoScoreService.calculateAutoScore(story, teamConfig);
            Map<String, BigDecimal> breakdown = autoScoreService.calculateScoreBreakdown(story, teamConfig);

            storyScores.put(story.getIssueKey(), score.doubleValue());
            breakdowns.put(story.getIssueKey(), breakdown);
        }

        // Topological sort
        List<JiraIssueEntity> sorted = dependencyService.topologicalSort(stories, storyScores);

        // Build dependency graph
        StoryDependencyService.DependencyGraph graph = dependencyService.buildDependencyGraph(stories);

        // Get completed stories (for canStart check)
        Set<String> completedStories = stories.stream()
                .filter(s -> statusMappingService.isDone(s.getStatus(), teamConfig))
                .map(JiraIssueEntity::getIssueKey)
                .collect(Collectors.toSet());

        // Convert to DTOs
        List<StoryWithScore> storyDtos = sorted.stream()
                .map(story -> toStoryWithScore(story, storyScores, breakdowns, completedStories, teamConfig))
                .toList();

        return new StoriesResponse(storyDtos, graph);
    }

    /**
     * Recalculate AutoScore for all stories.
     */
    @Transactional
    public RecalculateResponse recalculateStories(String epicKey) {
        List<JiraIssueEntity> stories;

        if (epicKey != null && !epicKey.isEmpty()) {
            // Recalculate for specific epic
            stories = issueRepository.findByParentKey(epicKey).stream()
                    .filter(issue -> !issue.isSubtask())
                    .toList();
        } else {
            // Recalculate for all stories
            stories = issueRepository.findAll().stream()
                    .filter(issue -> !issue.isSubtask() && issue.getParentKey() != null)
                    .toList();
        }

        StatusMappingConfig teamConfig = statusMappingService.getDefaultConfig();
        int count = 0;

        for (JiraIssueEntity story : stories) {
            BigDecimal score = autoScoreService.calculateAutoScore(story, teamConfig);
            story.setAutoScore(score);
            story.setAutoScoreCalculatedAt(OffsetDateTime.now());
            issueRepository.save(story);
            count++;
        }

        log.info("Recalculated AutoScore for {} stories", count);

        return new RecalculateResponse(count, OffsetDateTime.now());
    }

    private StoryWithScore toStoryWithScore(
            JiraIssueEntity story,
            Map<String, Double> storyScores,
            Map<String, Map<String, BigDecimal>> breakdowns,
            Set<String> completedStories,
            StatusMappingConfig teamConfig
    ) {
        // Get subtasks
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());

        // Calculate totals from subtasks
        long totalEstimate = subtasks.stream()
                .mapToLong(st -> st.getOriginalEstimateSeconds() != null ? st.getOriginalEstimateSeconds() : 0)
                .sum();

        long totalSpent = subtasks.stream()
                .mapToLong(st -> st.getTimeSpentSeconds() != null ? st.getTimeSpentSeconds() : 0)
                .sum();

        double progress = totalEstimate > 0 ? (double) totalSpent / totalEstimate : 0.0;

        // Check if story can start
        boolean canStart = dependencyService.canStart(story, completedStories);

        // Convert subtasks to DTOs
        List<StoryWithScore.SubtaskInfo> subtaskDtos = subtasks.stream()
                .map(st -> new StoryWithScore.SubtaskInfo(
                        st.getIssueKey(),
                        st.getSummary(),
                        st.getIssueType(),
                        st.getStatus(),
                        null, // TODO: Add assignee when available
                        st.getOriginalEstimateSeconds(),
                        st.getTimeSpentSeconds()
                ))
                .toList();

        return new StoryWithScore(
                story.getIssueKey(),
                story.getSummary(),
                story.getIssueType(),
                story.getStatus(),
                story.getPriority(),
                story.getFlagged(),
                BigDecimal.valueOf(storyScores.getOrDefault(story.getIssueKey(), 0.0)),
                story.getIsBlockedBy(),
                story.getBlocks(),
                canStart,
                totalEstimate > 0 ? totalEstimate : null,
                totalSpent > 0 ? totalSpent : null,
                progress,
                breakdowns.get(story.getIssueKey()),
                subtaskDtos
        );
    }
}
