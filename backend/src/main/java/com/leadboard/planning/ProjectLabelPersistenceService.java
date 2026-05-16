package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persists project label mutations in an isolated, writable transaction.
 *
 * <p>Mirrors the pattern of {@link EpicLabelPersistenceService} for the project
 * (desired_quarter) flow. The dedicated bean exists to defeat the Spring AOP
 * self-invocation pitfall: {@link QuarterlyPlanningService} is annotated
 * {@code @Transactional(readOnly = true)} at class level, so calling a write
 * method via {@code this.someMethod()} from another method of the same bean
 * bypasses the proxy and inherits the read-only transaction. Hibernate's
 * {@code FlushMode.MANUAL} then silently drops the save.</p>
 *
 * <p>Calls into this separate bean go through the Spring CGLIB proxy, so the
 * {@link Transactional} below is honoured. {@link Propagation#REQUIRES_NEW}
 * additionally suspends any enclosing read-only transaction and starts a fresh
 * writable one.</p>
 */
@Service
public class ProjectLabelPersistenceService {

    private final JiraIssueRepository issueRepository;

    public ProjectLabelPersistenceService(JiraIssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    /**
     * Overwrite the label set for the given project and persist it.
     *
     * @throws ProjectNotFoundException if the project does not exist
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mirrorProjectLabels(String projectKey, List<String> newLabels) {
        JiraIssueEntity project = issueRepository.findByIssueKey(projectKey)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + projectKey));
        project.setLabels(newLabels.toArray(new String[0]));
        issueRepository.save(project);
    }
}
