package com.leadboard.team.dto;

import com.leadboard.team.AbsenceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateAbsenceRequest(
        @NotNull AbsenceType absenceType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 500) String comment
) {
}
