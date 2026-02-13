package com.leadboard.status;

import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.service.WorkflowConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade adapter: delegates all calls to WorkflowConfigService.
 * Preserves the existing API so callers don't need immediate changes.
 * The teamOverride parameter is ignored — all config comes from DB now.
 */
@Service
public class StatusMappingService {

    private static final Logger log = LoggerFactory.getLogger(StatusMappingService.class);

    private final WorkflowConfigService workflowConfigService;

    public StatusMappingService(WorkflowConfigService workflowConfigService) {
        this.workflowConfigService = workflowConfigService;
    }

    /**
     * Returns a dummy default config for backward compatibility.
     * Callers should migrate to WorkflowConfigService directly.
     */
    public StatusMappingConfig getDefaultConfig() {
        // Build from DB-backed data
        List<String> epicTodo = workflowConfigService.getStatusNames(BoardCategory.EPIC, StatusCategory.NEW);
        List<String> epicReq = workflowConfigService.getStatusNames(BoardCategory.EPIC, StatusCategory.REQUIREMENTS);
        List<String> epicTodoAll = new java.util.ArrayList<>(epicTodo);
        epicTodoAll.addAll(epicReq);

        List<String> epicPlanned = workflowConfigService.getStatusNames(BoardCategory.EPIC, StatusCategory.PLANNED);
        List<String> epicInProgress = workflowConfigService.getStatusNames(BoardCategory.EPIC, StatusCategory.IN_PROGRESS);
        List<String> epicInProgressAll = new java.util.ArrayList<>(epicPlanned);
        epicInProgressAll.addAll(epicInProgress);

        List<String> epicDone = workflowConfigService.getStatusNames(BoardCategory.EPIC, StatusCategory.DONE);

        List<String> storyTodo = workflowConfigService.getStatusNames(BoardCategory.STORY, StatusCategory.NEW);
        List<String> storyInProgress = workflowConfigService.getStatusNames(BoardCategory.STORY, StatusCategory.IN_PROGRESS);
        List<String> storyDone = workflowConfigService.getStatusNames(BoardCategory.STORY, StatusCategory.DONE);

        List<String> subtaskTodo = workflowConfigService.getStatusNames(BoardCategory.SUBTASK, StatusCategory.NEW);
        List<String> subtaskInProgress = workflowConfigService.getStatusNames(BoardCategory.SUBTASK, StatusCategory.IN_PROGRESS);
        List<String> subtaskDone = workflowConfigService.getStatusNames(BoardCategory.SUBTASK, StatusCategory.DONE);

        return new StatusMappingConfig(
                new WorkflowConfig(epicTodoAll, epicInProgressAll, epicDone),
                new WorkflowConfig(storyTodo, storyInProgress, storyDone),
                new WorkflowConfig(subtaskTodo, subtaskInProgress, subtaskDone),
                PhaseMapping.empty(),
                epicInProgressAll,
                epicInProgress
        );
    }

    public StatusMappingConfig getEffectiveConfig(StatusMappingConfig teamOverride) {
        // teamOverride is ignored — all config from DB
        return getDefaultConfig();
    }

    public StatusCategory categorizeEpic(String status, StatusMappingConfig teamOverride) {
        StatusCategory cat = workflowConfigService.categorizeEpic(status);
        return normalizeForLegacy(cat);
    }

    public StatusCategory categorizeStory(String status, StatusMappingConfig teamOverride) {
        StatusCategory cat = workflowConfigService.categorizeStory(status);
        return normalizeForLegacy(cat);
    }

    public StatusCategory categorizeSubtask(String status, StatusMappingConfig teamOverride) {
        StatusCategory cat = workflowConfigService.categorizeSubtask(status);
        return normalizeForLegacy(cat);
    }

    public StatusCategory categorize(String status, String issueType, StatusMappingConfig teamOverride) {
        StatusCategory cat = workflowConfigService.categorize(status, issueType);
        return normalizeForLegacy(cat);
    }

    public String determinePhase(String status, String issueType, StatusMappingConfig teamOverride) {
        return workflowConfigService.determinePhase(status, issueType);
    }

    public boolean isDone(String status, StatusMappingConfig teamOverride) {
        // isDone checks Story workflow by default
        return workflowConfigService.categorizeStory(status) == StatusCategory.DONE;
    }

    public boolean isInProgress(String status, StatusMappingConfig teamOverride) {
        StatusCategory cat = workflowConfigService.categorizeStory(status);
        return cat == StatusCategory.IN_PROGRESS;
    }

    public boolean isAllowedForRoughEstimate(String status, StatusMappingConfig teamOverride) {
        return workflowConfigService.isAllowedForRoughEstimate(status);
    }

    public boolean isPlanningAllowed(String status, StatusMappingConfig teamOverride) {
        return workflowConfigService.isPlanningAllowed(status);
    }

    public boolean isTimeLoggingAllowed(String status, StatusMappingConfig teamOverride) {
        return workflowConfigService.isTimeLoggingAllowed(status);
    }

    public boolean isEpicInProgress(String status, StatusMappingConfig teamOverride) {
        return workflowConfigService.isEpicInProgress(status);
    }

    /**
     * Maps new fine-grained categories back to legacy TODO/IN_PROGRESS/DONE
     * so existing callers keep working.
     */
    private StatusCategory normalizeForLegacy(StatusCategory cat) {
        if (cat == null) return StatusCategory.TODO;
        return switch (cat) {
            case NEW, REQUIREMENTS, TODO -> StatusCategory.TODO;
            case PLANNED, IN_PROGRESS -> StatusCategory.IN_PROGRESS;
            case DONE -> StatusCategory.DONE;
        };
    }
}
