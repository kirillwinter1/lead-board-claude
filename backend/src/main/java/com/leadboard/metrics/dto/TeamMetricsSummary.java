package com.leadboard.metrics.dto;

import java.time.LocalDate;
import java.util.List;

public record TeamMetricsSummary(
    LocalDate from,
    LocalDate to,
    Long teamId,
    ThroughputResponse throughput,
    LeadTimeResponse leadTime,
    CycleTimeResponse cycleTime,
    List<TimeInStatusResponse> timeInStatuses,
    List<AssigneeMetrics> byAssignee
) {}
