package com.leadboard.status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис для определения категории и фазы статуса.
 * Использует конфигурацию из application.yml как базу и позволяет
 * переопределять на уровне команды.
 */
@Service
public class StatusMappingService {

    private static final Logger log = LoggerFactory.getLogger(StatusMappingService.class);

    private final StatusMappingProperties properties;
    private final StatusMappingConfig defaultConfig;

    public StatusMappingService(StatusMappingProperties properties) {
        this.properties = properties;
        // Мержим дефолты из кода с конфигурацией из YAML
        this.defaultConfig = StatusMappingConfig.defaults().merge(properties.toConfig());
    }

    /**
     * Возвращает дефолтную конфигурацию (код + application.yml).
     */
    public StatusMappingConfig getDefaultConfig() {
        return defaultConfig;
    }

    /**
     * Возвращает итоговую конфигурацию после мержа с override команды.
     */
    public StatusMappingConfig getEffectiveConfig(StatusMappingConfig teamOverride) {
        if (teamOverride == null) {
            return defaultConfig;
        }
        return defaultConfig.merge(teamOverride);
    }

    /**
     * Определяет категорию статуса (TODO/IN_PROGRESS/DONE) для Epic.
     */
    public StatusCategory categorizeEpic(String status, StatusMappingConfig teamOverride) {
        StatusMappingConfig config = getEffectiveConfig(teamOverride);
        return categorize(status, config.epicWorkflow());
    }

    /**
     * Определяет категорию статуса (TODO/IN_PROGRESS/DONE) для Story.
     */
    public StatusCategory categorizeStory(String status, StatusMappingConfig teamOverride) {
        StatusMappingConfig config = getEffectiveConfig(teamOverride);
        return categorize(status, config.storyWorkflow());
    }

    /**
     * Определяет категорию статуса (TODO/IN_PROGRESS/DONE) для Subtask.
     */
    public StatusCategory categorizeSubtask(String status, StatusMappingConfig teamOverride) {
        StatusMappingConfig config = getEffectiveConfig(teamOverride);
        return categorize(status, config.subtaskWorkflow());
    }

    /**
     * Определяет категорию по типу задачи (Epic/Story/Sub-task).
     */
    public StatusCategory categorize(String status, String issueType, StatusMappingConfig teamOverride) {
        if (issueType == null) {
            return categorizeStory(status, teamOverride);
        }
        String typeLower = issueType.toLowerCase();
        if (typeLower.contains("epic") || typeLower.contains("эпик")) {
            return categorizeEpic(status, teamOverride);
        }
        if (typeLower.contains("sub-task") || typeLower.contains("подзадача")) {
            return categorizeSubtask(status, teamOverride);
        }
        return categorizeStory(status, teamOverride);
    }

    /**
     * Определяет фазу (SA/DEV/QA) по статусу и типу задачи.
     */
    public String determinePhase(String status, String issueType, StatusMappingConfig teamOverride) {
        StatusMappingConfig config = getEffectiveConfig(teamOverride);
        PhaseMapping pm = config.phaseMapping();

        String statusLower = status != null ? status.toLowerCase() : "";
        String typeLower = issueType != null ? issueType.toLowerCase() : "";

        // Сначала проверяем по типу задачи (приоритетнее)
        if (matchesAny(typeLower, pm.saIssueTypes())) {
            return "SA";
        }
        if (matchesAny(typeLower, pm.qaIssueTypes())) {
            return "QA";
        }

        // Затем проверяем по статусу
        // Точное совпадение
        if (containsIgnoreCase(pm.saStatuses(), status)) {
            return "SA";
        }
        if (containsIgnoreCase(pm.qaStatuses(), status)) {
            return "QA";
        }
        if (containsIgnoreCase(pm.devStatuses(), status)) {
            return "DEV";
        }

        // Fallback: substring matching (для обратной совместимости)
        if (statusLower.contains("analysis") || statusLower.contains("анализ") || statusLower.contains("аналитик") ||
            typeLower.contains("analysis") || typeLower.contains("анализ") || typeLower.contains("аналитик")) {
            return "SA";
        }
        if (statusLower.contains("test") || statusLower.contains("qa") ||
            statusLower.contains("тест") || statusLower.contains("review") ||
            typeLower.contains("test") || typeLower.contains("qa") || typeLower.contains("тест") ||
            typeLower.contains("bug") || typeLower.contains("баг") || typeLower.contains("дефект")) {
            return "QA";
        }

        return "DEV";
    }

    /**
     * Проверяет, является ли статус "Done" (завершённым).
     * Использует конфигурацию для Story workflow.
     */
    public boolean isDone(String status, StatusMappingConfig teamOverride) {
        if (status == null) return false;

        StatusMappingConfig config = getEffectiveConfig(teamOverride);
        WorkflowConfig workflow = config.storyWorkflow();

        // Точное совпадение
        if (containsIgnoreCase(workflow.doneStatuses(), status)) {
            return true;
        }

        // Fallback: substring matching (для обратной совместимости)
        String s = status.toLowerCase();
        return s.contains("done") || s.contains("closed") || s.contains("resolved")
                || s.contains("завершен") || s.contains("готов") || s.contains("выполнен");
    }

    /**
     * Проверяет, является ли статус "In Progress".
     * Использует конфигурацию для Story workflow.
     */
    public boolean isInProgress(String status, StatusMappingConfig teamOverride) {
        if (status == null) return false;

        StatusMappingConfig config = getEffectiveConfig(teamOverride);
        WorkflowConfig workflow = config.storyWorkflow();

        // Точное совпадение
        if (containsIgnoreCase(workflow.inProgressStatuses(), status)) {
            return true;
        }

        // Fallback: substring matching (для обратной совместимости)
        String s = status.toLowerCase();
        return s.contains("progress") || s.contains("work") || s.contains("review")
                || s.contains("test") || s.contains("в работе") || s.contains("ревью");
    }

    /**
     * Проверяет, является ли статус эпика допустимым для rough estimate.
     * Эпик должен быть в TODO статусах epic workflow.
     */
    public boolean isAllowedForRoughEstimate(String status, StatusMappingConfig teamOverride) {
        if (status == null) return false;

        StatusMappingConfig config = getEffectiveConfig(teamOverride);
        WorkflowConfig workflow = config.epicWorkflow();

        return containsIgnoreCase(workflow.todoStatuses(), status);
    }

    private StatusCategory categorize(String status, WorkflowConfig workflow) {
        if (status == null) {
            log.warn("Status is null, defaulting to TODO");
            return StatusCategory.TODO;
        }

        // Точное совпадение (case-insensitive)
        if (containsIgnoreCase(workflow.doneStatuses(), status)) {
            return StatusCategory.DONE;
        }
        if (containsIgnoreCase(workflow.inProgressStatuses(), status)) {
            return StatusCategory.IN_PROGRESS;
        }
        if (containsIgnoreCase(workflow.todoStatuses(), status)) {
            return StatusCategory.TODO;
        }

        // Fallback: substring matching для обратной совместимости
        String s = status.toLowerCase();

        // Done keywords
        if (s.contains("done") || s.contains("closed") || s.contains("resolved")
                || s.contains("завершен") || s.contains("готов") || s.contains("выполнен")) {
            return StatusCategory.DONE;
        }

        // In Progress keywords
        if (s.contains("progress") || s.contains("work") || s.contains("review")
                || s.contains("test") || s.contains("develop") || s.contains("analysis")
                || s.contains("в работе") || s.contains("ревью") || s.contains("разработ")
                || s.contains("анализ") || s.contains("тест")) {
            return StatusCategory.IN_PROGRESS;
        }

        // Default to TODO with warning
        log.warn("Unknown status '{}', defaulting to TODO", status);
        return StatusCategory.TODO;
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null || value == null) {
            return false;
        }
        return list.stream().anyMatch(item -> item.equalsIgnoreCase(value));
    }

    private boolean matchesAny(String value, List<String> patterns) {
        if (patterns == null || value == null) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> value.contains(pattern.toLowerCase()));
    }
}
