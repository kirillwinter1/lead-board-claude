package com.leadboard.status;

import java.util.List;

/**
 * Полная конфигурация маппинга статусов.
 * Содержит workflow для разных типов задач и маппинг фаз.
 */
public record StatusMappingConfig(
        WorkflowConfig epicWorkflow,
        WorkflowConfig storyWorkflow,
        WorkflowConfig subtaskWorkflow,
        PhaseMapping phaseMapping,
        List<String> planningAllowedStatuses,
        List<String> timeLoggingAllowedStatuses
) {
    /**
     * Возвращает конфигурацию по умолчанию для русских и английских Jira-проектов.
     */
    public static StatusMappingConfig defaults() {
        return new StatusMappingConfig(
                new WorkflowConfig(
                        List.of("New", "Requirements", "Rough Estimate", "Backlog", "To Do",
                                "Новый", "Требования", "Оценка", "Бэклог", "Сделать"),
                        List.of("Planned", "Developing", "E2E Testing", "Acceptance",
                                "Запланировано", "В разработке", "E2E Тестирование", "Приёмка"),
                        List.of("Done", "Closed", "Resolved",
                                "Готово", "Закрыто", "Решено")
                ),
                new WorkflowConfig(
                        List.of("New", "Ready", "Waiting Dev", "Waiting QA", "Ready to Release",
                                "Новый", "Готов", "Ожидает разработки", "Ожидает тестирования", "Готов к релизу"),
                        List.of("Analysis", "Analysis Review", "Development", "Dev Review", "Testing", "Test Review",
                                "Анализ", "Ревью анализа", "Разработка", "Ревью разработки", "Тестирование", "Ревью тестирования"),
                        List.of("Done", "Готово")
                ),
                new WorkflowConfig(
                        List.of("New", "Новый"),
                        List.of("In Progress", "Review", "В работе", "Ревью"),
                        List.of("Done", "Готово")
                ),
                new PhaseMapping(
                        List.of("Analysis", "Analysis Review", "Requirements",
                                "Анализ", "Ревью анализа", "Требования"),
                        List.of("Development", "Dev Review", "Developing",
                                "Разработка", "Ревью разработки", "В разработке"),
                        List.of("Testing", "Test Review", "E2E Testing",
                                "Тестирование", "Ревью тестирования", "E2E Тестирование"),
                        List.of("Аналитика", "Analysis", "Analytics"),
                        List.of("Тестирование", "Testing", "Bug", "Баг", "Дефект")
                ),
                // Planning allowed statuses - epics in these statuses will get Expected Done
                List.of("Planned", "Developing", "E2E Testing",
                        "Запланировано", "В разработке", "E2E Тестирование"),
                // Time logging allowed statuses - time can only be logged on children of epics in these statuses
                List.of("Developing", "E2E Testing",
                        "В разработке", "E2E Тестирование")
        );
    }

    /**
     * Мержит текущую конфигурацию с override.
     * Используется для переопределения дефолтов на уровне команды.
     */
    public StatusMappingConfig merge(StatusMappingConfig override) {
        if (override == null) {
            return this;
        }
        return new StatusMappingConfig(
                this.epicWorkflow != null
                        ? this.epicWorkflow.merge(override.epicWorkflow())
                        : override.epicWorkflow(),
                this.storyWorkflow != null
                        ? this.storyWorkflow.merge(override.storyWorkflow())
                        : override.storyWorkflow(),
                this.subtaskWorkflow != null
                        ? this.subtaskWorkflow.merge(override.subtaskWorkflow())
                        : override.subtaskWorkflow(),
                this.phaseMapping != null
                        ? this.phaseMapping.merge(override.phaseMapping())
                        : override.phaseMapping(),
                mergeList(this.planningAllowedStatuses, override.planningAllowedStatuses()),
                mergeList(this.timeLoggingAllowedStatuses, override.timeLoggingAllowedStatuses())
        );
    }

    private static List<String> mergeList(List<String> base, List<String> override) {
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return base != null ? base : List.of();
    }
}
