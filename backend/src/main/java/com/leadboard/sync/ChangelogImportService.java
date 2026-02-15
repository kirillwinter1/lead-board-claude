package com.leadboard.sync;

import com.leadboard.jira.JiraChangelogResponse;
import com.leadboard.jira.JiraClient;
import com.leadboard.metrics.service.StatusChangelogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChangelogImportService {

    private static final Logger log = LoggerFactory.getLogger(ChangelogImportService.class);
    private static final long RATE_LIMIT_MS = 100;

    private final JiraClient jiraClient;
    private final JiraIssueRepository issueRepository;
    private final StatusChangelogService statusChangelogService;

    public ChangelogImportService(JiraClient jiraClient,
                                  JiraIssueRepository issueRepository,
                                  StatusChangelogService statusChangelogService) {
        this.jiraClient = jiraClient;
        this.issueRepository = issueRepository;
        this.statusChangelogService = statusChangelogService;
    }

    /**
     * Count issues that would be processed by changelog import.
     */
    public Map<String, Object> countIssuesForImport(String projectKey, Integer months) {
        long count;
        if (months != null && months > 0) {
            OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(months);
            count = issueRepository.countByProjectKeyAndUpdatedAtAfter(projectKey, since);
        } else {
            count = issueRepository.countByProjectKey(projectKey);
        }
        long total = issueRepository.countByProjectKey(projectKey);
        return Map.of(
                "issueCount", count,
                "totalIssues", total,
                "months", months != null ? months : 0
        );
    }

    /**
     * Batch import changelogs for ALL issues in the project.
     * Runs async — called from admin endpoint or after first sync.
     */
    @Async
    public void importAllChangelogsAsync(String projectKey, Integer months) {
        try {
            ImportResult result = importAllChangelogs(projectKey, months);
            log.info("Async changelog import completed for {}: {}", projectKey, result);
        } catch (Exception e) {
            log.error("Async changelog import failed for {}", projectKey, e);
        }
    }

    /**
     * Import changelogs only for specific issues (e.g. those that changed status during sync).
     * Runs async after each sync completes.
     */
    @Async
    public void importChangelogsForIssuesAsync(List<String> issueKeys) {
        if (issueKeys == null || issueKeys.isEmpty()) return;

        log.info("Starting changelog import for {} changed issues", issueKeys.size());
        int imported = 0;
        int failed = 0;

        for (String issueKey : issueKeys) {
            try {
                JiraIssueEntity issue = issueRepository.findByIssueKey(issueKey).orElse(null);
                if (issue == null) continue;

                boolean wasImported = importChangelogForIssue(issue.getIssueKey(), issue.getIssueId(), issue.getIssueType());
                if (wasImported) imported++;

                Thread.sleep(RATE_LIMIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to import changelog for {}: {}", issueKey, e.getMessage());
            }
        }

        log.info("Changelog import for changed issues completed: {} imported, {} failed out of {}",
                imported, failed, issueKeys.size());
    }

    /**
     * Batch import changelogs for all issues in the project (synchronous).
     */
    public ImportResult importAllChangelogs(String projectKey, Integer months) {
        List<JiraIssueEntity> allIssues;
        if (months != null && months > 0) {
            OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(months);
            allIssues = issueRepository.findByProjectKeyAndUpdatedAtAfter(projectKey, since);
            log.info("Starting changelog import for {} issues updated in last {} months in project {}",
                    allIssues.size(), months, projectKey);
        } else {
            allIssues = issueRepository.findByProjectKey(projectKey);
            log.info("Starting changelog import for {} issues in project {}", allIssues.size(), projectKey);
        }

        int imported = 0;
        int failed = 0;
        int skipped = 0;

        for (JiraIssueEntity issue : allIssues) {
            try {
                boolean wasImported = importChangelogForIssue(issue.getIssueKey(), issue.getIssueId(), issue.getIssueType());
                if (wasImported) {
                    imported++;
                } else {
                    skipped++;
                }

                Thread.sleep(RATE_LIMIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Changelog import interrupted at issue {}", issue.getIssueKey());
                break;
            } catch (Exception e) {
                failed++;
                log.error("Failed to import changelog for {}: {}", issue.getIssueKey(), e.getMessage());
            }
        }

        // Fix started_at and done_at from real changelog data
        int startedFixed = fixStartedAtFromChangelog(projectKey);
        int doneFixed = fixDoneAtFromChangelog(projectKey);

        log.info("Changelog import completed: {} imported, {} skipped, {} failed, {} started_at fixed, {} done_at fixed",
                imported, skipped, failed, startedFixed, doneFixed);

        return new ImportResult(imported, skipped, failed, startedFixed, doneFixed);
    }

    /**
     * Import changelog for a single issue.
     *
     * @return true if changelog was imported, false if skipped (e.g. no transitions)
     */
    public boolean importChangelogForIssue(String issueKey, String issueId, String issueType) {
        List<JiraChangelogResponse.ChangelogHistory> histories = jiraClient.fetchIssueChangelog(issueKey);

        if (histories == null || histories.isEmpty()) {
            log.debug("No changelog found for {}", issueKey);
            return false;
        }

        int count = statusChangelogService.importJiraChangelog(issueKey, issueId, histories);

        if (count > 0) {
            fixDatesForIssue(issueKey, issueType);
        }

        return count > 0;
    }

    @Transactional
    public int fixStartedAtFromChangelog(String projectKey) {
        List<JiraIssueEntity> allIssues = issueRepository.findByProjectKey(projectKey);
        int fixed = 0;

        for (JiraIssueEntity issue : allIssues) {
            Optional<OffsetDateTime> firstInProgress =
                    statusChangelogService.findFirstInProgressTransition(issue.getIssueKey(), issue.getIssueType());

            if (firstInProgress.isPresent()) {
                OffsetDateTime realStartedAt = firstInProgress.get();
                if (!realStartedAt.equals(issue.getStartedAt())) {
                    issue.setStartedAt(realStartedAt);
                    issueRepository.save(issue);
                    fixed++;
                    log.debug("Fixed started_at for {}: {}", issue.getIssueKey(), realStartedAt);
                }
            }
        }

        return fixed;
    }

    @Transactional
    public int fixDoneAtFromChangelog(String projectKey) {
        List<JiraIssueEntity> allIssues = issueRepository.findByProjectKey(projectKey);
        int fixed = 0;

        for (JiraIssueEntity issue : allIssues) {
            Optional<OffsetDateTime> lastDone =
                    statusChangelogService.findLastDoneTransition(issue.getIssueKey(), issue.getIssueType());

            if (lastDone.isPresent()) {
                OffsetDateTime realDoneAt = lastDone.get();
                if (!realDoneAt.equals(issue.getDoneAt())) {
                    issue.setDoneAt(realDoneAt);
                    issueRepository.save(issue);
                    fixed++;
                    log.debug("Fixed done_at for {}: {}", issue.getIssueKey(), realDoneAt);
                }
            }
        }

        return fixed;
    }

    private void fixDatesForIssue(String issueKey, String issueType) {
        JiraIssueEntity issue = issueRepository.findByIssueKey(issueKey).orElse(null);
        if (issue == null) return;

        boolean changed = false;

        Optional<OffsetDateTime> firstInProgress =
                statusChangelogService.findFirstInProgressTransition(issueKey, issueType);
        if (firstInProgress.isPresent() && !firstInProgress.get().equals(issue.getStartedAt())) {
            issue.setStartedAt(firstInProgress.get());
            changed = true;
        }

        Optional<OffsetDateTime> lastDone =
                statusChangelogService.findLastDoneTransition(issueKey, issueType);
        if (lastDone.isPresent() && !lastDone.get().equals(issue.getDoneAt())) {
            issue.setDoneAt(lastDone.get());
            changed = true;
        }

        if (changed) {
            issueRepository.save(issue);
        }
    }

    public record ImportResult(int imported, int skipped, int failed, int startedAtFixed, int doneAtFixed) {}
}
