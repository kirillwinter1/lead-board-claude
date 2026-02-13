package com.leadboard.config.dto;

import java.util.List;

public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
) {
    public static ValidationResult ok() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult withIssues(List<String> errors, List<String> warnings) {
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
}
