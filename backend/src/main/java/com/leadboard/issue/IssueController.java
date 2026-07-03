package com.leadboard.issue;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueDetailService issueDetailService;

    public IssueController(IssueDetailService issueDetailService) {
        this.issueDetailService = issueDetailService;
    }

    @GetMapping("/{issueKey}/detail")
    public ResponseEntity<IssueDetailDto> getIssueDetail(@PathVariable String issueKey) {
        try {
            return ResponseEntity.ok(issueDetailService.getIssueDetail(issueKey));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
