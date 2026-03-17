package com.leadboard.metrics.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MetricsDataStatus(
    OffsetDateTime lastSyncCompletedAt,
    boolean syncInProgress,
    int issuesInScope,
    int issuesWithChangelog,
    BigDecimal dataCoveragePercent
) {}
