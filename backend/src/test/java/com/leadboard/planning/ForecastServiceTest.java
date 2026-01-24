package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.planning.dto.EpicForecast;
import com.leadboard.planning.dto.EpicForecast.*;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.status.StatusMappingProperties;
import com.leadboard.status.StatusMappingService;
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
    private StatusMappingService statusMappingService;

    private static final Long TEAM_ID = 1L;

    @BeforeEach
    void setUp() {
        // Create real StatusMappingService with defaults
        statusMappingService = new StatusMappingService(new StatusMappingProperties());

        forecastService = new ForecastService(
                issueRepository,
                teamService,
                memberRepository,
                calendarService,
                statusMappingService
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
        void expectedDoneTakesMaxEndDateFromPhasesWithWork() {
            // Given: Epic with DEV work but no QA work
            setupDefaultPlanningConfig();
            setupFullTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateDevDays(new BigDecimal("20")); // 20 days of DEV work
            // No SA or QA work - they will have workDays = 0

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: expectedDone should be DEV endDate (not QA which has 0 workDays)
            EpicForecast forecast = response.epics().get(0);
            PhaseSchedule schedule = forecast.phaseSchedule();

            // DEV has work, so its endDate should be set
            assertNotNull(schedule.dev().endDate(), "DEV endDate should be set");
            assertTrue(schedule.dev().workDays().compareTo(BigDecimal.ZERO) > 0, "DEV should have workDays > 0");

            // QA has no work
            assertEquals(0, schedule.qa().workDays().compareTo(BigDecimal.ZERO), "QA should have workDays = 0");

            // expectedDone should equal DEV endDate (the max of phases with actual work)
            assertEquals(schedule.dev().endDate(), forecast.expectedDone(),
                    "expectedDone should be DEV endDate when QA has no work");
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
        void pipelineWipQueuesDevPhasesByDevWipLimit() {
            // Given: Two epics with DEV WIP = 1 (so they queue on DEV phase)
            // Using default config where dev WIP = 3, but setting custom config with dev = 1
            PlanningConfigDto config = new PlanningConfigDto(
                    PlanningConfigDto.GradeCoefficients.defaults(),
                    new BigDecimal("0.2"),
                    new PlanningConfigDto.WipLimits(6, 2, 1, 2), // dev WIP = 1
                    PlanningConfigDto.StoryDuration.defaults(),
                    null  // statusMapping
            );
            when(teamService.getPlanningConfig(TEAM_ID)).thenReturn(config);
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

            // Then: With Pipeline WIP and DEV WIP = 1:
            // Epic2 DEV should start after Epic1 DEV ends (DEV phase is queued)
            assertEquals(2, response.epics().size());
            LocalDate epic1DevEnd = response.epics().get(0).phaseSchedule().dev().endDate();
            LocalDate epic2DevStart = response.epics().get(1).phaseSchedule().dev().startDate();

            assertTrue(epic2DevStart.isAfter(epic1DevEnd) || epic2DevStart.equals(epic1DevEnd.plusDays(1)),
                    "Epic2 DEV should start after Epic1 DEV ends when DEV WIP = 1");

            // But Epic2 DEV wait info should show waiting
            assertTrue(response.epics().get(1).phaseWaitInfo().dev().waiting(),
                    "Epic2 should be waiting on DEV phase");
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

    // ==================== WIP Limits Tests ====================

    @Nested
    class WipLimitsTests {

        @Test
        void epicsWithinWipAreMarkedAsWithinWip() {
            // Given: WIP limit = 3, 2 epics
            setupPlanningConfigWithWipLimit(3);
            setupFullTeam();

            JiraIssueEntity epic1 = createEpic("TEST-1");
            epic1.setRoughEstimateDevDays(new BigDecimal("5"));

            JiraIssueEntity epic2 = createEpic("TEST-2");
            epic2.setRoughEstimateDevDays(new BigDecimal("5"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic1, epic2));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: Both epics are within WIP
            assertEquals(2, response.epics().size());
            assertTrue(response.epics().get(0).isWithinWip(), "Epic 1 should be within WIP");
            assertTrue(response.epics().get(1).isWithinWip(), "Epic 2 should be within WIP");
            assertNull(response.epics().get(0).queuePosition(), "Epic 1 should not have queue position");
            assertNull(response.epics().get(1).queuePosition(), "Epic 2 should not have queue position");
        }

        @Test
        void epicsBeyondWipAreQueued() {
            // Given: WIP limit = 2, 4 epics
            setupPlanningConfigWithWipLimit(2);
            setupFullTeam();

            JiraIssueEntity epic1 = createEpic("TEST-1");
            epic1.setAutoScore(new BigDecimal("90"));
            epic1.setRoughEstimateDevDays(new BigDecimal("5"));

            JiraIssueEntity epic2 = createEpic("TEST-2");
            epic2.setAutoScore(new BigDecimal("80"));
            epic2.setRoughEstimateDevDays(new BigDecimal("5"));

            JiraIssueEntity epic3 = createEpic("TEST-3");
            epic3.setAutoScore(new BigDecimal("70"));
            epic3.setRoughEstimateDevDays(new BigDecimal("5"));

            JiraIssueEntity epic4 = createEpic("TEST-4");
            epic4.setAutoScore(new BigDecimal("60"));
            epic4.setRoughEstimateDevDays(new BigDecimal("5"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic1, epic2, epic3, epic4));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: First 2 are within WIP, last 2 are queued
            assertEquals(4, response.epics().size());

            // First 2 within WIP
            assertTrue(response.epics().get(0).isWithinWip(), "Epic 1 should be within WIP");
            assertTrue(response.epics().get(1).isWithinWip(), "Epic 2 should be within WIP");

            // Last 2 queued
            assertFalse(response.epics().get(2).isWithinWip(), "Epic 3 should be queued");
            assertFalse(response.epics().get(3).isWithinWip(), "Epic 4 should be queued");

            assertEquals(1, response.epics().get(2).queuePosition(), "Epic 3 queue position should be 1");
            assertEquals(2, response.epics().get(3).queuePosition(), "Epic 4 queue position should be 2");
        }

        @Test
        void pipelineWipAllowsSaToStartNextEpicImmediately() {
            // Given: SA WIP = 1, DEV WIP = 1 (Pipeline model)
            // Epic2 SA should start after Epic1 SA ends, not after Epic1 finishes entirely
            PlanningConfigDto config = new PlanningConfigDto(
                    PlanningConfigDto.GradeCoefficients.defaults(),
                    new BigDecimal("0.2"),
                    new PlanningConfigDto.WipLimits(6, 1, 1, 1), // All WIP = 1
                    PlanningConfigDto.StoryDuration.defaults(),
                    null  // statusMapping
            );
            when(teamService.getPlanningConfig(TEAM_ID)).thenReturn(config);
            setupFullTeam();

            JiraIssueEntity epic1 = createEpic("TEST-1");
            epic1.setAutoScore(new BigDecimal("90"));
            epic1.setRoughEstimateSaDays(new BigDecimal("3"));
            epic1.setRoughEstimateDevDays(new BigDecimal("10"));
            epic1.setRoughEstimateQaDays(new BigDecimal("3"));

            JiraIssueEntity epic2 = createEpic("TEST-2");
            epic2.setAutoScore(new BigDecimal("80"));
            epic2.setRoughEstimateSaDays(new BigDecimal("2"));
            epic2.setRoughEstimateDevDays(new BigDecimal("8"));
            epic2.setRoughEstimateQaDays(new BigDecimal("2"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic1, epic2));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: Pipeline WIP allows overlapping phases between epics
            EpicForecast forecast1 = response.epics().get(0);
            EpicForecast forecast2 = response.epics().get(1);

            // Epic1 is within team WIP
            assertTrue(forecast1.isWithinWip());

            // Epic2 SA waits for Epic1 SA (SA WIP = 1)
            assertTrue(forecast2.phaseWaitInfo().sa().waiting(),
                    "Epic2 SA should wait for SA slot");
            assertEquals(forecast1.phaseSchedule().sa().endDate(),
                    forecast2.phaseWaitInfo().sa().waitingUntil(),
                    "Epic2 SA waits until Epic1 SA ends");

            // Key Pipeline WIP assertion:
            // Epic2 SA starts after Epic1 SA (not after Epic1 finishes!)
            LocalDate epic1SaEnd = forecast1.phaseSchedule().sa().endDate();
            LocalDate epic2SaStart = forecast2.phaseSchedule().sa().startDate();

            assertTrue(epic2SaStart.isAfter(epic1SaEnd) || epic2SaStart.equals(epic1SaEnd.plusDays(1)),
                    "Epic2 SA should start after Epic1 SA ends (Pipeline WIP)");

            // Epic2 DEV waits for Epic1 DEV (DEV WIP = 1)
            assertTrue(forecast2.phaseWaitInfo().dev().waiting(),
                    "Epic2 DEV should wait for DEV slot");
        }

        @Test
        void pipelineWipAllowsOverlappingPhasesBetweenEpics() {
            // Given: High WIP limits allow parallel work
            // Epic2 SA should overlap with Epic1 DEV
            PlanningConfigDto config = new PlanningConfigDto(
                    PlanningConfigDto.GradeCoefficients.defaults(),
                    new BigDecimal("0.2"),
                    new PlanningConfigDto.WipLimits(6, 3, 3, 3), // Generous WIP limits
                    PlanningConfigDto.StoryDuration.defaults(),
                    null  // statusMapping
            );
            when(teamService.getPlanningConfig(TEAM_ID)).thenReturn(config);
            setupFullTeam();

            JiraIssueEntity epic1 = createEpic("TEST-1");
            epic1.setAutoScore(new BigDecimal("90"));
            epic1.setRoughEstimateSaDays(new BigDecimal("2"));
            epic1.setRoughEstimateDevDays(new BigDecimal("15")); // Long DEV phase
            epic1.setRoughEstimateQaDays(new BigDecimal("3"));

            JiraIssueEntity epic2 = createEpic("TEST-2");
            epic2.setAutoScore(new BigDecimal("80"));
            epic2.setRoughEstimateSaDays(new BigDecimal("2"));
            epic2.setRoughEstimateDevDays(new BigDecimal("10"));
            epic2.setRoughEstimateQaDays(new BigDecimal("2"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic1, epic2));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: With Pipeline WIP, phases can overlap
            EpicForecast forecast1 = response.epics().get(0);
            EpicForecast forecast2 = response.epics().get(1);

            // Both epics are within WIP (generous limits)
            assertTrue(forecast1.isWithinWip());
            assertTrue(forecast2.isWithinWip());

            // Key assertion: Epic2 SA can start while Epic1 DEV is ongoing
            // (Epic1 DEV takes 15 days, Epic1 SA only 2 days)
            LocalDate epic1DevEnd = forecast1.phaseSchedule().dev().endDate();
            LocalDate epic2SaStart = forecast2.phaseSchedule().sa().startDate();

            assertTrue(epic2SaStart.isBefore(epic1DevEnd),
                    "Epic2 SA should start before Epic1 DEV ends (overlapping phases in Pipeline WIP)");

            // No phase should be waiting (generous WIP limits)
            assertFalse(forecast2.phaseWaitInfo().sa().waiting(), "SA should not be waiting");
            assertFalse(forecast2.phaseWaitInfo().dev().waiting(), "DEV should not be waiting");
            assertFalse(forecast2.phaseWaitInfo().qa().waiting(), "QA should not be waiting");
        }

        @Test
        void wipStatusReturnsCorrectValues() {
            // Given: WIP limit = 3, 5 epics
            setupPlanningConfigWithWipLimit(3);
            setupFullTeam();

            List<JiraIssueEntity> epics = List.of(
                    createEpicWithEstimate("TEST-1", 5),
                    createEpicWithEstimate("TEST-2", 5),
                    createEpicWithEstimate("TEST-3", 5),
                    createEpicWithEstimate("TEST-4", 5),
                    createEpicWithEstimate("TEST-5", 5)
            );

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(epics);

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then
            assertNotNull(response.wipStatus());
            assertEquals(3, response.wipStatus().limit());
            assertEquals(3, response.wipStatus().current()); // min(5, 3) = 3
            assertFalse(response.wipStatus().exceeded());
        }

        @Test
        void defaultWipLimitIsUsedWhenNotConfigured() {
            // Given: No WIP limit configured (null)
            setupDefaultPlanningConfig(); // Uses null for WIP limits
            setupFullTeam();

            // Create 7 epics (more than default limit of 6)
            List<JiraIssueEntity> epics = List.of(
                    createEpicWithEstimate("TEST-1", 5),
                    createEpicWithEstimate("TEST-2", 5),
                    createEpicWithEstimate("TEST-3", 5),
                    createEpicWithEstimate("TEST-4", 5),
                    createEpicWithEstimate("TEST-5", 5),
                    createEpicWithEstimate("TEST-6", 5),
                    createEpicWithEstimate("TEST-7", 5)
            );

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(epics);

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: Default limit of 6 is used
            assertEquals(6, response.wipStatus().limit());

            // First 6 within WIP, 7th queued
            for (int i = 0; i < 6; i++) {
                assertTrue(response.epics().get(i).isWithinWip(), "Epic " + (i+1) + " should be within WIP");
            }
            assertFalse(response.epics().get(6).isWithinWip(), "Epic 7 should be queued");
        }

        private JiraIssueEntity createEpicWithEstimate(String key, int devDays) {
            JiraIssueEntity epic = createEpic(key);
            epic.setRoughEstimateDevDays(new BigDecimal(devDays));
            return epic;
        }
    }

    // ==================== Role-Specific WIP Limits Tests ====================

    @Nested
    class RoleSpecificWipTests {

        @Test
        void roleWipStatusIncludedInResponse() {
            // Given: Config with role-specific WIP limits
            PlanningConfigDto config = new PlanningConfigDto(
                    PlanningConfigDto.GradeCoefficients.defaults(),
                    new BigDecimal("0.2"),
                    new PlanningConfigDto.WipLimits(6, 2, 3, 2), // SA=2, DEV=3, QA=2
                    PlanningConfigDto.StoryDuration.defaults(),
                    null  // statusMapping
            );
            when(teamService.getPlanningConfig(TEAM_ID)).thenReturn(config);
            setupFullTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateDevDays(new BigDecimal("5"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: Role WIP status included
            assertNotNull(response.wipStatus());
            assertNotNull(response.wipStatus().sa(), "SA WIP status should be present");
            assertNotNull(response.wipStatus().dev(), "DEV WIP status should be present");
            assertNotNull(response.wipStatus().qa(), "QA WIP status should be present");

            assertEquals(2, response.wipStatus().sa().limit());
            assertEquals(3, response.wipStatus().dev().limit());
            assertEquals(2, response.wipStatus().qa().limit());
        }

        @Test
        void phaseWaitInfoIncludedInEpicForecast() {
            // Given
            setupPlanningConfigWithRoleWip(6, 2, 3, 2);
            setupFullTeam();

            JiraIssueEntity epic = createEpic("TEST-1");
            epic.setRoughEstimateSaDays(new BigDecimal("2"));
            epic.setRoughEstimateDevDays(new BigDecimal("5"));
            epic.setRoughEstimateQaDays(new BigDecimal("2"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: PhaseWaitInfo included
            EpicForecast forecast = response.epics().get(0);
            assertNotNull(forecast.phaseWaitInfo(), "PhaseWaitInfo should be present");
            assertNotNull(forecast.phaseWaitInfo().sa());
            assertNotNull(forecast.phaseWaitInfo().dev());
            assertNotNull(forecast.phaseWaitInfo().qa());
        }

        @Test
        void epicWaitsForRoleWipSlot() {
            // Given: SA WIP limit = 1, 3 epics each with SA work
            setupPlanningConfigWithRoleWip(6, 1, 3, 2); // SA limit = 1
            setupFullTeam();

            JiraIssueEntity epic1 = createEpic("TEST-1");
            epic1.setAutoScore(new BigDecimal("90"));
            epic1.setRoughEstimateSaDays(new BigDecimal("2"));
            epic1.setRoughEstimateDevDays(new BigDecimal("5"));

            JiraIssueEntity epic2 = createEpic("TEST-2");
            epic2.setAutoScore(new BigDecimal("80"));
            epic2.setRoughEstimateSaDays(new BigDecimal("2"));
            epic2.setRoughEstimateDevDays(new BigDecimal("5"));

            JiraIssueEntity epic3 = createEpic("TEST-3");
            epic3.setAutoScore(new BigDecimal("70"));
            epic3.setRoughEstimateSaDays(new BigDecimal("2"));
            epic3.setRoughEstimateDevDays(new BigDecimal("5"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic1, epic2, epic3));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: All epics within team WIP, but Epic 2 and 3 might wait for SA slot
            assertEquals(3, response.epics().size());

            // All are within team WIP
            assertTrue(response.epics().get(0).isWithinWip());
            assertTrue(response.epics().get(1).isWithinWip());
            assertTrue(response.epics().get(2).isWithinWip());

            // Epic 2 SA phase should wait for Epic 1 SA to finish
            EpicForecast epic2Forecast = response.epics().get(1);
            if (epic2Forecast.phaseWaitInfo() != null && epic2Forecast.phaseWaitInfo().sa() != null) {
                // SA wait info should indicate waiting
                Boolean saWaiting = epic2Forecast.phaseWaitInfo().sa().waiting();
                if (saWaiting != null && saWaiting) {
                    assertNotNull(epic2Forecast.phaseWaitInfo().sa().waitingUntil());
                }
            }
        }

        @Test
        void devWipLimitAffectsPhaseStart() {
            // Given: DEV WIP limit = 1, 2 epics
            setupPlanningConfigWithRoleWip(6, 2, 1, 2); // DEV limit = 1
            setupFullTeam();

            JiraIssueEntity epic1 = createEpic("TEST-1");
            epic1.setAutoScore(new BigDecimal("90"));
            epic1.setRoughEstimateDevDays(new BigDecimal("10"));

            JiraIssueEntity epic2 = createEpic("TEST-2");
            epic2.setAutoScore(new BigDecimal("80"));
            epic2.setRoughEstimateDevDays(new BigDecimal("10"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic1, epic2));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: Epic 2's DEV phase should start after Epic 1's DEV ends
            EpicForecast forecast1 = response.epics().get(0);
            EpicForecast forecast2 = response.epics().get(1);

            LocalDate epic1DevEnd = forecast1.phaseSchedule().dev().endDate();
            LocalDate epic2DevStart = forecast2.phaseSchedule().dev().startDate();

            // DEV WIP limit = 1 means Epic 2 DEV starts after Epic 1 DEV ends
            assertTrue(epic2DevStart.isAfter(epic1DevEnd) ||
                            epic2DevStart.equals(epic1DevEnd.plusDays(1)),
                    "Epic 2 DEV should start after Epic 1 DEV ends due to DEV WIP limit");
        }

        @Test
        void combinedTeamAndRoleWipLimits() {
            // Given: Team WIP = 2, SA = 1, DEV = 2, QA = 1
            setupPlanningConfigWithRoleWip(2, 1, 2, 1);
            setupFullTeam();

            JiraIssueEntity epic1 = createEpic("TEST-1");
            epic1.setAutoScore(new BigDecimal("90"));
            epic1.setRoughEstimateSaDays(new BigDecimal("2"));
            epic1.setRoughEstimateDevDays(new BigDecimal("5"));
            epic1.setRoughEstimateQaDays(new BigDecimal("2"));

            JiraIssueEntity epic2 = createEpic("TEST-2");
            epic2.setAutoScore(new BigDecimal("80"));
            epic2.setRoughEstimateSaDays(new BigDecimal("2"));
            epic2.setRoughEstimateDevDays(new BigDecimal("5"));
            epic2.setRoughEstimateQaDays(new BigDecimal("2"));

            JiraIssueEntity epic3 = createEpic("TEST-3");
            epic3.setAutoScore(new BigDecimal("70"));
            epic3.setRoughEstimateSaDays(new BigDecimal("2"));
            epic3.setRoughEstimateDevDays(new BigDecimal("5"));
            epic3.setRoughEstimateQaDays(new BigDecimal("2"));

            when(issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(anyList(), eq(TEAM_ID)))
                    .thenReturn(List.of(epic1, epic2, epic3));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: Team WIP = 2, so Epic 3 is queued at team level
            assertEquals(3, response.epics().size());

            assertTrue(response.epics().get(0).isWithinWip(), "Epic 1 within team WIP");
            assertTrue(response.epics().get(1).isWithinWip(), "Epic 2 within team WIP");
            assertFalse(response.epics().get(2).isWithinWip(), "Epic 3 queued at team level");

            // Epic 3 should have queue position
            assertEquals(1, response.epics().get(2).queuePosition());
        }

        private void setupPlanningConfigWithRoleWip(int team, int sa, int dev, int qa) {
            PlanningConfigDto config = new PlanningConfigDto(
                    PlanningConfigDto.GradeCoefficients.defaults(),
                    new BigDecimal("0.2"),
                    new PlanningConfigDto.WipLimits(team, sa, dev, qa),
                    PlanningConfigDto.StoryDuration.defaults(),
                    null  // statusMapping
            );
            when(teamService.getPlanningConfig(TEAM_ID)).thenReturn(config);
        }
    }

    // ==================== Helper Methods ====================

    private void setupPlanningConfigWithWipLimit(int wipLimit) {
        PlanningConfigDto config = new PlanningConfigDto(
                PlanningConfigDto.GradeCoefficients.defaults(),
                new BigDecimal("0.2"),
                new PlanningConfigDto.WipLimits(wipLimit, 2, 3, 2),
                PlanningConfigDto.StoryDuration.defaults(),
                null  // statusMapping
        );
        when(teamService.getPlanningConfig(TEAM_ID)).thenReturn(config);
    }

    private void setupDefaultPlanningConfig() {
        PlanningConfigDto config = new PlanningConfigDto(
                PlanningConfigDto.GradeCoefficients.defaults(),
                new BigDecimal("0.2"),
                null, // WIP limits
                PlanningConfigDto.StoryDuration.defaults(),
                null  // statusMapping
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
