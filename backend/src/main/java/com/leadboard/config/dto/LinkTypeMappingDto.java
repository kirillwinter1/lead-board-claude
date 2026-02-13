package com.leadboard.config.dto;

import com.leadboard.config.entity.LinkCategory;

public record LinkTypeMappingDto(
        Long id,
        String jiraLinkTypeName,
        LinkCategory linkCategory
) {}
