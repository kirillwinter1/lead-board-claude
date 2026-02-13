package com.leadboard.epic;

import com.leadboard.config.RoughEstimateProperties;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class EpicService {

    private final JiraIssueRepository issueRepository;
    private final RoughEstimateProperties roughEstimateProperties;
    private final WorkflowConfigService workflowConfigService;

    public EpicService(JiraIssueRepository issueRepository,
                       RoughEstimateProperties roughEstimateProperties,
                       WorkflowConfigService workflowConfigService) {
        this.issueRepository = issueRepository;
        this.roughEstimateProperties = roughEstimateProperties;
        this.workflowConfigService = workflowConfigService;
    }

    public RoughEstimateConfigDto getRoughEstimateConfig() {
        return new RoughEstimateConfigDto(
                roughEstimateProperties.isEnabled(),
                List.of(), // allowed statuses now determined dynamically by WorkflowConfigService
                roughEstimateProperties.getStepDays(),
                roughEstimateProperties.getMinDays(),
                roughEstimateProperties.getMaxDays()
        );
    }

    @Transactional
    public RoughEstimateResponseDto updateRoughEstimate(String epicKey, String role, RoughEstimateRequestDto request) {
        if (!roughEstimateProperties.isEnabled()) {
            throw new RoughEstimateException("Rough estimate feature is disabled");
        }

        // Validate role against configured workflow roles
        if (role == null) {
            throw new RoughEstimateException("Role is required");
        }
        List<String> validRoleCodes = workflowConfigService.getRoleCodesInPipelineOrder();
        if (validRoleCodes.stream().noneMatch(code -> code.equalsIgnoreCase(role))) {
            throw new RoughEstimateException("Invalid role: " + role + ". Must be one of: " +
                    String.join(", ", validRoleCodes));
        }

        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new RoughEstimateException("Epic not found: " + epicKey));

        // Validate it's an Epic
        if (!isEpic(epic.getIssueType())) {
            throw new RoughEstimateException("Issue " + epicKey + " is not an Epic");
        }

        // Validate status allows editing
        if (!workflowConfigService.isAllowedForRoughEstimate(epic.getStatus())) {
            throw new RoughEstimateException(
                    "Cannot edit rough estimate for Epic in status '" + epic.getStatus() + "'");
        }

        BigDecimal days = request.days();

        // Validate value
        if (days != null) {
            if (days.compareTo(roughEstimateProperties.getMinDays()) < 0) {
                throw new RoughEstimateException(
                        "Rough estimate must be >= " + roughEstimateProperties.getMinDays() + " days");
            }
            if (days.compareTo(roughEstimateProperties.getMaxDays()) > 0) {
                throw new RoughEstimateException(
                        "Rough estimate must be <= " + roughEstimateProperties.getMaxDays() + " days");
            }
        }

        // Update the specific role's rough estimate using dynamic method
        epic.setRoughEstimate(role.toUpperCase(), days);

        epic.setRoughEstimateUpdatedAt(OffsetDateTime.now());
        epic.setRoughEstimateUpdatedBy(request.updatedBy() != null ? request.updatedBy() : "anonymous");

        issueRepository.save(epic);

        return new RoughEstimateResponseDto(
                epic.getIssueKey(),
                role.toLowerCase(),
                days,
                epic.getRoughEstimates(),
                epic.getRoughEstimateUpdatedAt(),
                epic.getRoughEstimateUpdatedBy()
        );
    }

    private boolean isEpic(String issueType) {
        return workflowConfigService.isEpic(issueType);
    }
}
