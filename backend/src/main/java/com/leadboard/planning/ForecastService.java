package com.leadboard.planning;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.config.entity.WorkflowRoleEntity;
import com.leadboard.planning.dto.EpicForecast;
import com.leadboard.planning.dto.EpicForecast.*;
import com.leadboard.planning.dto.ForecastResponse;
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
import java.util.*;

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
    private final WorkflowConfigService workflowConfigService;

    public ForecastService(
            JiraIssueRepository issueRepository,
            TeamService teamService,
            TeamMemberRepository memberRepository,
            UnifiedPlanningService unifiedPlanningService,
            WorkflowConfigService workflowConfigService
    ) {
        this.issueRepository = issueRepository;
        this.teamService = teamService;
        this.memberRepository = memberRepository;
        this.unifiedPlanningService = unifiedPlanningService;
        this.workflowConfigService = workflowConfigService;
    }

    public ForecastResponse calculateForecast(Long teamId) {
        return calculateForecast(teamId, null);
    }

    public ForecastResponse calculateForecast(Long teamId, List<String> statuses) {
        log.info("Calculating forecast for team {} with statuses {}", teamId, statuses);

        UnifiedPlanningResult unifiedResult = unifiedPlanningService.calculatePlan(teamId);

        PlanningConfigDto config = teamService.getPlanningConfig(teamId);
        Map<String, BigDecimal> roleCapacity = calculateRoleCapacity(teamId, config);

        return convertToForecastResponse(unifiedResult, roleCapacity, statuses);
    }

    private ForecastResponse convertToForecastResponse(
            UnifiedPlanningResult unifiedResult,
            Map<String, BigDecimal> roleCapacity,
            List<String> statusFilter
    ) {
        List<EpicForecast> forecasts = new ArrayList<>();

        for (PlannedEpic plannedEpic : unifiedResult.epics()) {
            if (statusFilter != null && !statusFilter.isEmpty()) {
                Optional<JiraIssueEntity> epicEntity = issueRepository.findByIssueKey(plannedEpic.epicKey());
                if (epicEntity.isPresent() && !statusFilter.contains(epicEntity.get().getStatus())) {
                    continue;
                }
            }

            EpicForecast forecast = convertPlannedEpicToForecast(plannedEpic, unifiedResult.warnings());
            forecasts.add(forecast);
        }

        // Build dynamic role WIP status
        Map<String, RoleWipStatus> roleWip = new LinkedHashMap<>();
        for (String roleCode : workflowConfigService.getRoleCodesInPipelineOrder()) {
            roleWip.put(roleCode, RoleWipStatus.of(forecasts.size(), forecasts.size()));
        }
        WipStatus wipStatus = WipStatus.of(forecasts.size(), forecasts.size(), roleWip);

        return new ForecastResponse(
                unifiedResult.planningDate(),
                unifiedResult.teamId(),
                roleCapacity,
                wipStatus,
                forecasts
        );
    }

    private EpicForecast convertPlannedEpicToForecast(PlannedEpic plannedEpic, List<PlanningWarning> warnings) {
        Optional<JiraIssueEntity> epicEntityOpt = issueRepository.findByIssueKey(plannedEpic.epicKey());
        LocalDate dueDate = epicEntityOpt.map(JiraIssueEntity::getDueDate).orElse(null);

        Confidence confidence = calculateConfidence(plannedEpic, warnings);
        Integer dueDateDeltaDays = calculateDueDateDelta(plannedEpic.endDate(), dueDate);

        // Convert phase aggregation to remaining by role
        Map<String, PhaseAggregationEntry> agg = plannedEpic.phaseAggregation();
        Map<String, RoleRemaining> remainingByRole = new LinkedHashMap<>();
        for (Map.Entry<String, PhaseAggregationEntry> entry : agg.entrySet()) {
            BigDecimal hours = entry.getValue().hours();
            remainingByRole.put(entry.getKey(), new RoleRemaining(hours, hoursToDays(hours)));
        }

        // Convert phase aggregation to phase schedule
        Map<String, PhaseInfo> phaseSchedule = new LinkedHashMap<>();
        for (Map.Entry<String, PhaseAggregationEntry> entry : agg.entrySet()) {
            PhaseAggregationEntry e = entry.getValue();
            phaseSchedule.put(entry.getKey(), new PhaseInfo(
                    e.startDate(), e.endDate(), hoursToDays(e.hours()), false));
        }

        // Phase wait info (none for unified planning)
        Map<String, EpicForecast.RoleWaitInfo> phaseWaitInfo = new LinkedHashMap<>();
        for (String roleCode : workflowConfigService.getRoleCodesInPipelineOrder()) {
            phaseWaitInfo.put(roleCode, EpicForecast.RoleWaitInfo.none());
        }

        return new EpicForecast(
                plannedEpic.epicKey(),
                plannedEpic.summary(),
                plannedEpic.autoScore(),
                plannedEpic.endDate(),
                confidence,
                dueDateDeltaDays,
                dueDate,
                remainingByRole,
                phaseSchedule,
                null,
                null,
                true,
                phaseWaitInfo
        );
    }

    private Confidence calculateConfidence(PlannedEpic plannedEpic, List<PlanningWarning> warnings) {
        Map<String, PhaseAggregationEntry> agg = plannedEpic.phaseAggregation();

        boolean hasEstimates = agg.values().stream()
                .anyMatch(e -> e.hours() != null && e.hours().compareTo(BigDecimal.ZERO) > 0);

        if (!hasEstimates) {
            return Confidence.LOW;
        }

        long warningCount = warnings.stream()
                .filter(w -> plannedEpic.stories().stream()
                        .anyMatch(s -> s.storyKey().equals(w.issueKey())))
                .count();

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

    private BigDecimal hoursToDays(BigDecimal hours) {
        if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return hours.divide(HOURS_PER_DAY, 1, RoundingMode.HALF_UP);
    }

    /**
     * Calculates capacity per role dynamically from WorkflowConfigService.
     */
    private Map<String, BigDecimal> calculateRoleCapacity(Long teamId, PlanningConfigDto config) {
        List<TeamMemberEntity> members = memberRepository.findByTeamIdAndActiveTrue(teamId);

        var gradeCoefficients = config.gradeCoefficients() != null
                ? config.gradeCoefficients()
                : PlanningConfigDto.GradeCoefficients.defaults();

        Map<String, BigDecimal> capacity = new LinkedHashMap<>();

        for (TeamMemberEntity member : members) {
            BigDecimal hoursPerDay = member.getHoursPerDay();
            BigDecimal gradeCoef = getGradeCoefficient(member.getGrade(), gradeCoefficients);
            BigDecimal effectiveHours = hoursPerDay.divide(gradeCoef, 2, RoundingMode.HALF_UP);

            String roleCode = member.getRole();
            capacity.merge(roleCode, effectiveHours, BigDecimal::add);
        }

        return capacity;
    }

    private BigDecimal getGradeCoefficient(Grade grade, PlanningConfigDto.GradeCoefficients coefficients) {
        return switch (grade) {
            case SENIOR -> coefficients.senior() != null ? coefficients.senior() : new BigDecimal("0.8");
            case MIDDLE -> coefficients.middle() != null ? coefficients.middle() : new BigDecimal("1.0");
            case JUNIOR -> coefficients.junior() != null ? coefficients.junior() : new BigDecimal("1.5");
        };
    }

    private Integer calculateDueDateDelta(LocalDate expectedDone, LocalDate dueDate) {
        if (expectedDone == null || dueDate == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(dueDate, expectedDone);
    }
}
