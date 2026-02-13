package com.leadboard.config.dto;

import java.util.Map;

public record ProjectConfigUpdateRequest(
        String name,
        Map<String, Integer> statusScoreWeights,
        String planningAllowedCategories,
        String timeLoggingAllowedCategories
) {}
