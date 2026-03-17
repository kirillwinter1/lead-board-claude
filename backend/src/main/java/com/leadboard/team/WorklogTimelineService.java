package com.leadboard.team;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.calendar.dto.HolidayDto;
import com.leadboard.calendar.dto.WorkdaysResponseDto;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.team.dto.AbsenceDto;
import com.leadboard.team.dto.WorklogTimelineResponse;
import com.leadboard.team.dto.WorklogTimelineResponse.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorklogTimelineService {

    private final TeamMemberRepository memberRepository;
    private final IssueWorklogRepository worklogRepository;
    private final AbsenceService absenceService;
    private final WorkCalendarService workCalendarService;
    private final WorkflowConfigService workflowConfigService;

    public WorklogTimelineService(
            TeamMemberRepository memberRepository,
            IssueWorklogRepository worklogRepository,
            AbsenceService absenceService,
            WorkCalendarService workCalendarService,
            WorkflowConfigService workflowConfigService
    ) {
        this.memberRepository = memberRepository;
        this.worklogRepository = worklogRepository;
        this.absenceService = absenceService;
        this.workCalendarService = workCalendarService;
        this.workflowConfigService = workflowConfigService;
    }

    @Transactional(readOnly = true)
    public WorklogTimelineResponse getWorklogTimeline(Long teamId, LocalDate from, LocalDate to) {
        // 1. Get team members
        List<TeamMemberEntity> members = memberRepository.findByTeamIdAndActiveTrue(teamId);
        if (members.isEmpty()) {
            return new WorklogTimelineResponse(from, to, buildDayInfosFallback(from, to), List.of());
        }

        // 2. Get calendar info (holidays, workdays)
        WorkdaysResponseDto calendarInfo = workCalendarService.getWorkdaysInfo(from, to);
        Set<LocalDate> holidayDates = calendarInfo.holidayList().stream()
                .map(HolidayDto::date)
                .collect(Collectors.toSet());
        Set<LocalDate> workdayDates = new HashSet<>(calendarInfo.workdayDates());

        // 3. Build day infos
        List<DayInfo> dayInfos = new ArrayList<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            String dayType;
            if (holidayDates.contains(current)) {
                dayType = "HOLIDAY";
            } else if (!workdayDates.contains(current)) {
                dayType = "WEEKEND";
            } else {
                dayType = "WORKDAY";
            }
            dayInfos.add(new DayInfo(current, dayType));
            current = current.plusDays(1);
        }

        // 4. Get worklogs aggregated by author + date
        List<String> accountIds = members.stream()
                .map(TeamMemberEntity::getJiraAccountId)
                .filter(Objects::nonNull)
                .toList();

        Map<String, Map<LocalDate, Double>> worklogMap = new HashMap<>();
        if (!accountIds.isEmpty()) {
            List<Object[]> rawWorklogs = worklogRepository.findDailyWorklogsByAuthors(accountIds, from, to);
            for (Object[] row : rawWorklogs) {
                String authorId = (String) row[0];
                LocalDate date = ((java.sql.Date) row[1]).toLocalDate();
                long totalSeconds = ((Number) row[2]).longValue();
                double hours = Math.round(totalSeconds / 360.0) / 10.0; // round to 1 decimal
                worklogMap
                        .computeIfAbsent(authorId, k -> new HashMap<>())
                        .put(date, hours);
            }
        }

        // 5. Get absences and build memberId -> date -> absenceType map
        List<AbsenceDto> absences = absenceService.getAbsencesForTeam(teamId, from, to);
        Map<Long, Map<LocalDate, String>> absenceTypeMap = new HashMap<>();
        for (AbsenceDto absence : absences) {
            Map<LocalDate, String> memberAbsences = absenceTypeMap
                    .computeIfAbsent(absence.memberId(), k -> new HashMap<>());
            LocalDate d = absence.startDate().isBefore(from) ? from : absence.startDate();
            LocalDate end = absence.endDate().isAfter(to) ? to : absence.endDate();
            while (!d.isAfter(end)) {
                memberAbsences.put(d, absence.absenceType().name());
                d = d.plusDays(1);
            }
        }

        // 6. Get role pipeline order for sorting
        List<String> roleOrder = workflowConfigService.getRoleCodesInPipelineOrder();

        // 7. Build member worklogs
        List<MemberWorklog> memberWorklogs = new ArrayList<>();
        for (TeamMemberEntity member : members) {
            String accountId = member.getJiraAccountId();
            Map<LocalDate, Double> memberLogs = worklogMap.getOrDefault(accountId, Map.of());
            Map<LocalDate, String> memberAbsenceTypes = absenceTypeMap.getOrDefault(member.getId(), Map.of());

            List<DayEntry> entries = new ArrayList<>();
            double totalLogged = 0;
            int workdaysAvailable = 0;

            for (DayInfo dayInfo : dayInfos) {
                LocalDate date = dayInfo.date();
                String absenceType = memberAbsenceTypes.get(date);
                Double hoursLogged = memberLogs.get(date);

                if (hoursLogged != null) {
                    totalLogged += hoursLogged;
                }

                // Count workdays available (workdays minus absence days)
                if ("WORKDAY".equals(dayInfo.dayType()) && absenceType == null) {
                    workdaysAvailable++;
                }

                entries.add(new DayEntry(date, hoursLogged, absenceType));
            }

            double hoursPerDay = member.getHoursPerDay().doubleValue();
            double capacityHours = workdaysAvailable * hoursPerDay;
            double ratio = capacityHours > 0
                    ? Math.round(totalLogged / capacityHours * 1000.0) / 10.0
                    : 0;

            memberWorklogs.add(new MemberWorklog(
                    member.getId(),
                    member.getDisplayName(),
                    member.getRole(),
                    member.getAvatarUrl(),
                    hoursPerDay,
                    entries,
                    new WorklogSummary(totalLogged, workdaysAvailable, capacityHours, ratio)
            ));
        }

        // 8. Sort by role pipeline order, then by name
        Map<String, Integer> roleIndex = new HashMap<>();
        for (int i = 0; i < roleOrder.size(); i++) {
            roleIndex.put(roleOrder.get(i), i);
        }
        memberWorklogs.sort(Comparator
                .<MemberWorklog, Integer>comparing(m -> roleIndex.getOrDefault(m.role(), Integer.MAX_VALUE))
                .thenComparing(m -> m.displayName() != null ? m.displayName() : "")
        );

        return new WorklogTimelineResponse(from, to, dayInfos, memberWorklogs);
    }

    /**
     * Fallback day info builder when team has no members (no calendar API call needed).
     * Uses simple weekend detection without holiday info.
     */
    private List<DayInfo> buildDayInfosFallback(LocalDate from, LocalDate to) {
        List<DayInfo> days = new ArrayList<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            DayOfWeek dow = current.getDayOfWeek();
            String dayType = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) ? "WEEKEND" : "WORKDAY";
            days.add(new DayInfo(current, dayType));
            current = current.plusDays(1);
        }
        return days;
    }
}
