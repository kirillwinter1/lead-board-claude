package com.leadboard.status;

/**
 * Категория статуса задачи.
 *
 * Для Epic: NEW → REQUIREMENTS → PLANNED → IN_PROGRESS → DONE
 * Для Story/Subtask: NEW → IN_PROGRESS → DONE
 *
 * TODO is kept as an alias for NEW for backward compatibility.
 */
public enum StatusCategory {
    NEW,
    REQUIREMENTS,  // Epic only: сбор требований, rough estimate разрешён
    PLANNED,       // Epic only: forecast/планирование активно
    IN_PROGRESS,
    DONE,

    /** @deprecated Use NEW instead. Kept for backward compatibility. */
    @Deprecated
    TODO;

    /**
     * Normalizes TODO to NEW for consistent comparison.
     */
    public StatusCategory normalized() {
        return this == TODO ? NEW : this;
    }

    /**
     * Returns true if this category represents a "not started" state (NEW, TODO, REQUIREMENTS).
     */
    public boolean isNotStarted() {
        return this == NEW || this == TODO || this == REQUIREMENTS;
    }
}
