package com.leadboard.issue;

import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueDetailService {

    private final JiraIssueRepository issueRepository;

    public IssueDetailService(JiraIssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    // Loads summary + description for any issue (epic/story/sub-task/project) to feed board tooltips.
    @Transactional(readOnly = true)
    public IssueDetailDto getIssueDetail(String issueKey) {
        JiraIssueEntity issue = issueRepository.findByIssueKey(issueKey)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueKey));
        return new IssueDetailDto(
                issue.getIssueKey(),
                issue.getIssueType(),
                issue.getSummary(),
                issue.getDescription()
        );
    }
}
