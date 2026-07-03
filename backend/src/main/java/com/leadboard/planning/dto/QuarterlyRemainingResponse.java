package com.leadboard.planning.dto;

import java.util.Map;

/**
 * F86: Remaining-work overview for every epic the auto-planner returned for a team,
 * keyed by epic key. Consumed by the Quarterly Planning board to surface how much
 * work is left on needs-planning epics (now and at the selected quarter's start).
 */
public record QuarterlyRemainingResponse(
        String quarter,
        Long teamId,
        Map<String, EpicRemainingDto> epics
) {}
