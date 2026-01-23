package com.leadboard.calendar.dto;

import java.time.LocalDate;

public record HolidayDto(
        LocalDate date,
        String name
) {}
