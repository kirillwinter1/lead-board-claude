package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.planning.dto.RoleLoadResponse;
import com.leadboard.planning.dto.RoleLoadResponse.*;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.PhaseSchedule;
import com.leadboard.planning.dto.UnifiedPlanningResult.PlannedEpic;
import com.leadboard.planning.dto.UnifiedPlanningResult.PlannedStory;
import com.leadboard.team.Role;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис расчёта загрузки команды по ролям.
 * Анализирует запланированные задачи и capacity членов команды
 * для определения утилизации SA/DEV/QA.
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

    public RoleLoadService(
            TeamMemberRepository memberRepository,
            UnifiedPlanningService planningService,
            WorkCalendarService calendarService
    ) {
        this.memberRepository = memberRepository;
        this.planningService = planningService;
        this.calendarService = calendarService;
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

        // Рассчитываем capacity по ролям
        RoleCapacity saCapacity = calculateRoleCapacity(members, Role.SA, workdays);
        RoleCapacity devCapacity = calculateRoleCapacity(members, Role.DEV, workdays);
        RoleCapacity qaCapacity = calculateRoleCapacity(members, Role.QA, workdays);

        // Получаем unified planning для расчёта assigned hours
        UnifiedPlanningResult planning = planningService.calculatePlan(teamId);

        // Рассчитываем assigned hours по ролям (только в пределах периода)
        RoleAssigned assigned = calculateAssignedHours(planning, today, periodEnd);

        // Создаём RoleLoadInfo для каждой роли
        RoleLoadInfo saInfo = createRoleLoadInfo(saCapacity, assigned.saHours);
        RoleLoadInfo devInfo = createRoleLoadInfo(devCapacity, assigned.devHours);
        RoleLoadInfo qaInfo = createRoleLoadInfo(qaCapacity, assigned.qaHours);

        // Генерируем алерты
        List<RoleLoadAlert> alerts = generateAlerts(saInfo, devInfo, qaInfo);

        log.info("Role load calculated for team {}: SA={}%, DEV={}%, QA={}%",
                teamId,
                saInfo.utilizationPercent(),
                devInfo.utilizationPercent(),
                qaInfo.utilizationPercent());

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
     * Рассчитывает capacity для роли.
     */
    private RoleCapacity calculateRoleCapacity(List<TeamMemberEntity> members, Role role, int workdays) {
        List<TeamMemberEntity> roleMembers = members.stream()
                .filter(m -> m.getRole() == role)
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
     */
    private RoleAssigned calculateAssignedHours(UnifiedPlanningResult planning, LocalDate periodStart, LocalDate periodEnd) {
        BigDecimal saHours = BigDecimal.ZERO;
        BigDecimal devHours = BigDecimal.ZERO;
        BigDecimal qaHours = BigDecimal.ZERO;

        for (PlannedEpic epic : planning.epics()) {
            for (PlannedStory story : epic.stories()) {
                // SA phase
                if (story.phases().sa() != null) {
                    saHours = saHours.add(
                            calculateHoursInPeriod(story.phases().sa(), periodStart, periodEnd)
                    );
                }
                // DEV phase
                if (story.phases().dev() != null) {
                    devHours = devHours.add(
                            calculateHoursInPeriod(story.phases().dev(), periodStart, periodEnd)
                    );
                }
                // QA phase
                if (story.phases().qa() != null) {
                    qaHours = qaHours.add(
                            calculateHoursInPeriod(story.phases().qa(), periodStart, periodEnd)
                    );
                }
            }
        }

        return new RoleAssigned(saHours, devHours, qaHours);
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
     * Генерирует алерты на основе RoleLoadInfo.
     */
    private List<RoleLoadAlert> generateAlerts(RoleLoadInfo sa, RoleLoadInfo dev, RoleLoadInfo qa) {
        List<RoleLoadAlert> alerts = new ArrayList<>();

        // Проверяем перегрузку
        if (sa.status() == UtilizationStatus.OVERLOAD) {
            alerts.add(new RoleLoadAlert(
                    AlertType.ROLE_OVERLOAD,
                    "SA",
                    String.format("SA перегружены: %s%%", sa.utilizationPercent())
            ));
        }
        if (dev.status() == UtilizationStatus.OVERLOAD) {
            alerts.add(new RoleLoadAlert(
                    AlertType.ROLE_OVERLOAD,
                    "DEV",
                    String.format("DEV перегружены: %s%%", dev.utilizationPercent())
            ));
        }
        if (qa.status() == UtilizationStatus.OVERLOAD) {
            alerts.add(new RoleLoadAlert(
                    AlertType.ROLE_OVERLOAD,
                    "QA",
                    String.format("QA перегружены: %s%%", qa.utilizationPercent())
            ));
        }

        // Проверяем простой
        if (sa.status() == UtilizationStatus.IDLE) {
            alerts.add(new RoleLoadAlert(
                    AlertType.ROLE_IDLE,
                    "SA",
                    String.format("SA недозагружены: %s%%", sa.utilizationPercent())
            ));
        }
        if (dev.status() == UtilizationStatus.IDLE) {
            alerts.add(new RoleLoadAlert(
                    AlertType.ROLE_IDLE,
                    "DEV",
                    String.format("DEV недозагружены: %s%%", dev.utilizationPercent())
            ));
        }
        if (qa.status() == UtilizationStatus.IDLE) {
            alerts.add(new RoleLoadAlert(
                    AlertType.ROLE_IDLE,
                    "QA",
                    String.format("QA недозагружены: %s%%", qa.utilizationPercent())
            ));
        }

        // Проверяем отсутствие capacity
        if (sa.status() == UtilizationStatus.NO_CAPACITY && sa.totalAssignedHours().compareTo(BigDecimal.ZERO) > 0) {
            alerts.add(new RoleLoadAlert(
                    AlertType.NO_CAPACITY,
                    "SA",
                    "Нет SA в команде, но есть назначенная работа"
            ));
        }
        if (dev.status() == UtilizationStatus.NO_CAPACITY && dev.totalAssignedHours().compareTo(BigDecimal.ZERO) > 0) {
            alerts.add(new RoleLoadAlert(
                    AlertType.NO_CAPACITY,
                    "DEV",
                    "Нет DEV в команде, но есть назначенная работа"
            ));
        }
        if (qa.status() == UtilizationStatus.NO_CAPACITY && qa.totalAssignedHours().compareTo(BigDecimal.ZERO) > 0) {
            alerts.add(new RoleLoadAlert(
                    AlertType.NO_CAPACITY,
                    "QA",
                    "Нет QA в команде, но есть назначенная работа"
            ));
        }

        // Проверяем дисбаланс между ролями (только для ролей с capacity)
        List<BigDecimal> activeUtilizations = new ArrayList<>();
        List<String> activeRoles = new ArrayList<>();

        if (sa.status() != UtilizationStatus.NO_CAPACITY) {
            activeUtilizations.add(sa.utilizationPercent());
            activeRoles.add("SA");
        }
        if (dev.status() != UtilizationStatus.NO_CAPACITY) {
            activeUtilizations.add(dev.utilizationPercent());
            activeRoles.add("DEV");
        }
        if (qa.status() != UtilizationStatus.NO_CAPACITY) {
            activeUtilizations.add(qa.utilizationPercent());
            activeRoles.add("QA");
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

    /**
     * Вспомогательный record для assigned hours.
     */
    private record RoleAssigned(BigDecimal saHours, BigDecimal devHours, BigDecimal qaHours) {}
}
