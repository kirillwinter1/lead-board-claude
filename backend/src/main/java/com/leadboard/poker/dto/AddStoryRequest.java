package com.leadboard.poker.dto;

import jakarta.validation.constraints.NotBlank;

public record AddStoryRequest(
    @NotBlank String title,
    boolean needsSa,
    boolean needsDev,
    boolean needsQa,
    String existingStoryKey // if linking to existing Jira story
) {}
