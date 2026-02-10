package com.leadboard.simulation;

import com.leadboard.auth.OAuthService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraTransition;
import com.leadboard.simulation.dto.SimulationAction;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SimulationExecutor {

    private static final Logger log = LoggerFactory.getLogger(SimulationExecutor.class);
    private static final long RATE_LIMIT_DELAY_MS = 100;

    private final JiraClient jiraClient;
    private final OAuthService oauthService;
    private final TeamMemberRepository memberRepository;

    public SimulationExecutor(JiraClient jiraClient, OAuthService oauthService,
                              TeamMemberRepository memberRepository) {
        this.jiraClient = jiraClient;
        this.oauthService = oauthService;
        this.memberRepository = memberRepository;
    }

    /**
     * Executes planned actions against Jira API.
     * Each action is executed using the assigned user's OAuth token.
     */
    public List<SimulationAction> execute(List<SimulationAction> plannedActions, LocalDate simDate, Long teamId) {
        // Build map: displayName → jiraAccountId
        Map<String, String> nameToAccountId = memberRepository.findByTeamIdAndActiveTrue(teamId).stream()
                .collect(Collectors.toMap(
                        TeamMemberEntity::getDisplayName,
                        TeamMemberEntity::getJiraAccountId,
                        (a, b) -> a  // keep first on duplicate
                ));

        List<SimulationAction> results = new ArrayList<>();

        for (SimulationAction action : plannedActions) {
            try {
                SimulationAction result = executeAction(action, simDate, nameToAccountId);
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

    private SimulationAction executeAction(SimulationAction action, LocalDate simDate,
                                           Map<String, String> nameToAccountId) {
        if (action.type() == SimulationAction.ActionType.SKIP) {
            return action;
        }

        // Resolve account ID from assignee display name
        String accountId = resolveAccountId(action.assignee(), nameToAccountId);
        if (accountId == null) {
            // For epic/story transitions without assignee, use any available token
            if (action.assignee() == null) {
                accountId = nameToAccountId.values().stream().findFirst().orElse(null);
            }
            if (accountId == null) {
                return action.withError("No OAuth token: cannot resolve user '" + action.assignee() + "'");
            }
        }

        OAuthService.TokenInfo tokenInfo = oauthService.getValidAccessTokenForUser(accountId);
        if (tokenInfo == null) {
            return action.withError("No OAuth token for user: " + action.assignee());
        }

        switch (action.type()) {
            case TRANSITION -> {
                return executeTransition(action, tokenInfo);
            }
            case WORKLOG -> {
                return executeWorklog(action, simDate, tokenInfo);
            }
            default -> {
                return action;
            }
        }
    }

    private SimulationAction executeTransition(SimulationAction action, OAuthService.TokenInfo tokenInfo) {
        // Find the target transition
        List<JiraTransition> transitions = jiraClient.getTransitions(
                action.issueKey(), tokenInfo.accessToken(), tokenInfo.cloudId());

        JiraTransition target = transitions.stream()
                .filter(t -> {
                    String targetStatus = action.toStatus();
                    return targetStatus.equalsIgnoreCase(t.name())
                            || (t.to() != null && targetStatus.equalsIgnoreCase(t.to().name()));
                })
                .findFirst()
                .orElse(null);

        if (target == null) {
            String available = transitions.stream()
                    .map(t -> t.name() + (t.to() != null ? " → " + t.to().name() : ""))
                    .collect(Collectors.joining(", "));
            return action.withError("Transition to '" + action.toStatus()
                    + "' not found. Available: [" + available + "]");
        }

        jiraClient.transitionIssue(action.issueKey(), target.id(),
                tokenInfo.accessToken(), tokenInfo.cloudId());

        log.info("Simulation: transitioned {} from '{}' to '{}'",
                action.issueKey(), action.fromStatus(), action.toStatus());
        return action.withExecuted();
    }

    private SimulationAction executeWorklog(SimulationAction action, LocalDate simDate,
                                            OAuthService.TokenInfo tokenInfo) {
        int timeSpentSeconds = (int) (action.hoursLogged() * 3600);

        jiraClient.addWorklog(action.issueKey(), timeSpentSeconds, simDate,
                tokenInfo.accessToken(), tokenInfo.cloudId());

        log.info("Simulation: logged {}h on {} for {}",
                action.hoursLogged(), action.issueKey(), action.assignee());
        return action.withExecuted();
    }

    private String resolveAccountId(String displayName, Map<String, String> nameToAccountId) {
        if (displayName == null) return null;
        // Exact match
        String accountId = nameToAccountId.get(displayName);
        if (accountId != null) return accountId;

        // Case-insensitive match
        for (Map.Entry<String, String> entry : nameToAccountId.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(displayName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
