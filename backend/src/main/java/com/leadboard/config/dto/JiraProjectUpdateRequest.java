package com.leadboard.config.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update for a Jira project — {@code null} fields are left unchanged
 * (see {@code JiraProjectService.update}). {@code displayName}, when present,
 * must not be an empty string.
 */
public record JiraProjectUpdateRequest(
        @Size(min = 1, max = 255, message = "displayName must not be blank")
        String displayName,
        Boolean active,
        Boolean syncEnabled
) {}
