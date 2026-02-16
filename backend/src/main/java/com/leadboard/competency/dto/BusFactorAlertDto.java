package com.leadboard.competency.dto;

import java.util.List;

public record BusFactorAlertDto(
        String componentName,
        String severity,
        int expertCount,
        List<String> experts
) {}
