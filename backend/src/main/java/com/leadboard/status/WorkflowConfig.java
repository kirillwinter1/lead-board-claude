package com.leadboard.status;

import java.util.List;

/**
 * Конфигурация workflow для определённого типа задач (Epic, Story, Subtask).
 * Содержит списки статусов для каждой категории.
 */
public record WorkflowConfig(
        List<String> todoStatuses,
        List<String> inProgressStatuses,
        List<String> doneStatuses
) {
    public static WorkflowConfig empty() {
        return new WorkflowConfig(List.of(), List.of(), List.of());
    }

    /**
     * Мержит текущую конфигурацию с override.
     * Если в override поле не null и не пустое — берётся из override, иначе из this.
     */
    public WorkflowConfig merge(WorkflowConfig override) {
        if (override == null) {
            return this;
        }
        return new WorkflowConfig(
                (override.todoStatuses() != null && !override.todoStatuses().isEmpty())
                        ? override.todoStatuses() : this.todoStatuses(),
                (override.inProgressStatuses() != null && !override.inProgressStatuses().isEmpty())
                        ? override.inProgressStatuses() : this.inProgressStatuses(),
                (override.doneStatuses() != null && !override.doneStatuses().isEmpty())
                        ? override.doneStatuses() : this.doneStatuses()
        );
    }
}
