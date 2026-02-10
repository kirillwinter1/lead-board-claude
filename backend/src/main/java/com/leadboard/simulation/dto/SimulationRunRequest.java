package com.leadboard.simulation.dto;

import java.time.LocalDate;

public record SimulationRunRequest(
        Long teamId,
        LocalDate date
) {}
