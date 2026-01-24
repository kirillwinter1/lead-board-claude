package com.leadboard.status;

import java.util.List;

/**
 * Конфигурация маппинга статусов и типов задач на фазы (SA/DEV/QA).
 */
public record PhaseMapping(
        List<String> saStatuses,
        List<String> devStatuses,
        List<String> qaStatuses,
        List<String> saIssueTypes,
        List<String> qaIssueTypes
) {
    public static PhaseMapping empty() {
        return new PhaseMapping(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Мержит текущую конфигурацию с override.
     * Если в override поле не null и не пустое — берётся из override, иначе из this.
     */
    public PhaseMapping merge(PhaseMapping override) {
        if (override == null) {
            return this;
        }
        return new PhaseMapping(
                (override.saStatuses() != null && !override.saStatuses().isEmpty())
                        ? override.saStatuses() : this.saStatuses(),
                (override.devStatuses() != null && !override.devStatuses().isEmpty())
                        ? override.devStatuses() : this.devStatuses(),
                (override.qaStatuses() != null && !override.qaStatuses().isEmpty())
                        ? override.qaStatuses() : this.qaStatuses(),
                (override.saIssueTypes() != null && !override.saIssueTypes().isEmpty())
                        ? override.saIssueTypes() : this.saIssueTypes(),
                (override.qaIssueTypes() != null && !override.qaIssueTypes().isEmpty())
                        ? override.qaIssueTypes() : this.qaIssueTypes()
        );
    }
}
