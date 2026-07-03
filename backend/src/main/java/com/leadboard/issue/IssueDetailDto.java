package com.leadboard.issue;

// F85 — lightweight per-issue detail for board hover tooltips (any issue type).
public record IssueDetailDto(
        String issueKey,
        String issueType,
        String summary,
        String description
) {}
