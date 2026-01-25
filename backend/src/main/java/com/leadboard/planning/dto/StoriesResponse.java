package com.leadboard.planning.dto;

import com.leadboard.planning.StoryDependencyService;

import java.util.List;

public record StoriesResponse(
        List<StoryWithScore> stories,
        StoryDependencyService.DependencyGraph dependencyGraph
) {}
