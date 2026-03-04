package com.leadboard.planning;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.planning.dto.RetrospectiveResult;
import com.leadboard.planning.dto.RetrospectiveResult.*;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Date;
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
    private final IssueWorklogRepository worklogRepository;
    private final WorkflowConfigService workflowConfigService;

    public RetrospectiveTimelineService(
            JiraIssueRepository issueRepository,
            StatusChangelogRepository changelogRepository,
            IssueWorklogRepository worklogRepository,
            WorkflowConfigService workflowConfigService
    ) {
        this.issueRepository = issueRepository;
        this.changelogRepository = changelogRepository;
        this.worklogRepository = worklogRepository;
        this.workflowConfigService = workflowConfigService;
    }

    public RetrospectiveResult calculateRetrospective(Long teamId) {
        // Load STORY and BUG level issues for team
        List<JiraIssueEntity> stories = issueRepository.findByBoardCategoryInAndTeamId(List.of("STORY", "BUG"), teamId);

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

        // Batch-load worklogs: find subtasks for all stories, then aggregate worklogs by parent story
        Map<String, List<WorklogDay>> worklogsByStory = loadWorklogsByStory(storyKeys);

        // Build RetroStory for each story
        Map<String, RetroStory> retroStoryMap = new LinkedHashMap<>();
        for (JiraIssueEntity story : startedStories) {
            List<StatusChangelogEntity> transitions = transitionsByKey.getOrDefault(
                    story.getIssueKey(), List.of());

            RetroStory retroStory = buildRetroStory(story, transitions,
                    worklogsByStory.getOrDefault(story.getIssueKey(), List.of()));
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
            if (parentKey == null) continue; // Skip orphan stories without epic
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

            JiraIssueEntity epicEntity = issueRepository.findByIssueKey(epicKey).orElse(null);

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

    RetroStory buildRetroStory(JiraIssueEntity story, List<StatusChangelogEntity> transitions,
                               List<WorklogDay> worklogDays) {
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
                phases,
                worklogDays != null && !worklogDays.isEmpty() ? worklogDays : null
        );
    }

    /**
     * Load worklogs for stories by finding their subtasks and aggregating.
     * Worklogs are logged on subtasks, so we need to:
     * 1. Find all subtasks for the given story keys
     * 2. Load worklogs for those subtask keys
     * 3. Aggregate by parent story -> date -> role
     */
    private Map<String, List<WorklogDay>> loadWorklogsByStory(List<String> storyKeys) {
        if (storyKeys.isEmpty()) return Map.of();

        // Find subtasks for these stories
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKeyIn(storyKeys);
        if (subtasks.isEmpty()) return Map.of();

        // Build subtask key -> parent story key mapping
        Map<String, String> subtaskToParent = new HashMap<>();
        List<String> subtaskKeys = new ArrayList<>();
        for (JiraIssueEntity subtask : subtasks) {
            subtaskToParent.put(subtask.getIssueKey(), subtask.getParentKey());
            subtaskKeys.add(subtask.getIssueKey());
        }

        // Load aggregated worklogs for all subtask keys
        List<Object[]> rawWorklogs = worklogRepository.findAggregatedWorklogsByIssueKeys(subtaskKeys);
        if (rawWorklogs.isEmpty()) return Map.of();

        // Aggregate by parent story
        Map<String, Map<String, Map<String, Long>>> storyDateRole = new HashMap<>();
        // storyKey -> date -> roleCode -> totalSeconds

        for (Object[] row : rawWorklogs) {
            String issueKey = (String) row[0];
            LocalDate startedDate = row[1] instanceof Date
                    ? ((Date) row[1]).toLocalDate()
                    : (LocalDate) row[1];
            String roleCode = (String) row[2];
            long totalSeconds = ((Number) row[3]).longValue();

            String parentKey = subtaskToParent.get(issueKey);
            if (parentKey == null) continue;

            storyDateRole
                    .computeIfAbsent(parentKey, k -> new TreeMap<>())
                    .computeIfAbsent(startedDate.toString(), k -> new HashMap<>())
                    .merge(roleCode, totalSeconds, Long::sum);
        }

        // Convert to WorklogDay lists
        Map<String, List<WorklogDay>> result = new HashMap<>();
        for (var storyEntry : storyDateRole.entrySet()) {
            List<WorklogDay> days = new ArrayList<>();
            for (var dateEntry : storyEntry.getValue().entrySet()) {
                LocalDate date = LocalDate.parse(dateEntry.getKey());
                for (var roleEntry : dateEntry.getValue().entrySet()) {
                    days.add(new WorklogDay(date, roleEntry.getKey(), roleEntry.getValue()));
                }
            }
            result.put(storyEntry.getKey(), days);
        }

        return result;
    }
}
