package com.leadboard.planning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Запрос на обновление ручного boost приоритета.
 */
public record UpdateBoostRequest(
        @Min(0) @Max(5)
        int boost
) {}
