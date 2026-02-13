package com.leadboard.poker.dto;

import java.util.List;
import java.util.Map;

public record EpicStoryResponse(
    String storyKey,
    String summary,
    String status,
    List<String> subtaskRoles,
    Map<String, Integer> roleEstimates
) {}
