package com.leadboard.epic;

import com.leadboard.config.RoughEstimateProperties;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class EpicService {

    private final JiraIssueRepository issueRepository;
    private final RoughEstimateProperties roughEstimateProperties;

    public EpicService(JiraIssueRepository issueRepository, RoughEstimateProperties roughEstimateProperties) {
        this.issueRepository = issueRepository;
        this.roughEstimateProperties = roughEstimateProperties;
    }

    public RoughEstimateConfigDto getRoughEstimateConfig() {
        return new RoughEstimateConfigDto(
                roughEstimateProperties.isEnabled(),
                roughEstimateProperties.getAllowedEpicStatuses(),
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

        // Validate role
        if (role == null || (!role.equalsIgnoreCase("sa") && !role.equalsIgnoreCase("dev") && !role.equalsIgnoreCase("qa"))) {
            throw new RoughEstimateException("Invalid role: " + role + ". Must be one of: sa, dev, qa");
        }

        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new RoughEstimateException("Epic not found: " + epicKey));

        // Validate it's an Epic
        if (!isEpic(epic.getIssueType())) {
            throw new RoughEstimateException("Issue " + epicKey + " is not an Epic");
        }

        // Validate status allows editing
        if (!roughEstimateProperties.isStatusAllowed(epic.getStatus())) {
            throw new RoughEstimateException(
                    "Cannot edit rough estimate for Epic in status '" + epic.getStatus() +
                    "'. Allowed statuses: " + roughEstimateProperties.getAllowedEpicStatuses());
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

        // Update the specific role's rough estimate
        switch (role.toLowerCase()) {
            case "sa":
                epic.setRoughEstimateSaDays(days);
                break;
            case "dev":
                epic.setRoughEstimateDevDays(days);
                break;
            case "qa":
                epic.setRoughEstimateQaDays(days);
                break;
        }

        epic.setRoughEstimateUpdatedAt(OffsetDateTime.now());
        epic.setRoughEstimateUpdatedBy(request.updatedBy() != null ? request.updatedBy() : "anonymous");

        issueRepository.save(epic);

        return new RoughEstimateResponseDto(
                epic.getIssueKey(),
                role.toLowerCase(),
                days,
                epic.getRoughEstimateSaDays(),
                epic.getRoughEstimateDevDays(),
                epic.getRoughEstimateQaDays(),
                epic.getRoughEstimateUpdatedAt(),
                epic.getRoughEstimateUpdatedBy()
        );
    }

    private boolean isEpic(String issueType) {
        return "Epic".equalsIgnoreCase(issueType) || "Эпик".equalsIgnoreCase(issueType);
    }
}
