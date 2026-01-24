package com.leadboard.planning.dto;

/**
 * Запрос на обновление ручного boost приоритета.
 */
public record UpdateBoostRequest(
        int boost
) {}
