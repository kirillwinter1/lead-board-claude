package com.leadboard.status;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Конфигурация маппинга статусов из application.yml.
 * Используется как системный дефолт, который можно переопределить на уровне команды.
 */
@Component
@ConfigurationProperties(prefix = "status-mapping")
public class StatusMappingProperties {

    private WorkflowProps epicWorkflow;
    private WorkflowProps storyWorkflow;
    private WorkflowProps subtaskWorkflow;
    private PhaseMappingProps phaseMapping;

    public WorkflowProps getEpicWorkflow() {
        return epicWorkflow;
    }

    public void setEpicWorkflow(WorkflowProps epicWorkflow) {
        this.epicWorkflow = epicWorkflow;
    }

    public WorkflowProps getStoryWorkflow() {
        return storyWorkflow;
    }

    public void setStoryWorkflow(WorkflowProps storyWorkflow) {
        this.storyWorkflow = storyWorkflow;
    }

    public WorkflowProps getSubtaskWorkflow() {
        return subtaskWorkflow;
    }

    public void setSubtaskWorkflow(WorkflowProps subtaskWorkflow) {
        this.subtaskWorkflow = subtaskWorkflow;
    }

    public PhaseMappingProps getPhaseMapping() {
        return phaseMapping;
    }

    public void setPhaseMapping(PhaseMappingProps phaseMapping) {
        this.phaseMapping = phaseMapping;
    }

    /**
     * Конвертирует properties в immutable config record.
     */
    public StatusMappingConfig toConfig() {
        return new StatusMappingConfig(
                toWorkflowConfig(epicWorkflow),
                toWorkflowConfig(storyWorkflow),
                toWorkflowConfig(subtaskWorkflow),
                toPhaseMappingConfig(phaseMapping)
        );
    }

    private WorkflowConfig toWorkflowConfig(WorkflowProps props) {
        if (props == null) {
            return WorkflowConfig.empty();
        }
        return new WorkflowConfig(
                props.getTodoStatuses() != null ? props.getTodoStatuses() : List.of(),
                props.getInProgressStatuses() != null ? props.getInProgressStatuses() : List.of(),
                props.getDoneStatuses() != null ? props.getDoneStatuses() : List.of()
        );
    }

    private PhaseMapping toPhaseMappingConfig(PhaseMappingProps props) {
        if (props == null) {
            return PhaseMapping.empty();
        }
        return new PhaseMapping(
                props.getSaStatuses() != null ? props.getSaStatuses() : List.of(),
                props.getDevStatuses() != null ? props.getDevStatuses() : List.of(),
                props.getQaStatuses() != null ? props.getQaStatuses() : List.of(),
                props.getSaIssueTypes() != null ? props.getSaIssueTypes() : List.of(),
                props.getQaIssueTypes() != null ? props.getQaIssueTypes() : List.of()
        );
    }

    // Nested classes for YAML binding
    public static class WorkflowProps {
        private List<String> todoStatuses;
        private List<String> inProgressStatuses;
        private List<String> doneStatuses;

        public List<String> getTodoStatuses() {
            return todoStatuses;
        }

        public void setTodoStatuses(List<String> todoStatuses) {
            this.todoStatuses = todoStatuses;
        }

        public List<String> getInProgressStatuses() {
            return inProgressStatuses;
        }

        public void setInProgressStatuses(List<String> inProgressStatuses) {
            this.inProgressStatuses = inProgressStatuses;
        }

        public List<String> getDoneStatuses() {
            return doneStatuses;
        }

        public void setDoneStatuses(List<String> doneStatuses) {
            this.doneStatuses = doneStatuses;
        }
    }

    public static class PhaseMappingProps {
        private List<String> saStatuses;
        private List<String> devStatuses;
        private List<String> qaStatuses;
        private List<String> saIssueTypes;
        private List<String> qaIssueTypes;

        public List<String> getSaStatuses() {
            return saStatuses;
        }

        public void setSaStatuses(List<String> saStatuses) {
            this.saStatuses = saStatuses;
        }

        public List<String> getDevStatuses() {
            return devStatuses;
        }

        public void setDevStatuses(List<String> devStatuses) {
            this.devStatuses = devStatuses;
        }

        public List<String> getQaStatuses() {
            return qaStatuses;
        }

        public void setQaStatuses(List<String> qaStatuses) {
            this.qaStatuses = qaStatuses;
        }

        public List<String> getSaIssueTypes() {
            return saIssueTypes;
        }

        public void setSaIssueTypes(List<String> saIssueTypes) {
            this.saIssueTypes = saIssueTypes;
        }

        public List<String> getQaIssueTypes() {
            return qaIssueTypes;
        }

        public void setQaIssueTypes(List<String> qaIssueTypes) {
            this.qaIssueTypes = qaIssueTypes;
        }
    }
}
