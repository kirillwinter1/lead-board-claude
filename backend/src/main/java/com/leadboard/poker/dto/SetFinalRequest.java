package com.leadboard.poker.dto;

import java.util.Map;

public record SetFinalRequest(
    Long storyId,
    Map<String, Integer> finalEstimates
) {}
