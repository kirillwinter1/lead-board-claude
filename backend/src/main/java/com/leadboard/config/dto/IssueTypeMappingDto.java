package com.leadboard.config.dto;

import com.leadboard.config.entity.BoardCategory;

public record IssueTypeMappingDto(
        Long id,
        String jiraTypeName,
        BoardCategory boardCategory,
        String workflowRoleCode
) {}
