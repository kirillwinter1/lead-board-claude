package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persists epic label mutations in an isolated, writable transaction.
 *
 * <p>Extracted from {@link QuarterlyPlanningService} to defeat the Spring AOP
 * self-invocation pitfall: that service is annotated {@code @Transactional(readOnly = true)}
 * at class level, so calling a write method via {@code this.someMethod()} from another
 * method of the same bean bypasses the proxy and inherits the read-only transaction.
 * Hibernate's {@code FlushMode.MANUAL} on read-only transactions then silently drops
 * the {@code save()} — the L1 cache reflects the change but nothing reaches the DB.</p>
 *
 * <p>Calls into this separate bean go through the Spring CGLIB proxy, so the
 * {@link Transactional} below is honoured. {@link Propagation#REQUIRES_NEW} additionally
 * suspends any enclosing read-only transaction and starts a fresh writable one,
 * making this safe to invoke from any caller regardless of their transaction state.</p>
 */
@Service
public class EpicLabelPersistenceService {

    private final JiraIssueRepository issueRepository;

    public EpicLabelPersistenceService(JiraIssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    /**
     * Overwrite the label set for the given epic and persist it.
     *
     * @throws EpicNotFoundException if the epic does not exist
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mirrorEpicLabels(String epicKey, List<String> newLabels) {
        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new EpicNotFoundException("Epic not found: " + epicKey));
        epic.setLabels(newLabels.toArray(new String[0]));
        issueRepository.save(epic);
    }
}
