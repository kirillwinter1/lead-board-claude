package com.leadboard.poker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddStoryRequest(
    @NotBlank String title,
    @NotEmpty List<String> needsRoles,
    String existingStoryKey // if linking to existing Jira story
) {}
