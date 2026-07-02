package com.leadboard.team.dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Конфигурация автопланирования для команды.
 */
public record PlanningConfigDto(
        GradeCoefficients gradeCoefficients,
        BigDecimal riskBuffer,
        WipLimits wipLimits,
        StoryDuration storyDuration
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
            Map<String, Integer> roleLimits  // Рекомендуемые лимиты по ролям (ключ = код роли)
    ) {
        public static WipLimits defaults() {
            // Hardcoded last resort: used only when workflowConfigService.getRoleCodesInPipelineOrder()
            // comes back empty (no roles configured yet). SA/DEV/QA is a reasonable starting guess,
            // not a real role list — never assume these codes exist elsewhere in business logic.
            return defaults(List.of("SA", "DEV", "QA"));
        }

        public static WipLimits defaults(List<String> roleCodes) {
            Map<String, Integer> roles = new LinkedHashMap<>();
            int total = 0;
            for (String code : roleCodes) {
                // Hardcoded last resort: DEV usually has more parallel WIP than other roles, so it
                // gets a slightly higher recommended limit. Purely a heuristic default for display —
                // WIP limits don't affect the planning algorithm itself (see class javadoc above).
                int limit = "DEV".equals(code) ? 3 : 2;
                roles.put(code, limit);
                total += limit;
            }
            return new WipLimits(total, roles);
        }
    }

    /**
     * Средняя длительность работы над одной сторёй по ролям (в днях).
     * Используется для расчёта pipeline offset — когда следующая роль может начать работу.
     * Например, если SA тратит 2 дня на сторю, то DEV может начать через 2 дня после начала SA.
     */
    public record StoryDuration(
            Map<String, BigDecimal> roleDurations  // Длительность по ролям (ключ = код роли)
    ) {
        public static StoryDuration defaults() {
            // Hardcoded last resort: same fallback as WipLimits.defaults() above — only reached
            // when workflowConfigService.getRoleCodesInPipelineOrder() is empty.
            return defaults(List.of("SA", "DEV", "QA"));
        }

        public static StoryDuration defaults(List<String> roleCodes) {
            Map<String, BigDecimal> roles = new LinkedHashMap<>();
            for (String code : roleCodes) {
                roles.put(code, new BigDecimal("2"));
            }
            return new StoryDuration(roles);
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
                StoryDuration.defaults()
        );
    }
}
