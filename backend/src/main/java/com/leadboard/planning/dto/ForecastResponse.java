package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ответ API прогнозирования.
 */
public record ForecastResponse(
        OffsetDateTime calculatedAt,
        Long teamId,
        TeamCapacity teamCapacity,
        WipStatus wipStatus,
        List<EpicForecast> epics
) {
    /**
     * Суммарный capacity команды по ролям (часы в день).
     */
    public record TeamCapacity(
            BigDecimal saHoursPerDay,
            BigDecimal devHoursPerDay,
            BigDecimal qaHoursPerDay
    ) {}

    /**
     * Статус WIP лимитов.
     */
    public record WipStatus(
            Integer limit,      // WIP лимит команды
            Integer current,    // Текущее количество активных эпиков
            Boolean exceeded,   // Превышен ли лимит
            RoleWipStatus sa,   // WIP статус для SA
            RoleWipStatus dev,  // WIP статус для DEV
            RoleWipStatus qa    // WIP статус для QA
    ) {
        public static WipStatus of(int limit, int current) {
            return new WipStatus(limit, current, current > limit, null, null, null);
        }

        public static WipStatus of(int limit, int current, RoleWipStatus sa, RoleWipStatus dev, RoleWipStatus qa) {
            return new WipStatus(limit, current, current > limit, sa, dev, qa);
        }
    }

    /**
     * WIP статус для конкретной роли.
     */
    public record RoleWipStatus(
            Integer limit,      // Лимит для роли
            Integer current,    // Текущее количество эпиков на этой фазе
            Boolean exceeded    // Превышен ли лимит
    ) {
        public static RoleWipStatus of(int limit, int current) {
            return new RoleWipStatus(limit, current, current > limit);
        }
    }
}
