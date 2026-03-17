package com.leadboard.team;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.calendar.dto.HolidayDto;
import com.leadboard.calendar.dto.WorkdaysResponseDto;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.team.dto.AbsenceDto;
import com.leadboard.team.dto.WorklogTimelineResponse;
import com.leadboard.team.dto.WorklogTimelineResponse.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorklogTimelineServiceTest {

    @Mock
    private TeamMemberRepository memberRepository;
    @Mock
    private IssueWorklogRepository worklogRepository;
    @Mock
    private AbsenceService absenceService;
    @Mock
    private WorkCalendarService workCalendarService;
    @Mock
    private WorkflowConfigService workflowConfigService;

    @InjectMocks
    private WorklogTimelineService service;

    private static final Long TEAM_ID = 1L;
    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 7); // 7 days

    private TeamMemberEntity devMember;
    private TeamMemberEntity qaMember;

    @BeforeEach
    void setUp() {
        devMember = createMember(1L, "acc-dev", "Alice", "DEV", 8.0);
        qaMember = createMember(2L, "acc-qa", "Bob", "QA", 6.0);
    }

    @Test
    void emptyTeam_returnsEmptyResponse() {
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of());

        WorklogTimelineResponse result = service.getWorklogTimeline(TEAM_ID, FROM, TO);

        assertNotNull(result);
        assertEquals(FROM, result.from());
        assertEquals(TO, result.to());
        assertEquals(7, result.days().size());
        assertTrue(result.members().isEmpty());
    }

    @Test
    void noWorklogs_allEntriesNull() {
        setupMembers(List.of(devMember));
        setupCalendar();
        when(worklogRepository.findDailyWorklogsByAuthors(anyList(), eq(FROM), eq(TO)))
                .thenReturn(List.of());
        when(absenceService.getAbsencesForTeam(TEAM_ID, FROM, TO)).thenReturn(List.of());
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("SA", "DEV", "QA"));

        WorklogTimelineResponse result = service.getWorklogTimeline(TEAM_ID, FROM, TO);

        assertEquals(1, result.members().size());
        MemberWorklog mw = result.members().get(0);
        assertEquals("Alice", mw.displayName());
        // All entries should have null hoursLogged
        assertTrue(mw.entries().stream().allMatch(e -> e.hoursLogged() == null));
        assertEquals(0, mw.summary().totalLogged());
    }

    @Test
    void worklogsAggregatedCorrectly() {
        setupMembers(List.of(devMember));
        setupCalendar();

        // Monday March 2 has 6h logged
        List<Object[]> rawWorklogs = new ArrayList<>();
        rawWorklogs.add(new Object[]{"acc-dev", Date.valueOf(LocalDate.of(2026, 3, 2)), 21600L});
        when(worklogRepository.findDailyWorklogsByAuthors(anyList(), eq(FROM), eq(TO)))
                .thenReturn(rawWorklogs);
        when(absenceService.getAbsencesForTeam(TEAM_ID, FROM, TO)).thenReturn(List.of());
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("DEV"));

        WorklogTimelineResponse result = service.getWorklogTimeline(TEAM_ID, FROM, TO);

        MemberWorklog mw = result.members().get(0);
        // Find March 2 entry
        DayEntry march2 = mw.entries().stream()
                .filter(e -> e.date().equals(LocalDate.of(2026, 3, 2)))
                .findFirst().orElseThrow();
        assertEquals(6.0, march2.hoursLogged());
        assertEquals(6.0, mw.summary().totalLogged());
    }

    @Test
    void absencesMarkedCorrectly() {
        setupMembers(List.of(devMember));
        setupCalendar();

        when(worklogRepository.findDailyWorklogsByAuthors(anyList(), eq(FROM), eq(TO)))
                .thenReturn(List.of());

        AbsenceDto vacation = new AbsenceDto(
                100L, 1L, AbsenceType.VACATION,
                LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 4),
                null, OffsetDateTime.now()
        );
        when(absenceService.getAbsencesForTeam(TEAM_ID, FROM, TO)).thenReturn(List.of(vacation));
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("DEV"));

        WorklogTimelineResponse result = service.getWorklogTimeline(TEAM_ID, FROM, TO);

        MemberWorklog mw = result.members().get(0);

        // March 3 and 4 should have absence type
        DayEntry march3 = mw.entries().stream()
                .filter(e -> e.date().equals(LocalDate.of(2026, 3, 3)))
                .findFirst().orElseThrow();
        assertEquals("VACATION", march3.absenceType());

        DayEntry march4 = mw.entries().stream()
                .filter(e -> e.date().equals(LocalDate.of(2026, 3, 4)))
                .findFirst().orElseThrow();
        assertEquals("VACATION", march4.absenceType());

        // Absent days should reduce capacity
        // 4 workdays (Mon-Fri minus holiday Thu) minus 2 vacation days (Tue-Wed) = 2 available
        assertEquals(2, mw.summary().workdaysInPeriod());
        assertEquals(16.0, mw.summary().capacityHours()); // 2 * 8h
    }

    @Test
    void dayTypesDetectedCorrectly() {
        setupMembers(List.of(devMember));
        setupCalendar(); // March 1 = Sun, March 7 = Sat; March 5 = holiday

        when(worklogRepository.findDailyWorklogsByAuthors(anyList(), eq(FROM), eq(TO)))
                .thenReturn(List.of());
        when(absenceService.getAbsencesForTeam(TEAM_ID, FROM, TO)).thenReturn(List.of());
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("DEV"));

        WorklogTimelineResponse result = service.getWorklogTimeline(TEAM_ID, FROM, TO);

        // March 1 (Sunday) = WEEKEND
        DayInfo march1 = result.days().stream()
                .filter(d -> d.date().equals(LocalDate.of(2026, 3, 1)))
                .findFirst().orElseThrow();
        assertEquals("WEEKEND", march1.dayType());

        // March 5 (Thursday) = HOLIDAY (mocked)
        DayInfo march5 = result.days().stream()
                .filter(d -> d.date().equals(LocalDate.of(2026, 3, 5)))
                .findFirst().orElseThrow();
        assertEquals("HOLIDAY", march5.dayType());

        // March 2 (Monday) = WORKDAY
        DayInfo march2 = result.days().stream()
                .filter(d -> d.date().equals(LocalDate.of(2026, 3, 2)))
                .findFirst().orElseThrow();
        assertEquals("WORKDAY", march2.dayType());
    }

    @Test
    void membersSortedByRolePipelineOrder() {
        setupMembers(List.of(qaMember, devMember)); // QA first in list
        setupCalendar();

        when(worklogRepository.findDailyWorklogsByAuthors(anyList(), eq(FROM), eq(TO)))
                .thenReturn(List.of());
        when(absenceService.getAbsencesForTeam(TEAM_ID, FROM, TO)).thenReturn(List.of());
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("SA", "DEV", "QA"));

        WorklogTimelineResponse result = service.getWorklogTimeline(TEAM_ID, FROM, TO);

        assertEquals(2, result.members().size());
        // DEV should come before QA in pipeline order
        assertEquals("DEV", result.members().get(0).role());
        assertEquals("QA", result.members().get(1).role());
    }

    @Test
    void capacityAndRatioCalculation() {
        setupMembers(List.of(devMember));
        setupCalendar();

        // Log 40h across 5 days (8h each)
        List<Object[]> rawWorklogs = new ArrayList<>();
        rawWorklogs.add(new Object[]{"acc-dev", Date.valueOf(LocalDate.of(2026, 3, 2)), 28800L}); // 8h
        rawWorklogs.add(new Object[]{"acc-dev", Date.valueOf(LocalDate.of(2026, 3, 3)), 28800L}); // 8h
        rawWorklogs.add(new Object[]{"acc-dev", Date.valueOf(LocalDate.of(2026, 3, 4)), 28800L}); // 8h
        rawWorklogs.add(new Object[]{"acc-dev", Date.valueOf(LocalDate.of(2026, 3, 5)), 28800L}); // 8h (but holiday)
        rawWorklogs.add(new Object[]{"acc-dev", Date.valueOf(LocalDate.of(2026, 3, 6)), 28800L}); // 8h
        when(worklogRepository.findDailyWorklogsByAuthors(anyList(), eq(FROM), eq(TO)))
                .thenReturn(rawWorklogs);
        when(absenceService.getAbsencesForTeam(TEAM_ID, FROM, TO)).thenReturn(List.of());
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("DEV"));

        WorklogTimelineResponse result = service.getWorklogTimeline(TEAM_ID, FROM, TO);

        MemberWorklog mw = result.members().get(0);
        assertEquals(40.0, mw.summary().totalLogged());
        // Workdays: Mon-Fri minus 1 holiday = 4 workdays
        assertEquals(4, mw.summary().workdaysInPeriod());
        assertEquals(32.0, mw.summary().capacityHours()); // 4 * 8h
        // Ratio: 40 / 32 * 100 = 125%
        assertEquals(125.0, mw.summary().ratio());
    }

    // ==================== Helpers ====================

    private TeamMemberEntity createMember(Long id, String accountId, String name, String role, double hoursPerDay) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(id);
        member.setJiraAccountId(accountId);
        member.setDisplayName(name);
        member.setRole(role);
        member.setHoursPerDay(BigDecimal.valueOf(hoursPerDay));
        member.setAvatarUrl(null);
        return member;
    }

    private void setupMembers(List<TeamMemberEntity> members) {
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(members);
    }

    /**
     * Setup calendar: March 1-7, 2026
     * March 1 (Sun) = weekend, March 7 (Sat) = weekend
     * March 5 (Thu) = holiday
     * March 2,3,4,6 = workdays
     */
    private void setupCalendar() {
        LocalDate holidayDate = LocalDate.of(2026, 3, 5);
        List<LocalDate> workdayDates = List.of(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 4),
                LocalDate.of(2026, 3, 6)
        );

        WorkdaysResponseDto calendarInfo = new WorkdaysResponseDto(
                FROM, TO, "RU",
                7, // totalDays
                4, // workdays
                2, // weekends
                1, // holidays
                workdayDates,
                List.of(new HolidayDto(holidayDate, "Test Holiday"))
        );
        when(workCalendarService.getWorkdaysInfo(FROM, TO)).thenReturn(calendarInfo);
    }
}
