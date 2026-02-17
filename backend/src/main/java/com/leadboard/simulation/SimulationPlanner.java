package com.leadboard.simulation;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.UnifiedPlanningService;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.*;
import com.leadboard.simulation.dto.SimulationAction;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
public class SimulationPlanner {

    private static final Logger log = LoggerFactory.getLogger(SimulationPlanner.class);

    private final UnifiedPlanningService planningService;
    private final JiraIssueRepository issueRepository;
    private final TeamMemberRepository memberRepository;
    private final WorkflowConfigService workflowConfigService;
    private final WorkCalendarService calendarService;
    private final SimulationDeviation deviation;

    public SimulationPlanner(
            UnifiedPlanningService planningService,
            JiraIssueRepository issueRepository,
            TeamMemberRepository memberRepository,
            WorkflowConfigService workflowConfigService,
            WorkCalendarService calendarService,
            SimulationDeviation deviation
    ) {
        this.planningService = planningService;
        this.issueRepository = issueRepository;
        this.memberRepository = memberRepository;
        this.workflowConfigService = workflowConfigService;
        this.calendarService = calendarService;
        this.deviation = deviation;
    }

    /**
     * Plans simulation actions for a given day.
     * Reads the unified plan, identifies active phases, and determines
     * what transitions/worklogs should happen today.
     */
    public List<SimulationAction> planDay(Long teamId, LocalDate today) {
        if (!calendarService.isWorkday(today)) {
            log.info("Simulation: {} is not a workday, skipping", today);
            return List.of();
        }

        // Get the unified plan
        UnifiedPlanningResult plan = planningService.calculatePlan(teamId);

        // Build member map: jiraAccountId → TeamMemberEntity
        Map<String, TeamMemberEntity> memberMap = new HashMap<>();
        for (TeamMemberEntity m : memberRepository.findByTeamIdAndActiveTrue(teamId)) {
            memberMap.put(m.getJiraAccountId(), m);
        }

        List<SimulationAction> actions = new ArrayList<>();

        for (PlannedEpic epic : plan.epics()) {
            for (PlannedStory story : epic.stories()) {
                // Process each phase dynamically
                for (Map.Entry<String, PhaseSchedule> entry : story.phases().entrySet()) {
                    processPhase(entry.getValue(), entry.getKey(), story, today, memberMap, actions);
                }
            }

            // Check for epic auto-transitions
            planEpicTransition(epic, actions);
        }

        // Check for story auto-transitions
        for (PlannedEpic epic : plan.epics()) {
            for (PlannedStory story : epic.stories()) {
                planStoryTransition(story, actions);
            }
        }

        log.info("Simulation planner: {} actions planned for team {} on {}",
                actions.size(), teamId, today);
        return actions;
    }

    /**
     * Processes a single phase, generating subtask actions if the phase is active today.
     */
    private void processPhase(
            PhaseSchedule phase,
            String phaseName,
            PlannedStory story,
            LocalDate today,
            Map<String, TeamMemberEntity> memberMap,
            List<SimulationAction> actions
    ) {
        if (phase == null || phase.startDate() == null || phase.endDate() == null) {
            return;
        }

        // Check if today falls within the phase schedule
        if (today.isBefore(phase.startDate()) || today.isAfter(phase.endDate())) {
            return;
        }

        String assigneeId = phase.assigneeAccountId();
        if (assigneeId == null) {
            return;
        }

        TeamMemberEntity member = memberMap.get(assigneeId);
        if (member == null) {
            return;
        }

        String assigneeName = phase.assigneeDisplayName() != null
                ? phase.assigneeDisplayName() : assigneeId;

        // Find subtasks for this story in this phase
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.storyKey());
        List<JiraIssueEntity> phaseSubtasks = subtasks.stream()
                .filter(st -> phaseName.equals(
                        workflowConfigService.getSubtaskRole(st.getIssueType())))
                .toList();

        if (phaseSubtasks.isEmpty()) {
            return;
        }

        for (JiraIssueEntity subtask : phaseSubtasks) {
            StatusCategory category = workflowConfigService.categorize(
                    subtask.getStatus(), subtask.getIssueType());

            switch (category) {
                case NEW -> {
                    // Assign to the planned assignee
                    actions.add(SimulationAction.assign(
                            subtask.getIssueKey(),
                            subtask.getIssueType(),
                            assigneeName,
                            assigneeId,
                            "Phase " + phaseName + " starting, assigning to " + assigneeName
                    ));
                    // Transition to "In Progress"
                    actions.add(SimulationAction.transition(
                            subtask.getIssueKey(),
                            subtask.getIssueType(),
                            assigneeName,
                            subtask.getStatus(),
                            "In Progress",
                            "Phase " + phaseName + " active, starting subtask"
                    ));
                    // Also log work in the same day
                    planWorklog(subtask, member, phaseName, assigneeName, actions);
                }
                case IN_PROGRESS -> {
                    planWorklog(subtask, member, phaseName, assigneeName, actions);
                }
                case DONE -> {
                    // Already done, skip
                }
            }
        }
    }

    /**
     * Plans story auto-transition based on subtask completion.
     * If all subtasks of a story are done, transition the story to Done.
     */
    private void planStoryTransition(
            PlannedStory story,
            List<SimulationAction> actions
    ) {
        if (workflowConfigService.isDone(story.status(), story.issueType())) {
            return;
        }

        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.storyKey());
        if (subtasks.isEmpty()) {
            return;
        }

        // Check if all subtasks are done (or will be done by planned actions)
        Set<String> willBeDone = new HashSet<>();
        for (SimulationAction action : actions) {
            if (action.type() == SimulationAction.ActionType.TRANSITION
                    && "Done".equals(action.toStatus())) {
                willBeDone.add(action.issueKey());
            }
        }

        boolean allDone = subtasks.stream().allMatch(st ->
                workflowConfigService.isDone(st.getStatus(), st.getIssueType())
                        || willBeDone.contains(st.getIssueKey()));

        if (allDone) {
            actions.add(SimulationAction.transition(
                    story.storyKey(),
                    story.issueType(),
                    null,
                    story.status(),
                    "Done",
                    "All subtasks completed"
            ));
        }
    }

    /**
     * Plans epic auto-transition based on story completion.
     */
    private void planEpicTransition(
            PlannedEpic epic,
            List<SimulationAction> actions
    ) {
        String epicTypeName = workflowConfigService.getEpicTypeNames().stream()
                .findFirst().orElse("Epic");
        if (workflowConfigService.isDone(epic.status(), epicTypeName)) {
            return;
        }

        if (epic.stories().isEmpty()) {
            return;
        }

        // Check if any stories are still active after planned actions
        Set<String> willBeDone = new HashSet<>();
        for (SimulationAction action : actions) {
            if (action.type() == SimulationAction.ActionType.TRANSITION
                    && "Done".equals(action.toStatus())) {
                willBeDone.add(action.issueKey());
            }
        }

        boolean allStoriesDone = epic.stories().stream().allMatch(st ->
                workflowConfigService.isDone(st.status(), st.issueType())
                        || willBeDone.contains(st.storyKey()));

        if (allStoriesDone) {
            actions.add(SimulationAction.transition(
                    epic.epicKey(),
                    epicTypeName,
                    null,
                    epic.status(),
                    "Done",
                    "All stories completed"
            ));
        }
    }

    private void planWorklog(
            JiraIssueEntity subtask,
            TeamMemberEntity member,
            String phaseName,
            String assigneeName,
            List<SimulationAction> actions
    ) {
        double hoursPerDay = member.getHoursPerDay() != null
                ? member.getHoursPerDay().doubleValue() : 6.0;
        double dailyHours = deviation.applyDailyDeviation(hoursPerDay);

        // Cap by remaining estimate
        long remainingSeconds = subtask.getRemainingEstimateSeconds() != null
                ? subtask.getRemainingEstimateSeconds() : 0;
        if (remainingSeconds <= 0) {
            long est = subtask.getOriginalEstimateSeconds() != null
                    ? subtask.getOriginalEstimateSeconds() : 0;
            long spent = subtask.getTimeSpentSeconds() != null
                    ? subtask.getTimeSpentSeconds() : 0;
            remainingSeconds = Math.max(0, est - spent);
        }

        double remainingHours = remainingSeconds / 3600.0;

        if (remainingHours <= 0) {
            // Work is done, transition to Done
            actions.add(SimulationAction.transition(
                    subtask.getIssueKey(),
                    subtask.getIssueType(),
                    assigneeName,
                    subtask.getStatus(),
                    "Done",
                    "Remaining estimate is 0, completing subtask"
            ));
        } else {
            double hoursToLog = Math.min(dailyHours, remainingHours);
            hoursToLog = roundToHalf(hoursToLog);
            if (hoursToLog < 0.5) hoursToLog = 0.5;

            actions.add(SimulationAction.worklog(
                    subtask.getIssueKey(),
                    subtask.getIssueType(),
                    assigneeName,
                    hoursToLog,
                    String.format("Phase %s: logging %.1fh (remaining: %.1fh)",
                            phaseName, hoursToLog, remainingHours)
            ));

            // If this log will finish the subtask, also add transition
            if (hoursToLog >= remainingHours - 0.25) {
                actions.add(SimulationAction.transition(
                        subtask.getIssueKey(),
                        subtask.getIssueType(),
                        assigneeName,
                        subtask.getStatus(),
                        "Done",
                        "Work completed after logging"
                ));
            }
        }
    }

    private double roundToHalf(double value) {
        return Math.round(value * 2.0) / 2.0;
    }
}
