package com.leadboard.planning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Ответ API прогнозирования.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ForecastResponse(
        OffsetDateTime calculatedAt,
        Long teamId,
        Map<String, BigDecimal> roleCapacity,
        WipStatus wipStatus,
        List<EpicForecast> epics
) {
    /**
     * Статус WIP лимитов — ИНФОРМАЦИОННЫЙ.
     *
     * ВАЖНО: С версии F21 (Unified Planning) WIP лимиты НЕ влияют на планирование.
     * Это поле сохраняется для обратной совместимости API и отображения рекомендаций.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WipStatus(
            Integer limit,
            Integer current,
            Boolean exceeded,
            Map<String, RoleWipStatus> roleWip
    ) {
        public static WipStatus of(int limit, int current, Map<String, RoleWipStatus> roleWip) {
            return new WipStatus(limit, current, current > limit, roleWip);
        }
    }

    /**
     * WIP статус для конкретной роли — ИНФОРМАЦИОННЫЙ.
     */
    public record RoleWipStatus(
            Integer limit,
            Integer current,
            Boolean exceeded
    ) {
        public static RoleWipStatus of(int limit, int current) {
            return new RoleWipStatus(limit, current, current > limit);
        }
    }
}
