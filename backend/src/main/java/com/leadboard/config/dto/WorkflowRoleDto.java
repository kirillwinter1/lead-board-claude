package com.leadboard.config.dto;

public record WorkflowRoleDto(
        Long id,
        String code,
        String displayName,
        String color,
        int sortOrder,
        boolean isDefault
) {}
