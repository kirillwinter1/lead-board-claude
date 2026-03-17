package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.metrics.dto.VelocityResponse;
import com.leadboard.metrics.repository.MetricsQueryRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VelocityServiceTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private WorkCalendarService calendarService;

    @Mock
    private MetricsQueryRepository metricsQueryRepository;

    private VelocityService service;

    @BeforeEach
    void setUp() {
        service = new VelocityService(teamMemberRepository, calendarService, metricsQueryRepository);
    }

    @Test
    void calculateVelocity_multiWeekIssue_distributesProportionally() {
        // Given: 1 member, 8h/day capacity
        TeamMemberEntity member = new TeamMemberEntity();
        member.setHoursPerDay(new BigDecimal("8"));
        member.setActive(true);
        when(teamMemberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(member));

        // Issue: started Mon Jan 6, done Mon Jan 20 = 14 days total
        // 80 hours logged (10 days * 8h)
        Object[] row = new Object[]{
            80L * 3600, // time_spent_seconds
            java.sql.Date.valueOf("2025-01-06"), // started
            java.sql.Date.valueOf("2025-01-20")  // done
        };

        when(metricsQueryRepository.getVelocityData(eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(Collections.singletonList(row));

        when(calendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class))).thenReturn(5);

        // When
        LocalDate from = LocalDate.of(2025, 1, 6);
        LocalDate to = LocalDate.of(2025, 1, 26);
        VelocityResponse result = service.calculateVelocity(1L, from, to);

        // Then: hours should be distributed across 2 weeks, not all in one
        assertNotNull(result);
        assertTrue(result.byWeek().size() >= 2);

        // No single week should have all 80 hours
        for (var week : result.byWeek()) {
            assertTrue(week.loggedHours().compareTo(new BigDecimal("80")) < 0,
                    "Week " + week.weekStart() + " has " + week.loggedHours() + "h — should be distributed");
        }
    }

    @Test
    void calculateVelocity_singleWeekIssue_allInOneWeek() {
        // Given
        TeamMemberEntity member = new TeamMemberEntity();
        member.setHoursPerDay(new BigDecimal("8"));
        member.setActive(true);
        when(teamMemberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(member));

        // Issue: started and done in same week
        Object[] row = new Object[]{
            16L * 3600, // 16 hours
            java.sql.Date.valueOf("2025-01-06"), // started
            java.sql.Date.valueOf("2025-01-08")  // done (same week)
        };

        when(metricsQueryRepository.getVelocityData(eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(Collections.singletonList(row));

        when(calendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class))).thenReturn(5);

        // When
        LocalDate from = LocalDate.of(2025, 1, 6);
        LocalDate to = LocalDate.of(2025, 1, 12);
        VelocityResponse result = service.calculateVelocity(1L, from, to);

        // Then: all hours in one week
        assertNotNull(result);
        BigDecimal totalLogged = result.byWeek().stream()
                .map(VelocityResponse.WeeklyVelocity::loggedHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(totalLogged.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void calculateVelocity_nullStartedAt_fallbackToDoneWeek() {
        // Given
        TeamMemberEntity member = new TeamMemberEntity();
        member.setHoursPerDay(new BigDecimal("8"));
        member.setActive(true);
        when(teamMemberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(member));

        // Issue with no started_at — should fall back to done_at week
        Object[] row = new Object[]{
            24L * 3600, // 24 hours
            null,       // started (null)
            java.sql.Date.valueOf("2025-01-15")  // done (Wed of week Jan 13)
        };

        when(metricsQueryRepository.getVelocityData(eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(Collections.singletonList(row));

        when(calendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class))).thenReturn(5);

        // When
        LocalDate from = LocalDate.of(2025, 1, 6);
        LocalDate to = LocalDate.of(2025, 1, 19);
        VelocityResponse result = service.calculateVelocity(1L, from, to);

        // Then: all hours attributed to week of Jan 13 (Monday)
        assertNotNull(result);
        // Find week starting Jan 13
        var jan13Week = result.byWeek().stream()
                .filter(w -> w.weekStart().equals(LocalDate.of(2025, 1, 13)))
                .findFirst();
        assertTrue(jan13Week.isPresent(), "Should have week starting Jan 13");
    }

    @Test
    void calculateVelocity_noData_returnsZeroLogged() {
        // Given
        TeamMemberEntity member = new TeamMemberEntity();
        member.setHoursPerDay(new BigDecimal("8"));
        member.setActive(true);
        when(teamMemberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(member));
        when(metricsQueryRepository.getVelocityData(eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(calendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class))).thenReturn(5);

        // When
        VelocityResponse result = service.calculateVelocity(1L,
                LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 12));

        // Then
        assertNotNull(result);
        assertEquals(0, result.totalLoggedHours().compareTo(BigDecimal.ZERO));
    }
}
