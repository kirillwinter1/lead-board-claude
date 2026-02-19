package com.leadboard.team.dto;

import com.leadboard.team.AbsenceType;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateAbsenceRequest(
        AbsenceType absenceType,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 500) String comment
) {
}
