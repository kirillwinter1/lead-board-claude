package com.leadboard.poker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record AddStoryRequest(
    @NotBlank String title,
    @NotEmpty List<String> needsRoles,

    // If linking to an existing Jira story (createInJira=false); null when a new
    // story is being created in Jira (the controller fills this in afterwards
    // with the key Jira itself returned).
    //
    // SECURITY (SECURITY_AUDIT.md #4): this value flows unmodified into
    // JiraClient.getSubtasks() where it is interpolated into a JQL "parent = ..."
    // clause. Without strict validation here, a crafted value such as
    // "X OR project = SECRET" would let an authenticated user read/write
    // arbitrary issues via Jira. @Pattern rejects anything that isn't a
    // well-formed Jira issue key; null is allowed (Bean Validation treats null
    // as valid for @Pattern) since the field is optional.
    @Pattern(
            regexp = "^[A-Z][A-Z0-9]+-\\d+$",
            message = "existingStoryKey must be a valid Jira issue key, e.g. ABC-123"
    )
    String existingStoryKey
) {}
