package com.leadboard.metrics.dto;

import java.time.LocalDate;

public record PeriodThroughput(
    LocalDate periodStart,
    LocalDate periodEnd,
    int epics,
    int stories,
    int subtasks,
    int total
) {}
