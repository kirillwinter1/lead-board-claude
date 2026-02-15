package com.leadboard.planning;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.planning.dto.RetrospectiveResult;
import com.leadboard.planning.dto.RetrospectiveResult.*;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds retrospective timeline from status changelog data.
 * Shows how stories actually passed through workflow phases (e.g. SA -> DEV -> QA -> Done).
 */
@Service
public class RetrospectiveTimelineService {

    private static final Logger log = LoggerFactory.getLogger(RetrospectiveTimelineService.class);

    private final JiraIssueRepository issueRepository;
    private final StatusChangelogRepository changelogRepository;
    private final WorkflowConfigService workflowConfigService;

    public RetrospectiveTimelineService(
            JiraIssueRepository issueRepository,
            StatusChangelogRepository changelogRepository,
            WorkflowConfigService workflowConfigService
    ) {
        this.issueRepository = issueRepository;
        this.changelogRepository = changelogRepository;
        this.workflowConfigService = workflowConfigService;
    }

    public RetrospectiveResult calculateRetrospective(Long teamId) {
        // Load STORY-level issues for team
        List<JiraIssueEntity> stories = issueRepository.findByBoardCategoryAndTeamId("STORY", teamId);

        // Filter out stories that were never started (still in NEW/TODO status)
        List<JiraIssueEntity> startedStories = stories.stream()
                .filter(s -> {
                    StatusCategory cat = workflowConfigService.categorize(s.getStatus(), s.getIssueType());
                    return cat != StatusCategory.NEW && cat != StatusCategory.TODO;
                })
                .toList();

        if (startedStories.isEmpty()) {
            return new RetrospectiveResult(teamId, OffsetDateTime.now(), List.of());
        }

        // Batch fetch transitions for all stories
        List<String> storyKeys = startedStories.stream()
                .map(JiraIssueEntity::getIssueKey)
                .toList();

        List<StatusChangelogEntity> allTransitions =
                changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(storyKeys);

        // Group transitions by issue key
        Map<String, List<StatusChangelogEntity>> transitionsByKey = allTransitions.stream()
                .collect(Collectors.groupingBy(StatusChangelogEntity::getIssueKey));

        // Build RetroStory for each story
        Map<String, RetroStory> retroStoryMap = new LinkedHashMap<>();
        for (JiraIssueEntity story : startedStories) {
            List<StatusChangelogEntity> transitions = transitionsByKey.getOrDefault(
                    story.getIssueKey(), List.of());

            RetroStory retroStory = buildRetroStory(story, transitions);
            if (retroStory != null) {
                retroStoryMap.put(story.getIssueKey(), retroStory);
            }
        }

        // Group stories by parent epic
        Map<String, List<RetroStory>> storiesByEpic = new LinkedHashMap<>();
        Map<String, JiraIssueEntity> storyEntities = startedStories.stream()
                .collect(Collectors.toMap(JiraIssueEntity::getIssueKey, s -> s));

        for (Map.Entry<String, RetroStory> entry : retroStoryMap.entrySet()) {
            JiraIssueEntity storyEntity = storyEntities.get(entry.getKey());
            String parentKey = storyEntity.getParentKey();
            if (parentKey == null) parentKey = "NO_EPIC";
            storiesByEpic.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(entry.getValue());
        }

        // Build RetroEpic for each group
        List<RetroEpic> retroEpics = new ArrayList<>();
        for (Map.Entry<String, List<RetroStory>> entry : storiesByEpic.entrySet()) {
            String epicKey = entry.getKey();
            List<RetroStory> epicStories = entry.getValue();

            // Sort stories by start date
            epicStories.sort(Comparator.comparing(
                    s -> s.startDate() != null ? s.startDate() : LocalDate.MAX));

            JiraIssueEntity epicEntity = null;
            if (!"NO_EPIC".equals(epicKey)) {
                epicEntity = issueRepository.findByIssueKey(epicKey).orElse(null);
            }

            // Calculate epic date range from stories
            LocalDate epicStart = epicStories.stream()
                    .map(RetroStory::startDate)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);

            LocalDate epicEnd = epicStories.stream()
                    .map(s -> s.endDate() != null ? s.endDate() : LocalDate.now())
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            // Calculate epic progress
            long completed = epicStories.stream().filter(RetroStory::completed).count();
            int progressPercent = epicStories.isEmpty() ? 0 :
                    (int) (completed * 100 / epicStories.size());

            retroEpics.add(new RetroEpic(
                    epicKey,
                    epicEntity != null ? epicEntity.getSummary() : epicKey,
                    epicEntity != null ? epicEntity.getStatus() : null,
                    epicStart,
                    epicEnd,
                    progressPercent,
                    epicStories
            ));
        }

        // Sort epics by start date
        retroEpics.sort(Comparator.comparing(
                e -> e.startDate() != null ? e.startDate() : LocalDate.MAX));

        return new RetrospectiveResult(teamId, OffsetDateTime.now(), retroEpics);
    }

    RetroStory buildRetroStory(JiraIssueEntity story, List<StatusChangelogEntity> transitions) {
        if (transitions.isEmpty()) {
            return null;
        }

        Map<String, LocalDate> phaseFirstStart = new LinkedHashMap<>();
        Map<String, LocalDate> phaseLastEnd = new LinkedHashMap<>();
        Map<String, Boolean> phaseActive = new LinkedHashMap<>();

        String currentRole = null;

        for (StatusChangelogEntity transition : transitions) {
            String toStatus = transition.getToStatus();
            LocalDate transitionDate = transition.getTransitionedAt().toLocalDate();

            StatusCategory category = workflowConfigService.categorize(toStatus, story.getIssueType());
            String roleCode = workflowConfigService.determinePhase(toStatus, null);

            if (category == StatusCategory.NEW || category == StatusCategory.TODO) {
                // Story returned to backlog — close current phase
                if (currentRole != null) {
                    phaseLastEnd.put(currentRole, transitionDate);
                    phaseActive.put(currentRole, false);
                    currentRole = null;
                }
            } else if (category == StatusCategory.DONE) {
                // Story completed — close current phase
                if (currentRole != null) {
                    phaseLastEnd.put(currentRole, transitionDate);
                    phaseActive.put(currentRole, false);
                    currentRole = null;
                }
            } else {
                // IN_PROGRESS / PLANNED — story is in a phase
                if (currentRole != null && !currentRole.equals(roleCode)) {
                    // Role changed — close old phase, open new one
                    phaseLastEnd.put(currentRole, transitionDate);
                    phaseActive.put(currentRole, false);
                }

                if (!roleCode.equals(currentRole)) {
                    // Start or resume this role's phase
                    phaseFirstStart.putIfAbsent(roleCode, transitionDate);
                    phaseActive.put(roleCode, true);
                    currentRole = roleCode;
                }
            }
        }

        // If story is still in progress, mark current phase as active
        boolean storyDone = workflowConfigService.isDone(story.getStatus(), story.getIssueType());
        if (currentRole != null && !storyDone) {
            phaseActive.put(currentRole, true);
            // Don't set endDate — frontend will use "today"
        }

        // Build phase map
        Map<String, RetroPhase> phases = new LinkedHashMap<>();
        for (String role : phaseFirstStart.keySet()) {
            LocalDate start = phaseFirstStart.get(role);
            LocalDate end = phaseLastEnd.get(role);
            boolean active = phaseActive.getOrDefault(role, false);

            long duration;
            if (end != null) {
                duration = java.time.temporal.ChronoUnit.DAYS.between(start, end);
            } else if (active) {
                duration = java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.now());
            } else {
                duration = 0;
            }

            phases.put(role, new RetroPhase(role, start, end, duration, active));
        }

        if (phases.isEmpty()) {
            return null;
        }

        // Story-level dates
        LocalDate storyStart = phaseFirstStart.values().stream()
                .min(Comparator.naturalOrder())
                .orElse(null);

        LocalDate storyEnd;
        if (storyDone) {
            storyEnd = phaseLastEnd.values().stream()
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(LocalDate.now());
        } else {
            storyEnd = null; // active — frontend will show until today
        }

        // Progress = percentage of phases completed (non-active)
        long completedPhases = phases.values().stream()
                .filter(p -> !p.active() && p.endDate() != null)
                .count();
        int progressPercent = phases.isEmpty() ? 0 :
                (int) (completedPhases * 100 / phases.size());
        if (storyDone) progressPercent = 100;

        return new RetroStory(
                story.getIssueKey(),
                story.getSummary(),
                story.getStatus(),
                storyDone,
                storyStart,
                storyEnd,
                progressPercent,
                phases
        );
    }
}
