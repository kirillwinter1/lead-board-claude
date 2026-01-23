package com.leadboard.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JiraIssueRepository extends JpaRepository<JiraIssueEntity, Long> {

    Optional<JiraIssueEntity> findByIssueKey(String issueKey);

    List<JiraIssueEntity> findByProjectKey(String projectKey);

    List<JiraIssueEntity> findByProjectKeyAndIssueType(String projectKey, String issueType);

    List<JiraIssueEntity> findByProjectKeyAndSubtask(String projectKey, boolean subtask);

    List<JiraIssueEntity> findByParentKey(String parentKey);

    void deleteByProjectKey(String projectKey);
}
