package com.leadboard.calendar.dto;

import java.time.LocalDate;
import java.util.List;

public record WorkdaysResponseDto(
        LocalDate from,
        LocalDate to,
        String country,
        int totalDays,
        int workdays,
        int weekends,
        int holidays,
        List<LocalDate> workdayDates,
        List<HolidayDto> holidayList
) {}
