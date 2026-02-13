package com.leadboard.config.service;

import com.leadboard.config.dto.*;
import com.leadboard.config.entity.BoardCategory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates workflow configuration for consistency and completeness.
 */
@Service
public class MappingValidationService {

    public ValidationResult validate(
            List<WorkflowRoleDto> roles,
            List<IssueTypeMappingDto> issueTypes,
            List<StatusMappingDto> statuses,
            List<LinkTypeMappingDto> linkTypes
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateRoles(roles, errors, warnings);
        validateIssueTypes(issueTypes, roles, errors, warnings);
        validateStatuses(statuses, errors, warnings);
        validateLinkTypes(linkTypes, warnings);

        return ValidationResult.withIssues(errors, warnings);
    }

    private void validateRoles(List<WorkflowRoleDto> roles, List<String> errors, List<String> warnings) {
        if (roles == null || roles.isEmpty()) {
            errors.add("At least one workflow role is required");
            return;
        }

        long defaultCount = roles.stream().filter(WorkflowRoleDto::isDefault).count();
        if (defaultCount == 0) {
            errors.add("At least one role must be marked as default");
        }
        if (defaultCount > 1) {
            warnings.add("Multiple roles are marked as default; only the first will be used");
        }

        // Check unique codes
        Set<String> codes = new HashSet<>();
        for (WorkflowRoleDto role : roles) {
            if (role.code() == null || role.code().isBlank()) {
                errors.add("Role code cannot be empty");
            } else if (!codes.add(role.code().toUpperCase())) {
                errors.add("Duplicate role code: " + role.code());
            }
        }

        // Check unique sort orders
        Set<Integer> orders = new HashSet<>();
        for (WorkflowRoleDto role : roles) {
            if (!orders.add(role.sortOrder())) {
                warnings.add("Duplicate sort order " + role.sortOrder() + " for role " + role.code());
            }
        }
    }

    private void validateIssueTypes(List<IssueTypeMappingDto> issueTypes, List<WorkflowRoleDto> roles,
                                     List<String> errors, List<String> warnings) {
        if (issueTypes == null || issueTypes.isEmpty()) {
            errors.add("At least one issue type mapping is required");
            return;
        }

        boolean hasEpic = issueTypes.stream().anyMatch(t -> t.boardCategory() == BoardCategory.EPIC);
        boolean hasStory = issueTypes.stream().anyMatch(t -> t.boardCategory() == BoardCategory.STORY);
        boolean hasSubtask = issueTypes.stream().anyMatch(t -> t.boardCategory() == BoardCategory.SUBTASK);

        if (!hasEpic) errors.add("At least one EPIC issue type mapping is required");
        if (!hasStory) errors.add("At least one STORY issue type mapping is required");
        if (!hasSubtask) errors.add("At least one SUBTASK issue type mapping is required");

        // Check that SUBTASK types have role codes
        Set<String> roleCodes = roles != null
                ? roles.stream().map(WorkflowRoleDto::code).collect(Collectors.toSet())
                : Set.of();

        for (IssueTypeMappingDto type : issueTypes) {
            if (type.boardCategory() == BoardCategory.SUBTASK) {
                if (type.workflowRoleCode() == null || type.workflowRoleCode().isBlank()) {
                    warnings.add("SUBTASK type '" + type.jiraTypeName() + "' has no workflow role assigned");
                } else if (!roleCodes.isEmpty() && !roleCodes.contains(type.workflowRoleCode())) {
                    errors.add("SUBTASK type '" + type.jiraTypeName() + "' references unknown role: " + type.workflowRoleCode());
                }
            }
        }
    }

    private void validateStatuses(List<StatusMappingDto> statuses, List<String> errors, List<String> warnings) {
        if (statuses == null || statuses.isEmpty()) {
            warnings.add("No status mappings configured; fallback substring matching will be used");
            return;
        }

        // Check that each board category has at least one DONE status
        Map<BoardCategory, List<StatusMappingDto>> byCategory = statuses.stream()
                .collect(Collectors.groupingBy(StatusMappingDto::issueCategory));

        for (BoardCategory cat : List.of(BoardCategory.EPIC, BoardCategory.STORY, BoardCategory.SUBTASK)) {
            List<StatusMappingDto> catStatuses = byCategory.getOrDefault(cat, List.of());
            boolean hasDone = catStatuses.stream()
                    .anyMatch(s -> s.statusCategory() == com.leadboard.status.StatusCategory.DONE);
            if (!hasDone && !catStatuses.isEmpty()) {
                warnings.add("Category " + cat + " has no DONE status mapped");
            }
        }
    }

    private void validateLinkTypes(List<LinkTypeMappingDto> linkTypes, List<String> warnings) {
        if (linkTypes == null || linkTypes.isEmpty()) {
            warnings.add("No link type mappings configured; link categorization will use fallback");
        }
    }
}
