package com.leadboard.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
    @NotBlank(message = "Team name is required")
    @Size(max = 255, message = "Team name must not exceed 255 characters")
    String name,

    @Size(max = 255, message = "Jira team value must not exceed 255 characters")
    String jiraTeamValue,

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Color must be a valid hex color (e.g. #0052CC)")
    String color
) {}
