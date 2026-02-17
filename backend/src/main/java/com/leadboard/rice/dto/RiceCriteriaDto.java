package com.leadboard.rice.dto;

import java.util.List;

public record RiceCriteriaDto(
        Long id,
        String parameter,
        String name,
        String description,
        String selectionType,
        int sortOrder,
        List<RiceCriteriaOptionDto> options
) {}
