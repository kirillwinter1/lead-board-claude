package com.leadboard.metrics.dto;

import java.math.BigDecimal;
import java.util.List;

public record DeliveryHealth(
    BigDecimal score,
    String grade,
    List<HealthDimension> dimensions,
    List<RiskAlert> alerts
) {
    public record HealthDimension(
        String name,
        BigDecimal score,
        BigDecimal weight,
        String status
    ) {}

    public record RiskAlert(
        String severity,
        String title,
        String description,
        String metric,
        String recommendation
    ) {}
}
