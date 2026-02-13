package com.leadboard.team;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateTeamMemberRequest(
    @Size(max = 255, message = "Display name must not exceed 255 characters")
    String displayName,

    String role,

    Grade grade,

    @DecimalMin(value = "0.1", message = "Hours per day must be greater than 0")
    @DecimalMax(value = "12.0", message = "Hours per day must not exceed 12")
    BigDecimal hoursPerDay
) {}
