package com.leadboard.config.dto;

import com.leadboard.config.entity.BoardCategory;
import com.leadboard.status.StatusCategory;

public record StatusMappingDto(
        Long id,
        String jiraStatusName,
        BoardCategory issueCategory,
        StatusCategory statusCategory,
        String workflowRoleCode,
        int sortOrder,
        int scoreWeight
) {}
