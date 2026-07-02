package com.leadboard.epic;

public record EpicDetailDto(
        String issueKey,
        String summary,
        String description
) {}
