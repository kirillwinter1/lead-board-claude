package com.leadboard.team.dto;

import java.time.LocalDate;
import java.util.List;

public record WorklogTimelineResponse(
        LocalDate from,
        LocalDate to,
        List<DayInfo> days,
        List<MemberWorklog> members
) {
    public record DayInfo(
            LocalDate date,
            String dayType  // "WORKDAY", "WEEKEND", "HOLIDAY"
    ) {}

    public record MemberWorklog(
            Long memberId,
            String displayName,
            String role,
            String avatarUrl,
            double hoursPerDay,
            List<DayEntry> entries,
            WorklogSummary summary
    ) {}

    public record DayEntry(
            LocalDate date,
            Double hoursLogged,    // null if no worklog
            String absenceType     // null if not absent; "VACATION", "SICK_LEAVE", etc.
    ) {}

    public record WorklogSummary(
            double totalLogged,
            int workdaysInPeriod,    // workdays minus absence days for this member
            double capacityHours,
            double ratio             // totalLogged / capacityHours as percentage 0-100+
    ) {}
}
