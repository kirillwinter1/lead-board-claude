package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Прогноз завершения эпика.
 */
public record EpicForecast(
        String epicKey,
        String summary,
        BigDecimal autoScore,
        Integer manualPriorityBoost,
        LocalDate expectedDone,
        Confidence confidence,
        Integer dueDateDeltaDays,
        LocalDate dueDate,
        RemainingByRole remainingByRole,
        PhaseSchedule phaseSchedule,
        // WIP fields
        Integer queuePosition,      // Позиция в очереди (null если в WIP)
        LocalDate queuedUntil,      // До какой даты в очереди
        Boolean isWithinWip         // Входит ли в активный WIP
) {
    /**
     * Конструктор для обратной совместимости (без WIP полей).
     */
    public EpicForecast(
            String epicKey,
            String summary,
            BigDecimal autoScore,
            Integer manualPriorityBoost,
            LocalDate expectedDone,
            Confidence confidence,
            Integer dueDateDeltaDays,
            LocalDate dueDate,
            RemainingByRole remainingByRole,
            PhaseSchedule phaseSchedule
    ) {
        this(epicKey, summary, autoScore, manualPriorityBoost, expectedDone,
             confidence, dueDateDeltaDays, dueDate, remainingByRole, phaseSchedule,
             null, null, true);
    }
    public enum Confidence {
        HIGH,    // Высокая уверенность: есть декомпозиция, команда полная
        MEDIUM,  // Средняя: есть оценки, но не полная декомпозиция
        LOW      // Низкая: нет оценок или нехватка ресурсов
    }

    /**
     * Остаток работы по ролям.
     */
    public record RemainingByRole(
            RoleRemaining sa,
            RoleRemaining dev,
            RoleRemaining qa
    ) {
        public static RemainingByRole empty() {
            return new RemainingByRole(
                    new RoleRemaining(BigDecimal.ZERO, BigDecimal.ZERO),
                    new RoleRemaining(BigDecimal.ZERO, BigDecimal.ZERO),
                    new RoleRemaining(BigDecimal.ZERO, BigDecimal.ZERO)
            );
        }
    }

    /**
     * Остаток работы для роли.
     */
    public record RoleRemaining(
            BigDecimal hours,
            BigDecimal days
    ) {}

    /**
     * Расписание фаз эпика (SA → DEV → QA).
     */
    public record PhaseSchedule(
            PhaseInfo sa,
            PhaseInfo dev,
            PhaseInfo qa
    ) {}

    /**
     * Информация о фазе.
     */
    public record PhaseInfo(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal workDays,
            boolean noCapacity  // true если нет ресурсов для этой роли
    ) {
        // Конструктор для обратной совместимости
        public PhaseInfo(LocalDate startDate, LocalDate endDate, BigDecimal workDays) {
            this(startDate, endDate, workDays, false);
        }
    }
}
