package com.leadboard.config.dto;

import java.util.List;
import java.util.Map;

/**
 * Full workflow configuration response.
 */
public record WorkflowConfigResponse(
        Long configId,
        String configName,
        String projectKey,
        List<WorkflowRoleDto> roles,
        List<IssueTypeMappingDto> issueTypes,
        List<StatusMappingDto> statuses,
        List<LinkTypeMappingDto> linkTypes,
        Map<String, Integer> statusScoreWeights,
        String planningAllowedCategories,
        String timeLoggingAllowedCategories
) {}
