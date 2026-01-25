package com.leadboard.planning;

import com.leadboard.planning.dto.EpicForecast;
import com.leadboard.planning.dto.EpicForecast.*;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.ForecastResponse.TeamCapacity;
import com.leadboard.planning.dto.ForecastResponse.WipStatus;
import com.leadboard.planning.dto.ForecastResponse.RoleWipStatus;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.*;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.*;
import com.leadboard.team.dto.PlanningConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сервис расчёта прогноза завершения эпиков.
 *
 * Делегирует основную работу к UnifiedPlanningService и конвертирует
 * результат в ForecastResponse для обратной совместимости API.
 */
@Service
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);
    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("8");

    private final JiraIssueRepository issueRepository;
    private final TeamService teamService;
    private final TeamMemberRepository memberRepository;
    private final UnifiedPlanningService unifiedPlanningService;

    public ForecastService(
            JiraIssueRepository issueRepository,
            TeamService teamService,
            TeamMemberRepository memberRepository,
            UnifiedPlanningService unifiedPlanningService
    ) {
        this.issueRepository = issueRepository;
        this.teamService = teamService;
        this.memberRepository = memberRepository;
        this.unifiedPlanningService = unifiedPlanningService;
    }

    /**
     * Рассчитывает прогноз для всех эпиков команды.
     */
    public ForecastResponse calculateForecast(Long teamId) {
        return calculateForecast(teamId, null);
    }

    /**
     * Рассчитывает прогноз для эпиков команды с фильтрацией по статусам.
     * Делегирует к UnifiedPlanningService и конвертирует результат.
     */
    public ForecastResponse calculateForecast(Long teamId, List<String> statuses) {
        log.info("Calculating forecast for team {} with statuses {}", teamId, statuses);

        // Get unified planning result
        UnifiedPlanningResult unifiedResult = unifiedPlanningService.calculatePlan(teamId);

        // Calculate team capacity for response
        PlanningConfigDto config = teamService.getPlanningConfig(teamId);
        TeamCapacity teamCapacity = calculateTeamCapacity(teamId, config);

        // Convert unified result to forecast response
        return convertToForecastResponse(unifiedResult, teamCapacity, statuses);
    }

    /**
     * Converts UnifiedPlanningResult to ForecastResponse for backward compatibility.
     */
    private ForecastResponse convertToForecastResponse(
            UnifiedPlanningResult unifiedResult,
            TeamCapacity teamCapacity,
            List<String> statusFilter
    ) {
        List<EpicForecast> forecasts = new ArrayList<>();

        for (PlannedEpic plannedEpic : unifiedResult.epics()) {
            // Apply status filter if provided
            if (statusFilter != null && !statusFilter.isEmpty()) {
                Optional<JiraIssueEntity> epicEntity = issueRepository.findByIssueKey(plannedEpic.epicKey());
                if (epicEntity.isPresent() && !statusFilter.contains(epicEntity.get().getStatus())) {
                    continue;
                }
            }

            EpicForecast forecast = convertPlannedEpicToForecast(plannedEpic, unifiedResult.warnings());
            forecasts.add(forecast);
        }

        // Create default WIP status (unified planning doesn't use WIP limits)
        WipStatus wipStatus = WipStatus.of(
                forecasts.size(),  // No limit, all epics are "in WIP"
                forecasts.size(),
                RoleWipStatus.of(forecasts.size(), forecasts.size()),
                RoleWipStatus.of(forecasts.size(), forecasts.size()),
                RoleWipStatus.of(forecasts.size(), forecasts.size())
        );

        return new ForecastResponse(
                unifiedResult.planningDate(),
                unifiedResult.teamId(),
                teamCapacity,
                wipStatus,
                forecasts
        );
    }

    /**
     * Converts a PlannedEpic to EpicForecast.
     */
    private EpicForecast convertPlannedEpicToForecast(PlannedEpic plannedEpic, List<PlanningWarning> warnings) {
        // Get epic entity for additional data
        Optional<JiraIssueEntity> epicEntityOpt = issueRepository.findByIssueKey(plannedEpic.epicKey());

        Integer manualPriorityBoost = null;
        LocalDate dueDate = null;
        if (epicEntityOpt.isPresent()) {
            JiraIssueEntity epic = epicEntityOpt.get();
            manualPriorityBoost = epic.getManualPriorityBoost();
            dueDate = epic.getDueDate();
        }

        // Calculate confidence from warnings
        Confidence confidence = calculateConfidence(plannedEpic, warnings);

        // Calculate due date delta
        Integer dueDateDeltaDays = calculateDueDateDelta(plannedEpic.endDate(), dueDate);

        // Convert phase aggregation to remaining by role
        PhaseAggregation agg = plannedEpic.phaseAggregation();
        RemainingByRole remainingByRole = new RemainingByRole(
                new RoleRemaining(agg.saHours(), hoursToDays(agg.saHours())),
                new RoleRemaining(agg.devHours(), hoursToDays(agg.devHours())),
                new RoleRemaining(agg.qaHours(), hoursToDays(agg.qaHours()))
        );

        // Convert phase aggregation to phase schedule
        EpicForecast.PhaseSchedule phaseSchedule = new EpicForecast.PhaseSchedule(
                new PhaseInfo(agg.saStartDate(), agg.saEndDate(), hoursToDays(agg.saHours()), false),
                new PhaseInfo(agg.devStartDate(), agg.devEndDate(), hoursToDays(agg.devHours()), false),
                new PhaseInfo(agg.qaStartDate(), agg.qaEndDate(), hoursToDays(agg.qaHours()), false)
        );

        return new EpicForecast(
                plannedEpic.epicKey(),
                plannedEpic.summary(),
                plannedEpic.autoScore(),
                manualPriorityBoost,
                plannedEpic.endDate(),
                confidence,
                dueDateDeltaDays,
                dueDate,
                remainingByRole,
                phaseSchedule,
                null,  // queuePosition - not used in unified planning
                null,  // queuedUntil - not used in unified planning
                true,  // isWithinWip - all epics are "active" in unified planning
                EpicForecast.PhaseWaitInfo.none()  // No wait info in unified planning
        );
    }

    /**
     * Calculates confidence level based on epic data and warnings.
     */
    private Confidence calculateConfidence(PlannedEpic plannedEpic, List<PlanningWarning> warnings) {
        PhaseAggregation agg = plannedEpic.phaseAggregation();

        // Check if epic has any estimates
        boolean hasEstimates = agg.saHours().compareTo(BigDecimal.ZERO) > 0
                || agg.devHours().compareTo(BigDecimal.ZERO) > 0
                || agg.qaHours().compareTo(BigDecimal.ZERO) > 0;

        if (!hasEstimates) {
            return Confidence.LOW;
        }

        // Count warnings for this epic's stories
        long warningCount = warnings.stream()
                .filter(w -> plannedEpic.stories().stream()
                        .anyMatch(s -> s.storyKey().equals(w.issueKey())))
                .count();

        // Count no-capacity warnings
        long noCapacityWarnings = warnings.stream()
                .filter(w -> w.type() == WarningType.NO_CAPACITY)
                .filter(w -> plannedEpic.stories().stream()
                        .anyMatch(s -> s.storyKey().equals(w.issueKey())))
                .count();

        if (noCapacityWarnings >= 2) {
            return Confidence.LOW;
        }
        if (noCapacityWarnings == 1 || warningCount > plannedEpic.stories().size() / 2) {
            return Confidence.MEDIUM;
        }

        return Confidence.HIGH;
    }

    /**
     * Helper method to convert hours to days.
     */
    private BigDecimal hoursToDays(BigDecimal hours) {
        if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return hours.divide(HOURS_PER_DAY, 1, RoundingMode.HALF_UP);
    }

    /**
     * Рассчитывает capacity команды по ролям с учётом коэффициентов грейдов.
     */
    private TeamCapacity calculateTeamCapacity(Long teamId, PlanningConfigDto config) {
        List<TeamMemberEntity> members = memberRepository.findByTeamIdAndActiveTrue(teamId);

        BigDecimal saCapacity = BigDecimal.ZERO;
        BigDecimal devCapacity = BigDecimal.ZERO;
        BigDecimal qaCapacity = BigDecimal.ZERO;

        var gradeCoefficients = config.gradeCoefficients() != null
                ? config.gradeCoefficients()
                : PlanningConfigDto.GradeCoefficients.defaults();

        for (TeamMemberEntity member : members) {
            BigDecimal hoursPerDay = member.getHoursPerDay();
            BigDecimal gradeCoef = getGradeCoefficient(member.getGrade(), gradeCoefficients);

            // Эффективные часы = часы / коэффициент грейда
            // (Junior с коэффициентом 1.5 даёт меньше эффективных часов)
            BigDecimal effectiveHours = hoursPerDay.divide(gradeCoef, 2, RoundingMode.HALF_UP);

            switch (member.getRole()) {
                case SA -> saCapacity = saCapacity.add(effectiveHours);
                case DEV -> devCapacity = devCapacity.add(effectiveHours);
                case QA -> qaCapacity = qaCapacity.add(effectiveHours);
            }
        }

        return new TeamCapacity(saCapacity, devCapacity, qaCapacity);
    }

    private BigDecimal getGradeCoefficient(Grade grade, PlanningConfigDto.GradeCoefficients coefficients) {
        return switch (grade) {
            case SENIOR -> coefficients.senior() != null ? coefficients.senior() : new BigDecimal("0.8");
            case MIDDLE -> coefficients.middle() != null ? coefficients.middle() : new BigDecimal("1.0");
            case JUNIOR -> coefficients.junior() != null ? coefficients.junior() : new BigDecimal("1.5");
        };
    }

    /**
     * Рассчитывает дельту между Expected Done и Due Date.
     * Положительное значение = опоздание, отрицательное = запас.
     */
    private Integer calculateDueDateDelta(LocalDate expectedDone, LocalDate dueDate) {
        if (expectedDone == null || dueDate == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(dueDate, expectedDone);
    }
}
