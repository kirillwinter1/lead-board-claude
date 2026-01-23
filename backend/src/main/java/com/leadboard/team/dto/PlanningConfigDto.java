package com.leadboard.team.dto;

import java.math.BigDecimal;

/**
 * Конфигурация автопланирования для команды.
 */
public record PlanningConfigDto(
        GradeCoefficients gradeCoefficients,
        BigDecimal riskBuffer,
        WipLimits wipLimits
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
     * WIP (Work In Progress) лимиты.
     * Ограничивают количество эпиков в работе.
     */
    public record WipLimits(
            Integer team,  // Общий лимит на команду
            Integer sa,    // Лимит эпиков на одного SA
            Integer dev,   // Лимит эпиков на одного DEV
            Integer qa     // Лимит эпиков на одного QA
    ) {
        public static WipLimits defaults() {
            return new WipLimits(6, 2, 3, 2);
        }
    }

    /**
     * Возвращает конфигурацию по умолчанию.
     */
    public static PlanningConfigDto defaults() {
        return new PlanningConfigDto(
                GradeCoefficients.defaults(),
                new BigDecimal("0.2"),
                WipLimits.defaults()
        );
    }
}
