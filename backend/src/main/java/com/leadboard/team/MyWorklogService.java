package com.leadboard.team;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraWriteService;
import com.leadboard.metrics.entity.IssueWorklogEntity;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Orchestrates time logging from My Work (F89): validates the request, writes to Jira
 * from the acting user's own OAuth identity via {@link JiraWriteService}, then upserts
 * the local worklog cache so the board/analytics reflect the new entry immediately
 * (a full re-sync will later reconcile with Jira, which remains the source of truth).
 */
@Service
public class MyWorklogService {

    /** Request fails basic validation (bad hours/date/issue) — controller maps to 400. */
    public static class LogTimeValidationException extends RuntimeException {
        public LogTimeValidationException(String m) {
            super(m);
        }
    }

    /** User tried to log time on a task not assigned to them — controller maps to 403. */
    public static class LogTimeForbiddenException extends RuntimeException {
        public LogTimeForbiddenException(String m) {
            super(m);
        }
    }

    private final JiraIssueRepository issueRepository;
    private final IssueWorklogRepository worklogRepository;
    private final WorkflowConfigService workflowConfigService;
    private final JiraWriteService jiraWriteService;

    public MyWorklogService(JiraIssueRepository issueRepository, IssueWorklogRepository worklogRepository,
                            WorkflowConfigService workflowConfigService, JiraWriteService jiraWriteService) {
        this.issueRepository = issueRepository;
        this.worklogRepository = worklogRepository;
        this.workflowConfigService = workflowConfigService;
        this.jiraWriteService = jiraWriteService;
    }

    /**
     * Log time on a subtask from the current user's My Work page.
     *
     * <p>All validations run before any Jira call; once Jira is written, the local
     * upsert follows. If Jira fails, no local rows are written.</p>
     *
     * @return the Jira worklog id
     * @throws LogTimeValidationException hours/date/issue invalid (400)
     * @throws LogTimeForbiddenException the task is not assigned to {@code accountId} (403)
     * @throws JiraWriteService.NoUserTokenException no valid Jira OAuth token for the user (409)
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException Jira call failed (502)
     */
    @Transactional
    public String logTime(String accountId, String issueKey, LocalDate date, BigDecimal hours, String comment) {
        if (hours == null || hours.signum() <= 0 || hours.compareTo(BigDecimal.valueOf(24)) > 0) {
            throw new LogTimeValidationException("Hours must be between 0 and 24");
        }
        if (date == null || date.isAfter(LocalDate.now())) {
            throw new LogTimeValidationException("Date must not be in the future");
        }
        JiraIssueEntity issue = issueRepository.findByIssueKey(issueKey)
                .orElseThrow(() -> new LogTimeValidationException("Unknown issue: " + issueKey));
        if (!"SUBTASK".equals(issue.getBoardCategory())) {
            throw new LogTimeValidationException("Time can be logged only on subtasks");
        }
        if (!accountId.equals(issue.getAssigneeAccountId())) {
            throw new LogTimeForbiddenException("You can log time only on your own tasks");
        }

        int seconds = hours.multiply(BigDecimal.valueOf(3600)).intValue();

        String worklogId = jiraWriteService.logWorkAs(accountId, issueKey, seconds, date, comment);
        if (worklogId == null) {
            throw new IllegalStateException("Jira did not return worklog id");
        }

        IssueWorklogEntity entity = new IssueWorklogEntity();
        entity.setIssueKey(issueKey);
        entity.setWorklogId(worklogId);
        entity.setAuthorAccountId(accountId);
        entity.setTimeSpentSeconds(seconds);
        entity.setStartedDate(date);
        entity.setRoleCode(issue.getWorkflowRole() != null
                ? issue.getWorkflowRole()
                : workflowConfigService.getSubtaskRole(issue.getIssueType()));
        worklogRepository.save(entity);

        long cur = issue.getTimeSpentSeconds() != null ? issue.getTimeSpentSeconds() : 0L;
        issue.setTimeSpentSeconds(cur + seconds);
        issueRepository.save(issue);

        return worklogId;
    }
}
