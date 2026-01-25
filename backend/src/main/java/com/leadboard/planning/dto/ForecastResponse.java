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
     * Статус WIP лимитов — ИНФОРМАЦИОННЫЙ.
     *
     * ВАЖНО: С версии F21 (Unified Planning) WIP лимиты НЕ влияют на планирование.
     * Это поле сохраняется для обратной совместимости API и отображения рекомендаций.
     *
     * В текущей реализации:
     * - limit = current = количество эпиков (все "активны")
     * - exceeded = false (нет превышения)
     */
    public record WipStatus(
            Integer limit,      // Рекомендуемый WIP лимит команды
            Integer current,    // Текущее количество эпиков в плане
            Boolean exceeded,   // Всегда false (WIP не применяется)
            RoleWipStatus sa,   // Информация по SA
            RoleWipStatus dev,  // Информация по DEV
            RoleWipStatus qa    // Информация по QA
    ) {
        public static WipStatus of(int limit, int current) {
            return new WipStatus(limit, current, current > limit, null, null, null);
        }

        public static WipStatus of(int limit, int current, RoleWipStatus sa, RoleWipStatus dev, RoleWipStatus qa) {
            return new WipStatus(limit, current, current > limit, sa, dev, qa);
        }
    }

    /**
     * WIP статус для конкретной роли — ИНФОРМАЦИОННЫЙ.
     * Сохраняется для обратной совместимости.
     */
    public record RoleWipStatus(
            Integer limit,      // Рекомендуемый лимит для роли
            Integer current,    // Количество эпиков
            Boolean exceeded    // Всегда false (WIP не применяется)
    ) {
        public static RoleWipStatus of(int limit, int current) {
            return new RoleWipStatus(limit, current, current > limit);
        }
    }
}
