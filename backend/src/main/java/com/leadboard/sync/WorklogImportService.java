package com.leadboard.sync;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraWorklogResponse;
import com.leadboard.metrics.entity.IssueWorklogEntity;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WorklogImportService {

    private static final Logger log = LoggerFactory.getLogger(WorklogImportService.class);
    private static final long RATE_LIMIT_MS = 100;

    private static class ProgressState {
        final AtomicBoolean inProgress = new AtomicBoolean(false);
        final AtomicInteger processed = new AtomicInteger(0);
        final AtomicInteger total = new AtomicInteger(0);
        final AtomicInteger imported = new AtomicInteger(0);
        // Candidate 6: keys that arrived while an import was already running. They must NOT be
        // dropped — the active importer drains and processes them when it finishes. A Set gives
        // free de-duplication if the same subtask is enqueued twice.
        final Set<String> pending = ConcurrentHashMap.newKeySet();

        void reset() {
            processed.set(0);
            total.set(0);
            imported.set(0);
        }
    }

    private final ConcurrentHashMap<String, ProgressState> progressByTenant = new ConcurrentHashMap<>();

    private ProgressState getState() {
        String schema = com.leadboard.tenant.TenantContext.getCurrentSchema();
        return progressByTenant.computeIfAbsent(schema, k -> new ProgressState());
    }

    public record ImportProgress(boolean inProgress, int processed, int total, int imported) {}

    public ImportProgress getProgress() {
        ProgressState state = getState();
        return new ImportProgress(state.inProgress.get(), state.processed.get(), state.total.get(), state.imported.get());
    }

    private final JiraClient jiraClient;
    private final JiraIssueRepository issueRepository;
    private final IssueWorklogRepository worklogRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final WorkflowConfigService workflowConfigService;

    // Self-reference (through the Spring proxy) so per-issue @Transactional applies when
    // importWorklogsForIssue is invoked from the @Async loops below — a plain this.-call
    // would bypass the proxy and run the delete+insert without a transaction.
    @Autowired
    @Lazy
    private WorklogImportService self;

    public WorklogImportService(JiraClient jiraClient,
                                JiraIssueRepository issueRepository,
                                IssueWorklogRepository worklogRepository,
                                TeamMemberRepository teamMemberRepository,
                                WorkflowConfigService workflowConfigService) {
        this.jiraClient = jiraClient;
        this.issueRepository = issueRepository;
        this.worklogRepository = worklogRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.workflowConfigService = workflowConfigService;
    }

    /**
     * Called after sync: if worklogs table is empty, run full import; otherwise incremental for changed subtasks.
     */
    public void importWorklogsAfterSync(String projectKey, List<String> statusChangedKeys) {
        long existingCount = worklogRepository.count();
        if (existingCount == 0) {
            log.info("Worklogs table empty — triggering full worklog import for project {}", projectKey);
            importAllWorklogsAsync(projectKey);
        } else if (statusChangedKeys != null && !statusChangedKeys.isEmpty()) {
            List<String> subtaskKeys = statusChangedKeys.stream()
                    .filter(key -> {
                        JiraIssueEntity issue = issueRepository.findByIssueKey(key).orElse(null);
                        return issue != null && workflowConfigService.isSubtask(issue.getIssueType());
                    })
                    .toList();
            if (!subtaskKeys.isEmpty()) {
                log.info("Scheduling incremental worklog import for {} subtasks", subtaskKeys.size());
                importWorklogsForIssuesAsync(subtaskKeys);
            }
        }
    }

    /**
     * Import worklogs for specific subtask issue keys (called after sync).
     *
     * <p>Candidate 6: if an import is already running, the incoming keys are NOT dropped. They
     * are queued into {@link ProgressState#pending} and the active importer drains them when it
     * finishes. Lock-free single-consumer pattern: enqueue first, then try to become the active
     * importer; whoever wins the CAS drains the queue in a loop, so keys queued by a caller that
     * lost the CAS are always picked up. The trailing re-acquire in the do/while closes the
     * window where a key is enqueued after the last drain but before the flag is released.
     */
    @Async
    public void importWorklogsForIssuesAsync(List<String> issueKeys) {
        if (issueKeys == null || issueKeys.isEmpty()) return;

        ProgressState state = getState();
        state.pending.addAll(issueKeys);

        if (!state.inProgress.compareAndSet(false, true)) {
            log.info("Worklog import busy — {} keys queued for the active run", issueKeys.size());
            return;
        }

        do {
            try {
                List<String> batch;
                while (!(batch = drainPending(state)).isEmpty()) {
                    importBatch(state, batch);
                }
            } finally {
                state.inProgress.set(false);
            }
        } while (!state.pending.isEmpty() && state.inProgress.compareAndSet(false, true));
    }

    /**
     * Drain the currently-pending keys into a snapshot list, removing exactly that snapshot from
     * the set. Keys added concurrently after the snapshot remain queued for the next drain.
     */
    private List<String> drainPending(ProgressState state) {
        if (state.pending.isEmpty()) return List.of();
        List<String> drained = new ArrayList<>(state.pending);
        state.pending.removeAll(drained);
        return drained;
    }

    /**
     * Import one batch of subtask keys. Caller must already hold the {@code inProgress} flag.
     */
    private void importBatch(ProgressState state, List<String> issueKeys) {
        state.reset();
        state.total.set(issueKeys.size());

        log.info("Starting worklog import for {} issues", issueKeys.size());
        int failed = 0;

        for (String issueKey : issueKeys) {
            try {
                int count = self.importWorklogsForIssue(issueKey);
                if (count > 0) state.imported.incrementAndGet();
                state.processed.incrementAndGet();

                Thread.sleep(RATE_LIMIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failed++;
                state.processed.incrementAndGet();
                log.warn("Failed to import worklogs for {}: {}", issueKey, e.getMessage());
            }
        }

        log.info("Worklog import completed: {} imported, {} failed out of {}",
                state.imported.get(), failed, issueKeys.size());
    }

    /**
     * Batch import worklogs for all subtasks in a project.
     */
    @Async
    public void importAllWorklogsAsync(String projectKey) {
        ProgressState state = getState();
        if (!state.inProgress.compareAndSet(false, true)) {
            log.warn("Worklog import already in progress, skipping");
            return;
        }
        state.reset();

        try {
            List<JiraIssueEntity> subtasks = issueRepository.findByProjectKey(projectKey).stream()
                    .filter(i -> workflowConfigService.isSubtask(i.getIssueType()))
                    .toList();

            state.total.set(subtasks.size());
            log.info("Starting worklog import for {} subtasks in project {}", subtasks.size(), projectKey);
            int failed = 0;

            for (JiraIssueEntity subtask : subtasks) {
                try {
                    int count = self.importWorklogsForIssue(subtask.getIssueKey());
                    if (count > 0) state.imported.incrementAndGet();
                    state.processed.incrementAndGet();
                    Thread.sleep(RATE_LIMIT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    failed++;
                    state.processed.incrementAndGet();
                    log.warn("Failed to import worklogs for {}: {}", subtask.getIssueKey(), e.getMessage());
                }
            }

            log.info("Worklog import completed for {}: {} imported, {} failed out of {}",
                    projectKey, state.imported.get(), failed, subtasks.size());
        } finally {
            state.inProgress.set(false);
        }

        // Candidate 6: incremental keys may have been queued while this full import held the flag.
        // Drain them so they are not stranded until the next status/time change of the subtask.
        if (!state.pending.isEmpty() && state.inProgress.compareAndSet(false, true)) {
            do {
                try {
                    List<String> batch;
                    while (!(batch = drainPending(state)).isEmpty()) {
                        importBatch(state, batch);
                    }
                } finally {
                    state.inProgress.set(false);
                }
            } while (!state.pending.isEmpty() && state.inProgress.compareAndSet(false, true));
        }
    }

    /**
     * Import worklogs for a single issue. Idempotent: deletes existing and re-inserts.
     * Transactional so the delete + re-insert are atomic — a failure mid-insert rolls
     * back the delete instead of leaving the issue with its worklogs partially wiped.
     * @return number of worklogs imported
     */
    @Transactional
    public int importWorklogsForIssue(String issueKey) {
        List<JiraWorklogResponse.WorklogEntry> worklogs = jiraClient.fetchIssueWorklogs(issueKey);

        if (worklogs == null || worklogs.isEmpty()) {
            return 0;
        }

        // Resolve role for this subtask
        JiraIssueEntity subtask = issueRepository.findByIssueKey(issueKey).orElse(null);
        String roleCode = resolveRole(subtask);

        // Delete existing worklogs for this issue (idempotent)
        worklogRepository.deleteByIssueKey(issueKey);

        int count = 0;
        for (JiraWorklogResponse.WorklogEntry entry : worklogs) {
            LocalDate startedDate = parseStartedDate(entry.getStarted());
            if (startedDate == null) continue;

            IssueWorklogEntity entity = new IssueWorklogEntity();
            entity.setIssueKey(issueKey);
            entity.setWorklogId(entry.getId());
            entity.setAuthorAccountId(entry.getAuthor() != null ? entry.getAuthor().getAccountId() : null);
            entity.setTimeSpentSeconds(entry.getTimeSpentSeconds());
            entity.setStartedDate(startedDate);
            entity.setRoleCode(roleCode);

            worklogRepository.save(entity);
            count++;
        }

        return count;
    }

    /**
     * Resolve role code for a subtask:
     * 1. subtask.workflowRole (primary — set by workflow config)
     * 2. team_members.role for assignee (fallback)
     */
    String resolveRole(JiraIssueEntity subtask) {
        if (subtask == null) return null;

        // Primary: subtask's own workflow role
        String workflowRole = subtask.getWorkflowRole();
        if (workflowRole != null && !workflowRole.isEmpty()) {
            return workflowRole;
        }

        // Fallback: look up team member role by assignee
        String assignee = subtask.getAssigneeAccountId();
        if (assignee != null && !assignee.isEmpty()) {
            return teamMemberRepository.findFirstByJiraAccountIdAndActiveTrue(assignee)
                    .map(TeamMemberEntity::getRole)
                    .orElse(null);
        }

        return null;
    }

    /**
     * Parse Jira datetime string to LocalDate.
     * Supports ISO datetime (e.g. "2024-01-15T10:00:00.000+0000") and plain date.
     */
    static LocalDate parseStartedDate(String started) {
        if (started == null || started.isEmpty()) return null;
        try {
            // Try ISO datetime first
            if (started.contains("T")) {
                OffsetDateTime odt = OffsetDateTime.parse(started, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                return odt.toLocalDate();
            }
            return LocalDate.parse(started);
        } catch (DateTimeParseException e) {
            // Try alternative Jira format: "2024-01-15T10:00:00.000+0000"
            try {
                // Strip everything after the date
                String dateOnly = started.substring(0, 10);
                return LocalDate.parse(dateOnly);
            } catch (Exception ex) {
                log.warn("Failed to parse worklog date: {}", started);
                return null;
            }
        }
    }
}
