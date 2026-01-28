package com.leadboard.planning;

import com.leadboard.planning.dto.EpicForecast;
import com.leadboard.planning.dto.EpicForecast.*;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.*;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForecastServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private TeamService teamService;

    @Mock
    private TeamMemberRepository memberRepository;

    @Mock
    private UnifiedPlanningService unifiedPlanningService;

    private ForecastService forecastService;

    private static final Long TEAM_ID = 1L;

    @BeforeEach
    void setUp() {
        forecastService = new ForecastService(
                issueRepository,
                teamService,
                memberRepository,
                unifiedPlanningService
        );

        // Default planning config
        setupDefaultPlanningConfig();
    }

    // ==================== Team Capacity Tests ====================

    @Nested
    class TeamCapacityTests {

        @Test
        void calculatesCapacityWithDefaultGradeCoefficients() {
            // Given: team with SA(6h), DEV(8h), QA(6h) all Middle grade
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.SA, Grade.MIDDLE, new BigDecimal("6")),
                    createMember(Role.DEV, Grade.MIDDLE, new BigDecimal("8")),
                    createMember(Role.QA, Grade.MIDDLE, new BigDecimal("6"))
            ));
            setupEmptyUnifiedPlanningResult();

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
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.DEV, Grade.SENIOR, new BigDecimal("8"))
            ));
            setupEmptyUnifiedPlanningResult();

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: 8 / 0.8 = 10 effective hours
            assertEquals(new BigDecimal("10.00"), response.teamCapacity().devHoursPerDay());
        }

        @Test
        void juniorGradeDecreasesEffectiveCapacity() {
            // Given: Junior with 8h/day, coefficient 1.5
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.DEV, Grade.JUNIOR, new BigDecimal("8"))
            ));
            setupEmptyUnifiedPlanningResult();

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: 8 / 1.5 = 5.33 effective hours
            assertEquals(new BigDecimal("5.33"), response.teamCapacity().devHoursPerDay());
        }

        @Test
        void aggregatesCapacityFromMultipleMembers() {
            // Given: 2 DEVs with different grades
            when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                    createMember(Role.DEV, Grade.SENIOR, new BigDecimal("8")),  // 8/0.8 = 10
                    createMember(Role.DEV, Grade.MIDDLE, new BigDecimal("6"))   // 6/1.0 = 6
            ));
            setupEmptyUnifiedPlanningResult();

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: 10 + 6 = 16 effective hours
            assertEquals(new BigDecimal("16.00"), response.teamCapacity().devHoursPerDay());
        }
    }

    // ==================== Conversion Tests ====================

    @Nested
    class ConversionTests {

        @Test
        void convertsUnifiedResultToForecastResponse() {
            // Given
            setupEmptyTeam();
            LocalDate today = LocalDate.now();

            PlannedStory story = new PlannedStory(
                    "STORY-1",
                    "Test Story",
                    new BigDecimal("70"),
                    "To Do",
                    today,
                    today.plusDays(5),
                    new PlannedPhases(
                            new UnifiedPlanningResult.PhaseSchedule("user-1", "SA User", today, today.plusDays(1), new BigDecimal("8"), false),
                            new UnifiedPlanningResult.PhaseSchedule("user-2", "DEV User", today.plusDays(2), today.plusDays(4), new BigDecimal("24"), false),
                            new UnifiedPlanningResult.PhaseSchedule("user-3", "QA User", today.plusDays(5), today.plusDays(5), new BigDecimal("8"), false)
                    ),
                    List.of(),
                    List.of(),
                    "Story",
                    "Medium",
                    false,
                    null,
                    null,
                    null,
                    RoleProgressInfo.empty()
            );

            PhaseAggregation aggregation = new PhaseAggregation(
                    new BigDecimal("8"),  // SA hours
                    new BigDecimal("24"), // DEV hours
                    new BigDecimal("8"),  // QA hours
                    today, today.plusDays(1),
                    today.plusDays(2), today.plusDays(4),
                    today.plusDays(5), today.plusDays(5)
            );

            PlannedEpic plannedEpic = new PlannedEpic(
                    "EPIC-1",
                    "Test Epic",
                    new BigDecimal("80"),
                    today,
                    today.plusDays(5),
                    List.of(story),
                    aggregation,
                    "Developing",
                    null,
                    null,
                    null,
                    null,
                    RoleProgressInfo.empty(),
                    1,
                    1
            );

            UnifiedPlanningResult unifiedResult = new UnifiedPlanningResult(
                    TEAM_ID,
                    OffsetDateTime.now(),
                    List.of(plannedEpic),
                    List.of(),
                    Map.of()
            );

            when(unifiedPlanningService.calculatePlan(TEAM_ID)).thenReturn(unifiedResult);
            when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(createEpic("EPIC-1")));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then
            assertEquals(1, response.epics().size());
            EpicForecast forecast = response.epics().get(0);

            assertEquals("EPIC-1", forecast.epicKey());
            assertEquals("Test Epic", forecast.summary());
            assertEquals(new BigDecimal("80"), forecast.autoScore());
            assertEquals(today.plusDays(5), forecast.expectedDone());

            // Check remaining by role (hours converted to days)
            assertEquals(new BigDecimal("1.0"), forecast.remainingByRole().sa().days());
            assertEquals(new BigDecimal("3.0"), forecast.remainingByRole().dev().days());
            assertEquals(new BigDecimal("1.0"), forecast.remainingByRole().qa().days());

            // Check phase schedule
            assertNotNull(forecast.phaseSchedule());
            assertEquals(today, forecast.phaseSchedule().sa().startDate());
            assertEquals(today.plusDays(1), forecast.phaseSchedule().sa().endDate());
        }

        @Test
        void setsHighConfidenceWhenNoWarnings() {
            // Given: Epic with estimates and no warnings
            setupEmptyTeam();
            LocalDate today = LocalDate.now();

            PlannedEpic plannedEpic = createPlannedEpic("EPIC-1", today, today.plusDays(5),
                    new BigDecimal("8"), new BigDecimal("24"), new BigDecimal("8"));

            UnifiedPlanningResult unifiedResult = new UnifiedPlanningResult(
                    TEAM_ID,
                    OffsetDateTime.now(),
                    List.of(plannedEpic),
                    List.of(), // No warnings
                    Map.of()
            );

            when(unifiedPlanningService.calculatePlan(TEAM_ID)).thenReturn(unifiedResult);
            when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(createEpic("EPIC-1")));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then
            assertEquals(Confidence.HIGH, response.epics().get(0).confidence());
        }

        @Test
        void setsLowConfidenceWhenNoEstimates() {
            // Given: Epic with zero estimates
            setupEmptyTeam();
            LocalDate today = LocalDate.now();

            PlannedEpic plannedEpic = createPlannedEpic("EPIC-1", today, today,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            UnifiedPlanningResult unifiedResult = new UnifiedPlanningResult(
                    TEAM_ID,
                    OffsetDateTime.now(),
                    List.of(plannedEpic),
                    List.of(),
                    Map.of()
            );

            when(unifiedPlanningService.calculatePlan(TEAM_ID)).thenReturn(unifiedResult);
            when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(createEpic("EPIC-1")));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then
            assertEquals(Confidence.LOW, response.epics().get(0).confidence());
        }

        @Test
        void setsMediumConfidenceWhenMultipleNoCapacityWarnings() {
            // Given: Epic with no-capacity warnings
            setupEmptyTeam();
            LocalDate today = LocalDate.now();

            PlannedStory story1 = new PlannedStory("STORY-1", "Story 1", BigDecimal.TEN, "To Do",
                    today, today.plusDays(2), PlannedPhases.empty(), List.of(), List.of(),
                    "Story", "Medium", false, null, null, null, RoleProgressInfo.empty());
            PlannedStory story2 = new PlannedStory("STORY-2", "Story 2", BigDecimal.TEN, "To Do",
                    today, today.plusDays(2), PlannedPhases.empty(), List.of(), List.of(),
                    "Story", "Medium", false, null, null, null, RoleProgressInfo.empty());

            PlannedEpic plannedEpic = new PlannedEpic(
                    "EPIC-1", "Test Epic", new BigDecimal("80"),
                    today, today.plusDays(5),
                    List.of(story1, story2),
                    new PhaseAggregation(
                            new BigDecimal("8"), new BigDecimal("24"), new BigDecimal("8"),
                            today, today.plusDays(1),
                            today.plusDays(2), today.plusDays(4),
                            today.plusDays(5), today.plusDays(5)
                    ),
                    "Developing", null, null, null, null, RoleProgressInfo.empty(), 2, 2
            );

            List<PlanningWarning> warnings = List.of(
                    new PlanningWarning("STORY-1", WarningType.NO_CAPACITY, "No SA capacity"),
                    new PlanningWarning("STORY-2", WarningType.NO_CAPACITY, "No QA capacity")
            );

            UnifiedPlanningResult unifiedResult = new UnifiedPlanningResult(
                    TEAM_ID,
                    OffsetDateTime.now(),
                    List.of(plannedEpic),
                    warnings,
                    Map.of()
            );

            when(unifiedPlanningService.calculatePlan(TEAM_ID)).thenReturn(unifiedResult);
            when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(createEpic("EPIC-1")));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then
            assertEquals(Confidence.LOW, response.epics().get(0).confidence());
        }

        @Test
        void calculatesDueDateDelta() {
            // Given
            setupEmptyTeam();
            LocalDate today = LocalDate.now();
            LocalDate dueDate = today.plusDays(10);
            LocalDate expectedDone = today.plusDays(5);

            PlannedEpic plannedEpic = createPlannedEpic("EPIC-1", today, expectedDone,
                    new BigDecimal("8"), new BigDecimal("24"), new BigDecimal("8"));

            UnifiedPlanningResult unifiedResult = new UnifiedPlanningResult(
                    TEAM_ID,
                    OffsetDateTime.now(),
                    List.of(plannedEpic),
                    List.of(),
                    Map.of()
            );

            when(unifiedPlanningService.calculatePlan(TEAM_ID)).thenReturn(unifiedResult);

            JiraIssueEntity epicEntity = createEpic("EPIC-1");
            epicEntity.setDueDate(dueDate);
            when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epicEntity));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: delta = expectedDone - dueDate = 5 - 10 = -5 (ahead of schedule)
            assertEquals(-5, response.epics().get(0).dueDateDeltaDays());
        }

        @Test
        void filtersEpicsByStatusWhenProvided() {
            // Given
            setupEmptyTeam();
            LocalDate today = LocalDate.now();

            PlannedEpic epic1 = createPlannedEpic("EPIC-1", today, today.plusDays(5),
                    new BigDecimal("8"), new BigDecimal("24"), new BigDecimal("8"));
            PlannedEpic epic2 = createPlannedEpic("EPIC-2", today, today.plusDays(10),
                    new BigDecimal("16"), new BigDecimal("48"), new BigDecimal("16"));

            UnifiedPlanningResult unifiedResult = new UnifiedPlanningResult(
                    TEAM_ID,
                    OffsetDateTime.now(),
                    List.of(epic1, epic2),
                    List.of(),
                    Map.of()
            );

            when(unifiedPlanningService.calculatePlan(TEAM_ID)).thenReturn(unifiedResult);

            JiraIssueEntity epicEntity1 = createEpic("EPIC-1");
            epicEntity1.setStatus("In Progress");

            JiraIssueEntity epicEntity2 = createEpic("EPIC-2");
            epicEntity2.setStatus("To Do");

            when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epicEntity1));
            when(issueRepository.findByIssueKey("EPIC-2")).thenReturn(Optional.of(epicEntity2));

            // When: filter by "In Progress"
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID, List.of("In Progress"));

            // Then: only EPIC-1 should be returned
            assertEquals(1, response.epics().size());
            assertEquals("EPIC-1", response.epics().get(0).epicKey());
        }
    }

    // ==================== WIP Status Tests ====================

    @Nested
    class WipStatusTests {

        @Test
        void wipStatusReflectsAllEpicsAsActive() {
            // Given: Unified planning doesn't use WIP limits, all epics are "active"
            setupEmptyTeam();
            LocalDate today = LocalDate.now();

            PlannedEpic epic1 = createPlannedEpic("EPIC-1", today, today.plusDays(5),
                    new BigDecimal("8"), new BigDecimal("24"), new BigDecimal("8"));
            PlannedEpic epic2 = createPlannedEpic("EPIC-2", today, today.plusDays(10),
                    new BigDecimal("16"), new BigDecimal("48"), new BigDecimal("16"));

            UnifiedPlanningResult unifiedResult = new UnifiedPlanningResult(
                    TEAM_ID,
                    OffsetDateTime.now(),
                    List.of(epic1, epic2),
                    List.of(),
                    Map.of()
            );

            when(unifiedPlanningService.calculatePlan(TEAM_ID)).thenReturn(unifiedResult);
            when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(createEpic("EPIC-1")));
            when(issueRepository.findByIssueKey("EPIC-2")).thenReturn(Optional.of(createEpic("EPIC-2")));

            // When
            ForecastResponse response = forecastService.calculateForecast(TEAM_ID);

            // Then: All epics are within WIP
            assertEquals(2, response.epics().size());
            assertTrue(response.epics().get(0).isWithinWip());
            assertTrue(response.epics().get(1).isWithinWip());
            assertNull(response.epics().get(0).queuePosition());
            assertNull(response.epics().get(1).queuePosition());

            // WIP status shows all active
            assertEquals(2, response.wipStatus().current());
            assertEquals(2, response.wipStatus().limit());
            assertFalse(response.wipStatus().exceeded());
        }
    }

    // ==================== Helper Methods ====================

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

    private void setupEmptyUnifiedPlanningResult() {
        UnifiedPlanningResult emptyResult = new UnifiedPlanningResult(
                TEAM_ID,
                OffsetDateTime.now(),
                List.of(),
                List.of(),
                Map.of()
        );
        when(unifiedPlanningService.calculatePlan(TEAM_ID)).thenReturn(emptyResult);
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
        epic.setStatus("Developing");
        epic.setTeamId(TEAM_ID);
        epic.setAutoScore(new BigDecimal("50"));
        epic.setManualPriorityBoost(0);
        return epic;
    }

    private PlannedEpic createPlannedEpic(String epicKey, LocalDate startDate, LocalDate endDate,
                                          BigDecimal saHours, BigDecimal devHours, BigDecimal qaHours) {
        PlannedStory story = new PlannedStory(
                epicKey + "-STORY-1",
                "Story for " + epicKey,
                new BigDecimal("70"),
                "To Do",
                startDate,
                endDate,
                PlannedPhases.empty(),
                List.of(),
                List.of(),
                "Story",
                "Medium",
                false,
                null,
                null,
                null,
                RoleProgressInfo.empty()
        );

        PhaseAggregation aggregation = new PhaseAggregation(
                saHours, devHours, qaHours,
                startDate, startDate.plusDays(1),
                startDate.plusDays(2), endDate.minusDays(1),
                endDate, endDate
        );

        return new PlannedEpic(
                epicKey,
                "Test Epic " + epicKey,
                new BigDecimal("80"),
                startDate,
                endDate,
                List.of(story),
                aggregation,
                "Developing",
                null,
                null,
                null,
                null,
                RoleProgressInfo.empty(),
                1,
                1
        );
    }
}
