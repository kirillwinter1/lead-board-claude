package com.leadboard.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
    @NotBlank(message = "Team name is required")
    @Size(max = 255, message = "Team name must not exceed 255 characters")
    String name,

    @Size(max = 255, message = "Jira team value must not exceed 255 characters")
    String jiraTeamValue
) {}
