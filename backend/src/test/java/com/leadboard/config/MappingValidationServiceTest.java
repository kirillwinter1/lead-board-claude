package com.leadboard.config;

import com.leadboard.config.dto.*;
import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.entity.LinkCategory;
import com.leadboard.config.service.MappingValidationService;
import com.leadboard.status.StatusCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MappingValidationServiceTest {

    private MappingValidationService service;

    @BeforeEach
    void setUp() {
        service = new MappingValidationService();
    }

    @Test
    void validConfig_returnsNoErrors() {
        List<WorkflowRoleDto> roles = List.of(
                new WorkflowRoleDto(1L, "SA", "Analyst", null, 1, false),
                new WorkflowRoleDto(2L, "DEV", "Developer", null, 2, true),
                new WorkflowRoleDto(3L, "QA", "Tester", null, 3, false)
        );

        List<IssueTypeMappingDto> issueTypes = List.of(
                new IssueTypeMappingDto(1L, "Epic", BoardCategory.EPIC, null),
                new IssueTypeMappingDto(2L, "Story", BoardCategory.STORY, null),
                new IssueTypeMappingDto(3L, "Аналитика", BoardCategory.SUBTASK, "SA"),
                new IssueTypeMappingDto(4L, "Разработка", BoardCategory.SUBTASK, "DEV")
        );

        List<StatusMappingDto> statuses = List.of(
                new StatusMappingDto(1L, "Done", BoardCategory.EPIC, StatusCategory.DONE, null, 10, 100),
                new StatusMappingDto(2L, "Done", BoardCategory.STORY, StatusCategory.DONE, null, 10, 100),
                new StatusMappingDto(3L, "Done", BoardCategory.SUBTASK, StatusCategory.DONE, null, 10, 100)
        );

        List<LinkTypeMappingDto> linkTypes = List.of(
                new LinkTypeMappingDto(1L, "Blocks", LinkCategory.BLOCKS)
        );

        ValidationResult result = service.validate(roles, issueTypes, statuses, linkTypes);

        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void emptyRoles_returnsError() {
        ValidationResult result = service.validate(List.of(), List.of(), List.of(), List.of());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("role")));
    }

    @Test
    void noDefaultRole_returnsError() {
        List<WorkflowRoleDto> roles = List.of(
                new WorkflowRoleDto(1L, "SA", "Analyst", null, 1, false)
        );

        ValidationResult result = service.validate(roles, List.of(), List.of(), List.of());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("default")));
    }

    @Test
    void missingIssueTypeCategories_returnsErrors() {
        List<WorkflowRoleDto> roles = List.of(
                new WorkflowRoleDto(1L, "DEV", "Dev", null, 1, true)
        );

        List<IssueTypeMappingDto> issueTypes = List.of(
                new IssueTypeMappingDto(1L, "Story", BoardCategory.STORY, null)
        );

        ValidationResult result = service.validate(roles, issueTypes, List.of(), List.of());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("EPIC")));
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("SUBTASK")));
    }

    @Test
    void subtaskWithoutRole_returnsWarning() {
        List<WorkflowRoleDto> roles = List.of(
                new WorkflowRoleDto(1L, "DEV", "Dev", null, 1, true)
        );

        List<IssueTypeMappingDto> issueTypes = List.of(
                new IssueTypeMappingDto(1L, "Epic", BoardCategory.EPIC, null),
                new IssueTypeMappingDto(2L, "Story", BoardCategory.STORY, null),
                new IssueTypeMappingDto(3L, "Sub-task", BoardCategory.SUBTASK, null)
        );

        ValidationResult result = service.validate(roles, issueTypes, List.of(), List.of());

        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Sub-task") && w.contains("no workflow role")));
    }

    @Test
    void subtaskWithUnknownRole_returnsError() {
        List<WorkflowRoleDto> roles = List.of(
                new WorkflowRoleDto(1L, "DEV", "Dev", null, 1, true)
        );

        List<IssueTypeMappingDto> issueTypes = List.of(
                new IssueTypeMappingDto(1L, "Epic", BoardCategory.EPIC, null),
                new IssueTypeMappingDto(2L, "Story", BoardCategory.STORY, null),
                new IssueTypeMappingDto(3L, "Тестирование", BoardCategory.SUBTASK, "QA")
        );

        ValidationResult result = service.validate(roles, issueTypes, List.of(), List.of());

        assertTrue(result.errors().stream().anyMatch(e -> e.contains("unknown role") && e.contains("QA")));
    }

    @Test
    void duplicateRoleCode_returnsError() {
        List<WorkflowRoleDto> roles = List.of(
                new WorkflowRoleDto(1L, "DEV", "Dev 1", null, 1, true),
                new WorkflowRoleDto(2L, "DEV", "Dev 2", null, 2, false)
        );

        ValidationResult result = service.validate(roles, List.of(), List.of(), List.of());

        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Duplicate role code")));
    }

    @Test
    void noDoneStatus_returnsWarning() {
        List<WorkflowRoleDto> roles = List.of(
                new WorkflowRoleDto(1L, "DEV", "Dev", null, 1, true)
        );

        List<StatusMappingDto> statuses = List.of(
                new StatusMappingDto(1L, "New", BoardCategory.EPIC, StatusCategory.NEW, null, 1, 0)
        );

        ValidationResult result = service.validate(roles, List.of(), statuses, List.of());

        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("EPIC") && w.contains("no DONE")));
    }
}
