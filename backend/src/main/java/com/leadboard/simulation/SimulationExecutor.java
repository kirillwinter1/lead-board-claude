package com.leadboard.simulation;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraTransition;
import com.leadboard.simulation.dto.SimulationAction;
import com.leadboard.status.StatusCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SimulationExecutor {

    private static final Logger log = LoggerFactory.getLogger(SimulationExecutor.class);
    private static final long RATE_LIMIT_DELAY_MS = 100;

    private final JiraClient jiraClient;
    private final WorkflowConfigService workflowConfigService;

    public SimulationExecutor(JiraClient jiraClient,
                              WorkflowConfigService workflowConfigService) {
        this.jiraClient = jiraClient;
        this.workflowConfigService = workflowConfigService;
    }

    /**
     * Executes planned actions against Jira API.
     * Each action is executed using the assigned user's OAuth token.
     */
    public List<SimulationAction> execute(List<SimulationAction> plannedActions, LocalDate simDate, Long teamId) {
        List<SimulationAction> results = new ArrayList<>();

        for (SimulationAction action : plannedActions) {
            try {
                SimulationAction result = executeAction(action, simDate);
                results.add(result);
                Thread.sleep(RATE_LIMIT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                results.add(action.withError("Interrupted"));
                break;
            } catch (Exception e) {
                log.error("Failed to execute action {} on {}: {}",
                        action.type(), action.issueKey(), e.getMessage());
                results.add(action.withError(e.getMessage()));
            }
        }

        return results;
    }

    private SimulationAction executeAction(SimulationAction action, LocalDate simDate) {
        if (action.type() == SimulationAction.ActionType.SKIP) {
            return action;
        }

        switch (action.type()) {
            case TRANSITION -> {
                return executeTransition(action);
            }
            case WORKLOG -> {
                return executeWorklog(action, simDate);
            }
            default -> {
                return action;
            }
        }
    }

    private SimulationAction executeTransition(SimulationAction action) {
        // Use Basic Auth (system API token) for reliable write access
        List<JiraTransition> transitions = jiraClient.getTransitionsBasicAuth(action.issueKey());

        JiraTransition target = findBestTransition(transitions, action);

        if (target == null) {
            String available = transitions.stream()
                    .map(t -> t.name() + (t.to() != null ? " → " + t.to().name() : ""))
                    .collect(Collectors.joining(", "));
            return action.withError("Transition to '" + action.toStatus()
                    + "' not found. Available: [" + available + "]");
        }

        jiraClient.transitionIssueBasicAuth(action.issueKey(), target.id());

        String actualTarget = target.to() != null ? target.to().name() : target.name();
        log.info("Simulation: transitioned {} from '{}' to '{}' (requested '{}')",
                action.issueKey(), action.fromStatus(), actualTarget, action.toStatus());
        return action.withExecuted();
    }

    /**
     * Finds the best matching transition using multi-level strategy:
     * 1. Exact name match (transition name or target status name)
     * 2. Category-based match (IN_PROGRESS, DONE categories via WorkflowConfigService)
     * 3. Forward fallback: if target is DONE but unavailable, pick any forward transition
     */
    private JiraTransition findBestTransition(List<JiraTransition> transitions,
                                               SimulationAction action) {
        String targetStatus = action.toStatus();

        // 1. Exact match by transition name or target status name
        for (JiraTransition t : transitions) {
            if (targetStatus.equalsIgnoreCase(t.name())
                    || (t.to() != null && targetStatus.equalsIgnoreCase(t.to().name()))) {
                return t;
            }
        }

        // 2. Category-based match
        StatusCategory targetCategory = resolveTargetCategory(targetStatus);
        if (targetCategory != null) {
            for (JiraTransition t : transitions) {
                if (t.to() != null) {
                    StatusCategory transCategory = workflowConfigService.categorize(
                            t.to().name(), action.issueType());
                    if (transCategory == targetCategory) {
                        return t;
                    }
                }
            }

            // 3. Forward fallback: if targeting DONE but no DONE transition available,
            //    pick the first transition that moves forward (not back to TODO)
            if (targetCategory == StatusCategory.DONE) {
                for (JiraTransition t : transitions) {
                    if (t.to() != null) {
                        StatusCategory transCategory = workflowConfigService.categorize(
                                t.to().name(), action.issueType());
                        if (transCategory == StatusCategory.IN_PROGRESS) {
                            log.info("Simulation: no DONE transition for {}, using forward transition '{}' → '{}'",
                                    action.issueKey(), t.name(), t.to().name());
                            return t;
                        }
                    }
                }
            }
        }

        return null;
    }

    private StatusCategory resolveTargetCategory(String targetStatus) {
        if (targetStatus == null) return null;
        String lower = targetStatus.toLowerCase();
        if (lower.contains("progress") || lower.contains("работ")) return StatusCategory.IN_PROGRESS;
        if (lower.contains("done") || lower.contains("готов") || lower.contains("closed")) return StatusCategory.DONE;
        return null;
    }

    private SimulationAction executeWorklog(SimulationAction action, LocalDate simDate) {
        int timeSpentSeconds = (int) (action.hoursLogged() * 3600);

        jiraClient.addWorklogBasicAuth(action.issueKey(), timeSpentSeconds, simDate);

        log.info("Simulation: logged {}h on {} for {}",
                action.hoursLogged(), action.issueKey(), action.assignee());
        return action.withExecuted();
    }

}
