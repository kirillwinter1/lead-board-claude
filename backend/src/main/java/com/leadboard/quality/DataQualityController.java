package com.leadboard.quality;

import com.leadboard.config.JiraProperties;
import com.leadboard.quality.dto.DataQualityResponse;
import com.leadboard.quality.dto.IssueViolations;
import com.leadboard.status.StatusMappingConfig;
import com.leadboard.status.StatusMappingService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for data quality reports.
 */
@RestController
@RequestMapping("/api/data-quality")
public class DataQualityController {

    private final JiraIssueRepository issueRepository;
    private final JiraProperties jiraProperties;
    private final DataQualityService dataQualityService;
    private final StatusMappingService statusMappingService;

    public DataQualityController(
            JiraIssueRepository issueRepository,
            JiraProperties jiraProperties,
            DataQualityService dataQualityService,
            StatusMappingService statusMappingService
    ) {
        this.issueRepository = issueRepository;
        this.jiraProperties = jiraProperties;
        this.dataQualityService = dataQualityService;
        this.statusMappingService = statusMappingService;
    }

    /**
     * Get data quality report for a team or all teams.
     */
    @GetMapping
    public ResponseEntity<DataQualityResponse> getReport(
            @RequestParam(required = false) Long teamId
    ) {
        String projectKey = jiraProperties.getProjectKey();
        String baseUrl = jiraProperties.getBaseUrl();

        if (projectKey == null || projectKey.isEmpty()) {
            return ResponseEntity.ok(emptyResponse(teamId));
        }

        StatusMappingConfig statusMapping = statusMappingService.getDefaultConfig();

        // Load all issues
        List<JiraIssueEntity> allIssues = issueRepository.findByProjectKey(projectKey);

        // Build maps for quick lookup
        Map<String, JiraIssueEntity> issueMap = allIssues.stream()
                .collect(Collectors.toMap(JiraIssueEntity::getIssueKey, e -> e));

        // Separate by type
        List<JiraIssueEntity> epics = allIssues.stream()
                .filter(e -> isEpic(e.getIssueType()))
                .filter(e -> teamId == null || Objects.equals(e.getTeamId(), teamId))
                .toList();

        List<JiraIssueEntity> storiesAndBugs = allIssues.stream()
                .filter(e -> isStoryOrBug(e.getIssueType()))
                .toList();

        List<JiraIssueEntity> subtasks = allIssues.stream()
                .filter(JiraIssueEntity::isSubtask)
                .toList();

        // Build parent-children relationships
        Map<String, List<JiraIssueEntity>> childrenByParent = storiesAndBugs.stream()
                .filter(e -> e.getParentKey() != null)
                .collect(Collectors.groupingBy(JiraIssueEntity::getParentKey));

        Map<String, List<JiraIssueEntity>> subtasksByParent = subtasks.stream()
                .filter(e -> e.getParentKey() != null)
                .collect(Collectors.groupingBy(JiraIssueEntity::getParentKey));

        // Collect all violations
        List<IssueViolations> allViolations = new ArrayList<>();
        Map<String, Integer> byRule = new HashMap<>();
        Map<String, Integer> bySeverity = new HashMap<>();

        // Check epics
        for (JiraIssueEntity epic : epics) {
            List<JiraIssueEntity> children = childrenByParent.getOrDefault(epic.getIssueKey(), List.of());
            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, children, statusMapping);

            if (!violations.isEmpty()) {
                allViolations.add(toIssueViolations(epic, baseUrl, violations));
                countViolations(violations, byRule, bySeverity);
            }

            // Check children of this epic
            for (JiraIssueEntity child : children) {
                List<JiraIssueEntity> childSubtasks = subtasksByParent.getOrDefault(child.getIssueKey(), List.of());
                List<DataQualityViolation> childViolations = dataQualityService.checkStory(child, epic, childSubtasks, statusMapping);

                if (!childViolations.isEmpty()) {
                    allViolations.add(toIssueViolations(child, baseUrl, childViolations));
                    countViolations(childViolations, byRule, bySeverity);
                }

                // Check subtasks
                for (JiraIssueEntity subtask : childSubtasks) {
                    List<DataQualityViolation> subtaskViolations = dataQualityService.checkSubtask(subtask, child, epic, statusMapping);

                    if (!subtaskViolations.isEmpty()) {
                        allViolations.add(toIssueViolations(subtask, baseUrl, subtaskViolations));
                        countViolations(subtaskViolations, byRule, bySeverity);
                    }
                }
            }
        }

        // Sort violations by severity (errors first, then warnings, then info)
        allViolations.sort((a, b) -> {
            int severityA = getSeverityOrder(a);
            int severityB = getSeverityOrder(b);
            return Integer.compare(severityA, severityB);
        });

        // Calculate summary
        int issuesWithErrors = (int) allViolations.stream().filter(IssueViolations::hasErrors).count();
        int issuesWithWarnings = (int) allViolations.stream().filter(v -> !v.hasErrors() && v.hasWarnings()).count();
        int issuesWithInfo = allViolations.size() - issuesWithErrors - issuesWithWarnings;

        DataQualityResponse.Summary summary = new DataQualityResponse.Summary(
                allViolations.size(),
                issuesWithErrors,
                issuesWithWarnings,
                issuesWithInfo,
                byRule,
                bySeverity
        );

        return ResponseEntity.ok(new DataQualityResponse(
                OffsetDateTime.now(),
                teamId,
                summary,
                allViolations
        ));
    }

    private DataQualityResponse emptyResponse(Long teamId) {
        return new DataQualityResponse(
                OffsetDateTime.now(),
                teamId,
                new DataQualityResponse.Summary(0, 0, 0, 0, Map.of(), Map.of()),
                List.of()
        );
    }

    private IssueViolations toIssueViolations(JiraIssueEntity issue, String baseUrl, List<DataQualityViolation> violations) {
        String jiraUrl = baseUrl + "/browse/" + issue.getIssueKey();
        List<IssueViolations.ViolationDto> dtos = violations.stream()
                .map(IssueViolations.ViolationDto::from)
                .toList();

        return new IssueViolations(
                issue.getIssueKey(),
                issue.getIssueType(),
                issue.getSummary(),
                issue.getStatus(),
                jiraUrl,
                dtos
        );
    }

    private void countViolations(List<DataQualityViolation> violations, Map<String, Integer> byRule, Map<String, Integer> bySeverity) {
        for (DataQualityViolation v : violations) {
            byRule.merge(v.rule().name(), 1, Integer::sum);
            bySeverity.merge(v.severity().name(), 1, Integer::sum);
        }
    }

    private int getSeverityOrder(IssueViolations issue) {
        if (issue.hasErrors()) return 0;
        if (issue.hasWarnings()) return 1;
        return 2;
    }

    private boolean isEpic(String issueType) {
        return "Epic".equalsIgnoreCase(issueType) || "Эпик".equalsIgnoreCase(issueType);
    }

    private boolean isStoryOrBug(String issueType) {
        String lower = issueType.toLowerCase();
        return lower.contains("story") || lower.contains("история")
                || lower.contains("bug") || lower.contains("баг");
    }
}
