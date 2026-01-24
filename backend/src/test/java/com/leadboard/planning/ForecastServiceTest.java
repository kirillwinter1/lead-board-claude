package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.planning.dto.EpicForecast;
import com.leadboard.planning.dto.EpicForecast.*;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.*;
import com.leadboard.team.dto.PlanningConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private TeamService teamService;

    @Mock
    private TeamMemberRepository memberRepository;

    @Mock
    private WorkCalendarService calendarService;

    private ForecastService forecastService;

    private static final Long TEAM_ID = 1L;

    @BeforeEach
    void setUp() {
        forecastService = new ForecastService(
                issueRepository,
                teamService,
                memberRepository,
                calendarService
        );

        // Default calendar behavior: add N workdays returns date + N days
        lenient().when(calendarService.addWorkdays(any(LocalDate.class), anyInt()))
                .thenAnswer(inv -> {
                    LocalDate date = inv.getArgument(0);
                    int days = inv.getArgument(1);
                    return date.plusDays(days);
                });
    }

    // ==================== Team Capacity Tests ====================

    @Nested
    class TeamCapacityTests {

        @Test
        void calculatesCapacityWithDefaultGradeCoefficients() {
            // Given: team with SA(6h), DEV(8h), QA(6h) all Middle grade
            setupDefaultPlanningConfig();
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.SA, Grade.MIDDLE, new BigDecimal("6")),
                    createMember(Role.DEV, Grade.MIDDLE, new BigDecimal("8")),
                    createMember(Role.QA, Grade.MIDDLE, new BigDecimal("6"))
            ));
            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of());

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: Middle coefficient = 1.0, so capacity = hours
            assertEquals(new BigDecimal("6.00"), response.teamCapacity().saHoursPerDay());
            assertEquals(new BigDecimal("8.00"), response.teamCapacity().devHoursPerDay());
            assertEquals(new BigDecimal("6.00"), response.teamCapacity().qaHoursPerDay());
        }

        @Test
        void seniorGradeIncreasesEffectiveCapacity() {
            // Given: Senior with 8h/day, coefficient 0.8
            setupDefaultPlanningConfig();
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.DEV, Grade.SENIOR, new BigDecimal("8"))
            ));
            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of());

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: 8 / 0.8 = 10 effective hours
            assertEquals(new BigDecimal("10.00"), response.teamCapacity().devHoursPerDay());
        }

        @Test
        void juniorGradeDecreasesEffectiveCapacity() {
            // Given: Junior with 8h/day, coefficient 1.5
            setupDefaultPlanningConfig();
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.DEV, Grade.JUNIOR, new BigDecimal("8"))
            ));
            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of());

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: 8 / 1.5 = 5.33 effective hours
            assertEquals(new BigDecimal("5.33"), response.teamCapacity().devHoursPerDay());
        }

        @Test
        void aggregatesCapacityFromMultipleMembers() {
            // Given: 2 DEVs with different grades
            setupDefaultPlanningConfig();
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.DEV, Grade.SENIOR, new BigDecimal("8")),  // 8/0.8 = 10
                    createMember(Role.DEV, Grade.MIDDLE, new BigDecimal("6"))   // 6/1.0 = 6
            ));
            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of());

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: 10 + 6 = 16 effective hours
            assertEquals(new BigDecimal("16.00"), response.teamCapacity().devHoursPerDay());
        }
    }

    // ==================== Remaining Work Tests ====================

    @Nested
    class RemainingWorkTests {

        @Test
        void usesRoughEstimateWhenAvailable() {
            // Given: Epic with rough estimates
            setupDefaultPlanningConfig();
            setupEmptyTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateSaDays(new BigDecimal("2"));
            epic.setRoughEstimateDevDays(new BigDecimal("10"));
            epic.setRoughEstimateQaDays(new BigDecimal("3"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: remaining = rough estimate * (1 + riskBuffer 0.2)
            EpicForecast forecast = response.epics().get(0);
            assertEquals(new BigDecimal("2.4"), forecast.remainingByRole().sa().days());
            assertEquals(new BigDecimal("12.0"), forecast.remainingByRole().dev().days());
            assertEquals(new BigDecimal("3.6"), forecast.remainingByRole().qa().days());
        }

        @Test
        void distributesOriginalEstimateWhenNoRoughEstimate() {
            // Given: Epic with only original estimate (100 hours remaining)
            setupDefaultPlanningConfig();
            setupEmptyTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setOriginalEstimateSeconds(100L * 3600); // 100 hours
            epic.setTimeSpentSeconds(0L);

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: distributed 10% SA, 70% DEV, 20% QA with risk buffer
            // Verify distribution is reasonable (exact values depend on rounding)
            EpicForecast forecast = response.epics().get(0);
            BigDecimal saDays = forecast.remainingByRole().sa().days();
            BigDecimal devDays = forecast.remainingByRole().dev().days();
            BigDecimal qaDays = forecast.remainingByRole().qa().days();

            // SA should be ~10% of total
            assertTrue(saDays.compareTo(new BigDecimal("1")) >= 0 && saDays.compareTo(new BigDecimal("2")) <= 0,
                    "SA days should be ~1.5, got: " + saDays);
            // DEV should be ~70% of total
            assertTrue(devDays.compareTo(new BigDecimal("9")) >= 0 && devDays.compareTo(new BigDecimal("12")) <= 0,
                    "DEV days should be ~10.5, got: " + devDays);
            // QA should be ~20% of total
            assertTrue(qaDays.compareTo(new BigDecimal("2.5")) >= 0 && qaDays.compareTo(new BigDecimal("4")) <= 0,
                    "QA days should be ~3, got: " + qaDays);
        }

        @Test
        void subtractsSpentTimeFromEstimate() {
            // Given: Epic with 100h estimate, 40h spent
            setupDefaultPlanningConfig();
            setupEmptyTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setOriginalEstimateSeconds(100L * 3600); // 100 hours
            epic.setTimeSpentSeconds(40L * 3600);          // 40 hours spent

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: remaining = 60h (less than 100h with no spent time)
            EpicForecast forecast = response.epics().get(0);
            BigDecimal totalRemaining = forecast.remainingByRole().sa().days()
                    .add(forecast.remainingByRole().dev().days())
                    .add(forecast.remainingByRole().qa().days());

            // Total should be ~9 days (60h/8h * 1.2 risk buffer)
            assertTrue(totalRemaining.compareTo(new BigDecimal("7")) >= 0 &&
                            totalRemaining.compareTo(new BigDecimal("11")) <= 0,
                    "Total remaining should be ~9 days, got: " + totalRemaining);
        }
    }

    // ==================== Confidence Tests ====================

    @Nested
    class ConfidenceTests {

        @Test
        void highConfidenceWhenRoughEstimateExists() {
            // Given: Epic with rough estimates and full team
            setupDefaultPlanningConfig();
            setupFullTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateSaDays(new BigDecimal("2"));
            epic.setRoughEstimateDevDays(new BigDecimal("10"));
            epic.setRoughEstimateQaDays(new BigDecimal("3"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then
            assertEquals(Confidence.HIGH, response.epics().get(0).confidence());
        }

        @Test
        void lowConfidenceWhenNoEstimates() {
            // Given: Epic without any estimates
            setupDefaultPlanningConfig();
            setupFullTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            // No rough estimates, no original estimate

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then
            assertEquals(Confidence.LOW, response.epics().get(0).confidence());
        }

        @Test
        void mediumConfidenceWhenMissingOneRole() {
            // Given: Epic with estimates but no QA in team
            setupDefaultPlanningConfig();
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.SA, Grade.MIDDLE, new BigDecimal("6")),
                    createMember(Role.DEV, Grade.MIDDLE, new BigDecimal("8"))
                    // No QA
            ));

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateSaDays(new BigDecimal("2"));
            epic.setRoughEstimateDevDays(new BigDecimal("10"));
            epic.setRoughEstimateQaDays(new BigDecimal("3"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then
            assertEquals(Confidence.MEDIUM, response.epics().get(0).confidence());
        }

        @Test
        void lowConfidenceWhenMissingTwoRoles() {
            // Given: Epic with estimates but only DEV in team
            setupDefaultPlanningConfig();
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.DEV, Grade.MIDDLE, new BigDecimal("8"))
                    // No SA, no QA
            ));

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateSaDays(new BigDecimal("2"));
            epic.setRoughEstimateDevDays(new BigDecimal("10"));
            epic.setRoughEstimateQaDays(new BigDecimal("3"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then
            assertEquals(Confidence.LOW, response.epics().get(0).confidence());
        }
    }

    // ==================== Due Date Delta Tests ====================

    @Nested
    class DueDateDeltaTests {

        @Test
        void deltaCalculatedWhenDueDateExists() {
            // Given: Epic with due date
            setupDefaultPlanningConfig();
            setupFullTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateDevDays(new BigDecimal("10"));
            epic.setDueDate(LocalDate.now().plusDays(20)); // Has due date

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: delta is calculated (not null)
            assertNotNull(response.epics().get(0).dueDateDeltaDays(),
                    "Delta should be calculated when due date exists");
            assertNotNull(response.epics().get(0).expectedDone(),
                    "Expected done date should be calculated");
        }

        @Test
        void negativeDeltaWhenAhead() {
            // Given: Due date is after expected done
            setupDefaultPlanningConfig();
            setupFullTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateDevDays(new BigDecimal("2")); // Will finish ~3 days from now
            epic.setDueDate(LocalDate.now().plusDays(30)); // Due in 30 days

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: negative delta means ahead of schedule
            assertNotNull(response.epics().get(0).dueDateDeltaDays());
            assertTrue(response.epics().get(0).dueDateDeltaDays() < 0,
                    "Expected negative delta (ahead), got: " + response.epics().get(0).dueDateDeltaDays());
        }

        @Test
        void nullDeltaWhenNoDueDate() {
            // Given: Epic without due date
            setupDefaultPlanningConfig();
            setupFullTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateDevDays(new BigDecimal("5"));
            // No due date

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then
            assertNull(response.epics().get(0).dueDateDeltaDays());
        }
    }

    // ==================== Phase Schedule Tests (Pipeline Model) ====================

    @Nested
    class PhaseScheduleTests {

        @Test
        void calculatesPhaseScheduleForAllRoles() {
            // Given
            setupDefaultPlanningConfig();
            setupFullTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateSaDays(new BigDecimal("2"));
            epic.setRoughEstimateDevDays(new BigDecimal("10"));
            epic.setRoughEstimateQaDays(new BigDecimal("3"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: all phases have schedules
            PhaseSchedule schedule = response.epics().get(0).phaseSchedule();
            assertNotNull(schedule);
            assertNotNull(schedule.sa());
            assertNotNull(schedule.dev());
            assertNotNull(schedule.qa());

            // SA starts first
            assertNotNull(schedule.sa().startDate());
            assertNotNull(schedule.sa().endDate());

            // DEV starts after SA offset (pipeline model)
            assertNotNull(schedule.dev().startDate());
            assertTrue(schedule.dev().startDate().isAfter(schedule.sa().startDate()) ||
                            schedule.dev().startDate().equals(schedule.sa().startDate()),
                    "DEV should start after or when SA starts");

            // QA starts after DEV offset
            assertNotNull(schedule.qa().startDate());
            assertTrue(schedule.qa().startDate().isAfter(schedule.dev().startDate()) ||
                            schedule.qa().startDate().equals(schedule.dev().startDate()),
                    "QA should start after or when DEV starts");
        }

        @Test
        void marksNoCapacityWhenRoleMissing() {
            // Given: No QA in team
            setupDefaultPlanningConfig();
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.SA, Grade.MIDDLE, new BigDecimal("6")),
                    createMember(Role.DEV, Grade.MIDDLE, new BigDecimal("8"))
            ));

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateSaDays(new BigDecimal("2"));
            epic.setRoughEstimateDevDays(new BigDecimal("10"));
            epic.setRoughEstimateQaDays(new BigDecimal("3"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: QA phase marked as noCapacity
            PhaseSchedule schedule = response.epics().get(0).phaseSchedule();
            assertFalse(schedule.sa().noCapacity());
            assertFalse(schedule.dev().noCapacity());
            assertTrue(schedule.qa().noCapacity(), "QA should be marked as noCapacity");
        }
    }

    // ==================== Multiple Epics Queue Tests ====================

    @Nested
    class MultipleEpicsTests {

        @Test
        void queuesEpicsSequentiallyByAutoScore() {
            // Given: Two epics, second has lower autoScore
            setupDefaultPlanningConfig();
            setupFullTeam();

            JiraIssueEntity epic1 = createEpic("TEST-1");
            epic1.setAutoScore(new BigDecimal("80"));
            epic1.setRoughEstimateDevDays(new BigDecimal("5"));

            JiraIssueEntity epic2 = createEpic("TEST-2");
            epic2.setAutoScore(new BigDecimal("60"));
            epic2.setRoughEstimateDevDays(new BigDecimal("5"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic1, epic2)); // Sorted by autoScore DESC

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: Second epic starts after first one's phase ends
            assertEquals(2, response.epics().size());
            LocalDate epic1DevEnd = response.epics().get(0).phaseSchedule().dev().endDate();
            LocalDate epic2DevStart = response.epics().get(1).phaseSchedule().dev().startDate();

            assertTrue(epic2DevStart.isAfter(epic1DevEnd) || epic2DevStart.equals(epic1DevEnd.plusDays(1)),
                    "Epic2 DEV should start after Epic1 DEV ends");
        }

        @Test
        void filtersEpicsByStatus() {
            // Given
            setupDefaultPlanningConfig();
            setupFullTeam();

            List<String> statuses = List.of("In Progress", "В работе");

            when(issueRepository.findByIssueTypeInAndTeamIdAndStatusInOrderByAutoScoreDesc(
                    anyList(), eq(TEAM_ID), eq(statuses)))
                    .thenReturn(List.of(createEpic("TEST-1")));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID, statuses);

            // Then: repository called with status filter
            verify(issueRepository).findByIssueTypeInAndTeamIdAndStatusInOrderByAutoScoreDesc(
                    anyList(), eq(TEAM_ID), eq(statuses));
            assertEquals(1, response.epics().size());
        }
    }

    // ==================== Helper Methods ====================

    private void setupDefaultPlanningConfig() {
        PlanningConfigDto config = new PlanningConfigDto(
                PlanningConfigDto.GradeCoefficients.defaults(),
                new BigDecimal("0.2"),
                null, // WIP limits
                PlanningConfigDto.StoryDuration.defaults()
        );
        when(teamService.getPlanningConfig(TEAM_ID)).thenReturn(config);
    }

    private void setupEmptyTeam() {
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of());
    }

    private void setupFullTeam() {
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                createMember(Role.SA, Grade.MIDDLE, new BigDecimal("6")),
                createMember(Role.DEV, Grade.MIDDLE, new BigDecimal("8")),
                createMember(Role.QA, Grade.MIDDLE, new BigDecimal("6"))
        ));
    }

    private TeamMemberEntity createMember(Role role, Grade grade, BigDecimal hoursPerDay) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setRole(role);
        member.setGrade(grade);
        member.setHoursPerDay(hoursPerDay);
        member.setActive(true);
        return member;
    }

    private JiraIssueEntity createEpic(String key) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setIssueType("Epic");
        epic.setSummary("Test Epic " + key);
        epic.setStatus("In Progress");
        epic.setTeamId(TEAM_ID);
        epic.setAutoScore(new BigDecimal("50"));
        epic.setManualPriorityBoost(0);
        return epic;
    }
}
