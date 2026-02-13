package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Прогноз завершения эпика.
 */
public record EpicForecast(
        String epicKey,
        String summary,
        BigDecimal autoScore,
        LocalDate expectedDone,
        Confidence confidence,
        Integer dueDateDeltaDays,
        LocalDate dueDate,
        Map<String, RoleRemaining> remainingByRole,
        Map<String, PhaseInfo> phaseSchedule,

        // ============ LEGACY WIP FIELDS ============
        Integer queuePosition,
        LocalDate queuedUntil,
        Boolean isWithinWip,
        Map<String, RoleWaitInfo> phaseWaitInfo
) {

    public record RoleWaitInfo(
            Boolean waiting,
            LocalDate waitingUntil,
            Integer queuePosition
    ) {
        public static RoleWaitInfo none() {
            return new RoleWaitInfo(false, null, null);
        }
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * Остаток работы для роли.
     */
    public record RoleRemaining(
            BigDecimal hours,
            BigDecimal days
    ) {}

    /**
     * Информация о фазе.
     */
    public record PhaseInfo(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal workDays,
            boolean noCapacity
    ) {
        public PhaseInfo(LocalDate startDate, LocalDate endDate, BigDecimal workDays) {
            this(startDate, endDate, workDays, false);
        }
    }
}
