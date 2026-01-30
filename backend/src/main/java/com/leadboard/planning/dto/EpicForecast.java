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
        LocalDate expectedDone,
        Confidence confidence,
        Integer dueDateDeltaDays,
        LocalDate dueDate,
        RemainingByRole remainingByRole,
        PhaseSchedule phaseSchedule,

        // ============ LEGACY WIP FIELDS ============
        // С версии F21 (Unified Planning) эти поля НЕ влияют на планирование.
        // Все эпики планируются на основе реальной capacity без WIP ограничений.
        // Поля сохранены для обратной совместимости API.

        Integer queuePosition,      // Всегда null (очередь не используется)
        LocalDate queuedUntil,      // Всегда null (очередь не используется)
        Boolean isWithinWip,        // Всегда true (все эпики "активны")
        PhaseWaitInfo phaseWaitInfo // Всегда PhaseWaitInfo.none()
) {
    /**
     * Конструктор для обратной совместимости (без WIP полей).
     */
    public EpicForecast(
            String epicKey,
            String summary,
            BigDecimal autoScore,
            LocalDate expectedDone,
            Confidence confidence,
            Integer dueDateDeltaDays,
            LocalDate dueDate,
            RemainingByRole remainingByRole,
            PhaseSchedule phaseSchedule
    ) {
        this(epicKey, summary, autoScore, expectedDone,
             confidence, dueDateDeltaDays, dueDate, remainingByRole, phaseSchedule,
             null, null, true, null);
    }

    /**
     * Информация об ожидании входа в фазы из-за роль-специфичных WIP лимитов.
     */
    public record PhaseWaitInfo(
            RoleWaitInfo sa,
            RoleWaitInfo dev,
            RoleWaitInfo qa
    ) {
        public static PhaseWaitInfo none() {
            return new PhaseWaitInfo(
                    RoleWaitInfo.none(),
                    RoleWaitInfo.none(),
                    RoleWaitInfo.none()
            );
        }
    }

    /**
     * Информация об ожидании для конкретной роли.
     */
    public record RoleWaitInfo(
            Boolean waiting,           // Ожидает ли вход в фазу
            LocalDate waitingUntil,    // До какой даты ждёт
            Integer queuePosition      // Позиция в очереди на эту фазу
    ) {
        public static RoleWaitInfo none() {
            return new RoleWaitInfo(false, null, null);
        }

        public static RoleWaitInfo waiting(LocalDate until, int position) {
            return new RoleWaitInfo(true, until, position);
        }
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
