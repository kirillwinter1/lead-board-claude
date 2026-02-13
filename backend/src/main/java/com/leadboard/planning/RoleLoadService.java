package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.dto.RoleLoadResponse;
import com.leadboard.planning.dto.RoleLoadResponse.*;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.PhaseSchedule;
import com.leadboard.planning.dto.UnifiedPlanningResult.PlannedEpic;
import com.leadboard.planning.dto.UnifiedPlanningResult.PlannedStory;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис расчёта загрузки команды по ролям.
 * Анализирует запланированные задачи и capacity членов команды
 * для определения утилизации по динамическим ролям из WorkflowConfigService.
 */
@Service
public class RoleLoadService {

    private static final Logger log = LoggerFactory.getLogger(RoleLoadService.class);

    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final BigDecimal OVERLOAD_THRESHOLD = new BigDecimal("100");
    private static final BigDecimal IDLE_THRESHOLD = new BigDecimal("50");
    private static final BigDecimal IMBALANCE_THRESHOLD = new BigDecimal("40");

    private final TeamMemberRepository memberRepository;
    private final UnifiedPlanningService planningService;
    private final WorkCalendarService calendarService;
    private final WorkflowConfigService workflowConfigService;

    public RoleLoadService(
            TeamMemberRepository memberRepository,
            UnifiedPlanningService planningService,
            WorkCalendarService calendarService,
            WorkflowConfigService workflowConfigService
    ) {
        this.memberRepository = memberRepository;
        this.planningService = planningService;
        this.calendarService = calendarService;
        this.workflowConfigService = workflowConfigService;
    }

    /**
     * Рассчитывает загрузку команды по ролям на период вперёд.
     *
     * @param teamId ID команды
     * @return информация о загрузке по ролям с алертами
     */
    public RoleLoadResponse calculateRoleLoad(Long teamId) {
        LocalDate today = LocalDate.now();
        LocalDate periodEnd = calendarService.addWorkdays(today, DEFAULT_PERIOD_DAYS);

        // Получаем количество рабочих дней в периоде
        int workdays = calendarService.countWorkdays(today, periodEnd);

        // Получаем членов команды
        List<TeamMemberEntity> members = memberRepository.findByTeamIdAndActiveTrue(teamId);

        // Получаем динамические роли из конфигурации
        List<String> roleCodes = workflowConfigService.getRoleCodesInPipelineOrder();

        // Рассчитываем capacity по ролям динамически
        Map<String, RoleCapacity> capacityMap = new LinkedHashMap<>();
        for (String roleCode : roleCodes) {
            capacityMap.put(roleCode, calculateRoleCapacity(members, roleCode, workdays));
        }

        // Получаем unified planning для расчёта assigned hours
        UnifiedPlanningResult planning = planningService.calculatePlan(teamId);

        // Рассчитываем assigned hours по ролям (только в пределах периода)
        Map<String, BigDecimal> assignedHoursMap = calculateAssignedHours(planning, today, periodEnd);

        // Создаём RoleLoadInfo для каждой роли
        Map<String, RoleLoadInfo> roleInfoMap = new LinkedHashMap<>();
        for (String roleCode : roleCodes) {
            RoleCapacity capacity = capacityMap.getOrDefault(roleCode, new RoleCapacity(0, BigDecimal.ZERO));
            BigDecimal assignedHours = assignedHoursMap.getOrDefault(roleCode, BigDecimal.ZERO);
            roleInfoMap.put(roleCode, createRoleLoadInfo(capacity, assignedHours));
        }

        // Генерируем алерты динамически
        List<RoleLoadAlert> alerts = generateAlerts(roleInfoMap);

        // Логируем утилизацию
        StringBuilder utilizationLog = new StringBuilder();
        for (Map.Entry<String, RoleLoadInfo> entry : roleInfoMap.entrySet()) {
            if (utilizationLog.length() > 0) utilizationLog.append(", ");
            utilizationLog.append(entry.getKey()).append("=").append(entry.getValue().utilizationPercent()).append("%");
        }
        log.info("Role load calculated for team {}: {}", teamId, utilizationLog);

        // Populate backward-compatible RoleLoadResponse with fixed sa/dev/qa fields
        RoleLoadInfo saInfo = roleInfoMap.getOrDefault("SA", RoleLoadInfo.empty());
        RoleLoadInfo devInfo = roleInfoMap.getOrDefault("DEV", RoleLoadInfo.empty());
        RoleLoadInfo qaInfo = roleInfoMap.getOrDefault("QA", RoleLoadInfo.empty());

        return new RoleLoadResponse(
                teamId,
                today,
                DEFAULT_PERIOD_DAYS,
                saInfo,
                devInfo,
                qaInfo,
                alerts
        );
    }

    /**
     * Рассчитывает capacity для роли по коду.
     */
    private RoleCapacity calculateRoleCapacity(List<TeamMemberEntity> members, String roleCode, int workdays) {
        List<TeamMemberEntity> roleMembers = members.stream()
                .filter(m -> m.getRoleCode().equals(roleCode))
                .toList();

        BigDecimal totalHoursPerDay = BigDecimal.ZERO;
        for (TeamMemberEntity member : roleMembers) {
            totalHoursPerDay = totalHoursPerDay.add(member.getHoursPerDay());
        }

        BigDecimal totalCapacity = totalHoursPerDay.multiply(new BigDecimal(workdays));

        return new RoleCapacity(roleMembers.size(), totalCapacity);
    }

    /**
     * Рассчитывает назначенные часы по ролям из unified planning.
     * Maps the fixed PlannedPhases fields (sa/dev/qa) to role codes for backward compatibility.
     */
    private Map<String, BigDecimal> calculateAssignedHours(UnifiedPlanningResult planning, LocalDate periodStart, LocalDate periodEnd) {
        Map<String, BigDecimal> assignedHours = new LinkedHashMap<>();

        for (PlannedEpic epic : planning.epics()) {
            for (PlannedStory story : epic.stories()) {
                // Map fixed phase accessors to role codes for backward compatibility
                // PlannedPhases has sa(), dev(), qa() fields
                addPhaseHours(assignedHours, "SA", story.phases().sa(), periodStart, periodEnd);
                addPhaseHours(assignedHours, "DEV", story.phases().dev(), periodStart, periodEnd);
                addPhaseHours(assignedHours, "QA", story.phases().qa(), periodStart, periodEnd);
            }
        }

        return assignedHours;
    }

    /**
     * Adds phase hours for a role code to the assigned hours map.
     */
    private void addPhaseHours(Map<String, BigDecimal> assignedHours, String roleCode,
                               PhaseSchedule phase, LocalDate periodStart, LocalDate periodEnd) {
        if (phase != null) {
            BigDecimal hours = calculateHoursInPeriod(phase, periodStart, periodEnd);
            assignedHours.merge(roleCode, hours, BigDecimal::add);
        }
    }

    /**
     * Рассчитывает часы работы, попадающие в указанный период.
     */
    private BigDecimal calculateHoursInPeriod(PhaseSchedule phase, LocalDate periodStart, LocalDate periodEnd) {
        if (phase.startDate() == null || phase.endDate() == null) {
            return BigDecimal.ZERO;
        }

        LocalDate phaseStart = phase.startDate();
        LocalDate phaseEnd = phase.endDate();

        // Проверяем пересечение периодов
        if (phaseEnd.isBefore(periodStart) || phaseStart.isAfter(periodEnd)) {
            return BigDecimal.ZERO;
        }

        // Находим пересечение
        LocalDate overlapStart = phaseStart.isBefore(periodStart) ? periodStart : phaseStart;
        LocalDate overlapEnd = phaseEnd.isAfter(periodEnd) ? periodEnd : phaseEnd;

        // Рассчитываем долю часов, попадающую в период
        int totalPhaseDays = calendarService.countWorkdays(phaseStart, phaseEnd);
        int overlapDays = calendarService.countWorkdays(overlapStart, overlapEnd);

        if (totalPhaseDays == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal ratio = new BigDecimal(overlapDays)
                .divide(new BigDecimal(totalPhaseDays), 4, RoundingMode.HALF_UP);

        return phase.hours().multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Создаёт RoleLoadInfo из capacity и assigned hours.
     */
    private RoleLoadInfo createRoleLoadInfo(RoleCapacity capacity, BigDecimal assignedHours) {
        if (capacity.memberCount == 0) {
            return new RoleLoadInfo(
                    0,
                    BigDecimal.ZERO,
                    assignedHours,
                    BigDecimal.ZERO,
                    UtilizationStatus.NO_CAPACITY
            );
        }

        BigDecimal utilization = BigDecimal.ZERO;
        if (capacity.totalHours.compareTo(BigDecimal.ZERO) > 0) {
            utilization = assignedHours
                    .multiply(new BigDecimal("100"))
                    .divide(capacity.totalHours, 1, RoundingMode.HALF_UP);
        }

        UtilizationStatus status = determineStatus(utilization, capacity.memberCount > 0);

        return new RoleLoadInfo(
                capacity.memberCount,
                capacity.totalHours.setScale(1, RoundingMode.HALF_UP),
                assignedHours.setScale(1, RoundingMode.HALF_UP),
                utilization,
                status
        );
    }

    /**
     * Определяет статус утилизации.
     */
    private UtilizationStatus determineStatus(BigDecimal utilization, boolean hasCapacity) {
        if (!hasCapacity) {
            return UtilizationStatus.NO_CAPACITY;
        }
        if (utilization.compareTo(OVERLOAD_THRESHOLD) > 0) {
            return UtilizationStatus.OVERLOAD;
        }
        if (utilization.compareTo(IDLE_THRESHOLD) < 0) {
            return UtilizationStatus.IDLE;
        }
        return UtilizationStatus.NORMAL;
    }

    /**
     * Генерирует алерты динамически на основе ролей из конфигурации.
     */
    private List<RoleLoadAlert> generateAlerts(Map<String, RoleLoadInfo> roleInfoMap) {
        List<RoleLoadAlert> alerts = new ArrayList<>();

        // Проверяем перегрузку, простой и отсутствие capacity для каждой роли
        for (Map.Entry<String, RoleLoadInfo> entry : roleInfoMap.entrySet()) {
            String roleCode = entry.getKey();
            RoleLoadInfo info = entry.getValue();

            if (info.status() == UtilizationStatus.OVERLOAD) {
                alerts.add(new RoleLoadAlert(
                        AlertType.ROLE_OVERLOAD,
                        roleCode,
                        String.format("%s перегружены: %s%%", roleCode, info.utilizationPercent())
                ));
            }

            if (info.status() == UtilizationStatus.IDLE) {
                alerts.add(new RoleLoadAlert(
                        AlertType.ROLE_IDLE,
                        roleCode,
                        String.format("%s недозагружены: %s%%", roleCode, info.utilizationPercent())
                ));
            }

            if (info.status() == UtilizationStatus.NO_CAPACITY && info.totalAssignedHours().compareTo(BigDecimal.ZERO) > 0) {
                alerts.add(new RoleLoadAlert(
                        AlertType.NO_CAPACITY,
                        roleCode,
                        String.format("Нет %s в команде, но есть назначенная работа", roleCode)
                ));
            }
        }

        // Проверяем дисбаланс между ролями (только для ролей с capacity)
        List<BigDecimal> activeUtilizations = new ArrayList<>();
        List<String> activeRoles = new ArrayList<>();

        for (Map.Entry<String, RoleLoadInfo> entry : roleInfoMap.entrySet()) {
            if (entry.getValue().status() != UtilizationStatus.NO_CAPACITY) {
                activeUtilizations.add(entry.getValue().utilizationPercent());
                activeRoles.add(entry.getKey());
            }
        }

        if (activeUtilizations.size() >= 2) {
            BigDecimal max = activeUtilizations.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal min = activeUtilizations.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal diff = max.subtract(min);

            if (diff.compareTo(IMBALANCE_THRESHOLD) > 0) {
                // Находим роли с max и min
                int maxIdx = activeUtilizations.indexOf(max);
                int minIdx = activeUtilizations.indexOf(min);
                String maxRole = activeRoles.get(maxIdx);
                String minRole = activeRoles.get(minIdx);

                alerts.add(new RoleLoadAlert(
                        AlertType.IMBALANCE,
                        null,
                        String.format("Дисбаланс нагрузки: %s (%s%%) vs %s (%s%%)",
                                maxRole, max.setScale(0, RoundingMode.HALF_UP),
                                minRole, min.setScale(0, RoundingMode.HALF_UP))
                ));
            }
        }

        return alerts;
    }

    /**
     * Вспомогательный record для capacity роли.
     */
    private record RoleCapacity(int memberCount, BigDecimal totalHours) {}
}
