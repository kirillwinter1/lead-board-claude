package com.leadboard.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraTransition(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("to") TransitionTarget to
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransitionTarget(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name
    ) {}
}
