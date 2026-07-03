package com.leadboard.issue;

import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueDetailServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Test
    void returnsTypeSummaryAndDescriptionForAnyIssue() {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("LB-2");
        story.setIssueType("Story");
        story.setSummary("A story");
        story.setDescription("Story description");
        when(issueRepository.findByIssueKey("LB-2")).thenReturn(Optional.of(story));

        IssueDetailService service = new IssueDetailService(issueRepository);
        IssueDetailDto dto = service.getIssueDetail("LB-2");

        assertEquals("LB-2", dto.issueKey());
        assertEquals("Story", dto.issueType());
        assertEquals("A story", dto.summary());
        assertEquals("Story description", dto.description());
    }

    @Test
    void throwsWhenNotFound() {
        when(issueRepository.findByIssueKey("LB-404")).thenReturn(Optional.empty());
        IssueDetailService service = new IssueDetailService(issueRepository);
        assertThrows(IllegalArgumentException.class, () -> service.getIssueDetail("LB-404"));
    }
}
