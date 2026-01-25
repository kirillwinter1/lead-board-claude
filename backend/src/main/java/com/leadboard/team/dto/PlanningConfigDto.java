package com.leadboard.team.dto;

import com.leadboard.status.StatusMappingConfig;

import java.math.BigDecimal;

/**
 * Конфигурация автопланирования для команды.
 */
public record PlanningConfigDto(
        GradeCoefficients gradeCoefficients,
        BigDecimal riskBuffer,
        WipLimits wipLimits,
        StoryDuration storyDuration,
        StatusMappingConfig statusMapping
) {
    /**
     * Коэффициенты производительности по грейдам.
     * Чем меньше коэффициент, тем быстрее работает специалист.
     * - Senior: 0.8 (делает 1 человеко-день за 0.8 реального дня)
     * - Middle: 1.0 (базовый)
     * - Junior: 1.5 (делает 1 человеко-день за 1.5 реального дня)
     */
    public record GradeCoefficients(
            BigDecimal senior,
            BigDecimal middle,
            BigDecimal junior
    ) {
        public static GradeCoefficients defaults() {
            return new GradeCoefficients(
                    new BigDecimal("0.8"),
                    new BigDecimal("1.0"),
                    new BigDecimal("1.5")
            );
        }
    }

    /**
     * WIP (Work In Progress) лимиты — РЕКОМЕНДАТЕЛЬНЫЕ значения.
     *
     * ВАЖНО: С версии F21 (Unified Planning) WIP лимиты НЕ влияют на алгоритм планирования.
     * UnifiedPlanningService планирует все эпики на основе реальной capacity команды
     * без искусственных ограничений WIP.
     *
     * Эти значения сохраняются как рекомендации и используются для:
     * - Отображения рекомендательных границ в UI
     * - Исторического анализа (WIP snapshots)
     * - Метрик и отчётов
     *
     * @deprecated для планирования. Используется только для информации.
     */
    public record WipLimits(
            Integer team,  // Рекомендуемый общий лимит на команду
            Integer sa,    // Рекомендуемый лимит для SA
            Integer dev,   // Рекомендуемый лимит для DEV
            Integer qa     // Рекомендуемый лимит для QA
    ) {
        public static WipLimits defaults() {
            return new WipLimits(6, 2, 3, 2);
        }
    }

    /**
     * Средняя длительность работы над одной сторёй по ролям (в днях).
     * Используется для расчёта pipeline offset — когда следующая роль может начать работу.
     * Например, если SA тратит 2 дня на сторю, то DEV может начать через 2 дня после начала SA.
     */
    public record StoryDuration(
            BigDecimal sa,   // Дней на сторю для SA
            BigDecimal dev,  // Дней на сторю для DEV
            BigDecimal qa    // Дней на сторю для QA
    ) {
        public static StoryDuration defaults() {
            return new StoryDuration(
                    new BigDecimal("2"),
                    new BigDecimal("2"),
                    new BigDecimal("2")
            );
        }
    }

    /**
     * Возвращает конфигурацию по умолчанию.
     */
    public static PlanningConfigDto defaults() {
        return new PlanningConfigDto(
                GradeCoefficients.defaults(),
                new BigDecimal("0.2"),
                WipLimits.defaults(),
                StoryDuration.defaults(),
                null  // statusMapping берётся из StatusMappingService
        );
    }
}
