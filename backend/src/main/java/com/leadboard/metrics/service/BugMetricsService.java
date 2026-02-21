package com.leadboard.metrics.service;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.dto.BugMetricsResponse;
import com.leadboard.metrics.dto.BugMetricsResponse.OpenBugDto;
import com.leadboard.metrics.dto.BugMetricsResponse.PriorityMetrics;
import com.leadboard.quality.BugSlaService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BugMetricsService {

    private static final List<String> PRIORITY_ORDER = List.of(
            "Blocker", "Critical", "Highest", "High", "Medium", "Low", "Lowest"
    );

    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;
    private final BugSlaService bugSlaService;
    private final JiraProperties jiraProperties;

    public BugMetricsService(JiraIssueRepository issueRepository,
                             WorkflowConfigService workflowConfigService,
                             BugSlaService bugSlaService,
                             JiraProperties jiraProperties) {
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
        this.bugSlaService = bugSlaService;
        this.jiraProperties = jiraProperties;
    }

    public BugMetricsResponse getBugMetrics(Long teamId) {
        List<JiraIssueEntity> allBugs = teamId != null
                ? issueRepository.findByBoardCategoryAndTeamId("BUG", teamId)
                : issueRepository.findByBoardCategory("BUG");

        List<JiraIssueEntity> openBugs = new ArrayList<>();
        List<JiraIssueEntity> resolvedBugs = new ArrayList<>();

        for (JiraIssueEntity bug : allBugs) {
            if (workflowConfigService.isDone(bug.getStatus(), bug.getIssueType())) {
                resolvedBugs.add(bug);
            } else {
                openBugs.add(bug);
            }
        }

        int staleBugs = (int) openBugs.stream().filter(bugSlaService::checkStale).count();

        long avgResolutionHours = calculateAvgResolutionHours(resolvedBugs);

        double slaCompliancePercent = calculateSlaCompliance(resolvedBugs);

        List<PriorityMetrics> byPriority = calculateByPriority(openBugs, resolvedBugs);

        List<OpenBugDto> openBugList = buildOpenBugList(openBugs);

        return new BugMetricsResponse(
                openBugs.size(),
                resolvedBugs.size(),
                staleBugs,
                avgResolutionHours,
                slaCompliancePercent,
                byPriority,
                openBugList
        );
    }

    private long calculateAvgResolutionHours(List<JiraIssueEntity> resolved) {
        if (resolved.isEmpty()) return 0;
        long totalHours = resolved.stream()
                .mapToLong(bugSlaService::getResolutionTimeHours)
                .sum();
        return totalHours / resolved.size();
    }

    private double calculateSlaCompliance(List<JiraIssueEntity> resolved) {
        if (resolved.isEmpty()) return 100.0;
        long withinSla = resolved.stream()
                .filter(bug -> !bugSlaService.checkSlaBreach(bug))
                .count();
        return Math.round(withinSla * 1000.0 / resolved.size()) / 10.0;
    }

    private List<PriorityMetrics> calculateByPriority(
            List<JiraIssueEntity> openBugs,
            List<JiraIssueEntity> resolvedBugs) {

        Set<String> allPriorities = new LinkedHashSet<>();
        openBugs.forEach(b -> { if (b.getPriority() != null) allPriorities.add(b.getPriority()); });
        resolvedBugs.forEach(b -> { if (b.getPriority() != null) allPriorities.add(b.getPriority()); });

        Map<String, List<JiraIssueEntity>> openByPriority = openBugs.stream()
                .filter(b -> b.getPriority() != null)
                .collect(Collectors.groupingBy(JiraIssueEntity::getPriority));

        Map<String, List<JiraIssueEntity>> resolvedByPriority = resolvedBugs.stream()
                .filter(b -> b.getPriority() != null)
                .collect(Collectors.groupingBy(JiraIssueEntity::getPriority));

        List<PriorityMetrics> result = new ArrayList<>();
        for (String priority : allPriorities) {
            List<JiraIssueEntity> open = openByPriority.getOrDefault(priority, List.of());
            List<JiraIssueEntity> resolved = resolvedByPriority.getOrDefault(priority, List.of());

            long avgHours = calculateAvgResolutionHours(resolved);
            Integer slaLimit = bugSlaService.getSlaForPriority(priority).orElse(null);
            double compliance = calculateSlaCompliance(resolved);

            result.add(new PriorityMetrics(
                    priority,
                    open.size(),
                    resolved.size(),
                    avgHours,
                    slaLimit,
                    compliance
            ));
        }

        result.sort((a, b) -> {
            int ai = PRIORITY_ORDER.indexOf(a.priority());
            int bi = PRIORITY_ORDER.indexOf(b.priority());
            if (ai == -1) ai = PRIORITY_ORDER.size();
            if (bi == -1) bi = PRIORITY_ORDER.size();
            return Integer.compare(ai, bi);
        });

        return result;
    }

    private List<OpenBugDto> buildOpenBugList(List<JiraIssueEntity> openBugs) {
        String baseUrl = jiraProperties.getBaseUrl();
        OffsetDateTime now = OffsetDateTime.now();

        List<OpenBugDto> list = openBugs.stream()
                .map(bug -> {
                    long ageHours = bug.getJiraCreatedAt() != null
                            ? ChronoUnit.HOURS.between(bug.getJiraCreatedAt(), now)
                            : 0;
                    long ageDays = ageHours / 24;
                    boolean slaBreach = bugSlaService.checkSlaBreach(bug);
                    String jiraUrl = baseUrl + "/browse/" + bug.getIssueKey();

                    return new OpenBugDto(
                            bug.getIssueKey(),
                            bug.getSummary(),
                            bug.getPriority(),
                            bug.getStatus(),
                            ageDays,
                            ageHours,
                            slaBreach,
                            jiraUrl
                    );
                })
                .collect(Collectors.toList());

        list.sort((a, b) -> {
            int ai = PRIORITY_ORDER.indexOf(a.priority());
            int bi = PRIORITY_ORDER.indexOf(b.priority());
            if (ai == -1) ai = PRIORITY_ORDER.size();
            if (bi == -1) bi = PRIORITY_ORDER.size();
            if (ai != bi) return Integer.compare(ai, bi);
            return Long.compare(b.ageHours(), a.ageHours());
        });

        return list;
    }
}
