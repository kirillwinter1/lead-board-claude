package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.planning.dto.EpicForecast;
import com.leadboard.planning.dto.EpicForecast.*;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.ForecastResponse.TeamCapacity;
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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис расчёта прогноза завершения эпиков.
 *
 * Алгоритм:
 * 1. Получить эпики команды отсортированные по AutoScore
 * 2. Для каждого эпика рассчитать остаток работы по ролям
 * 3. Последовательно распределить capacity: SA → DEV → QA
 * 4. Рассчитать даты завершения с учётом рабочего календаря
 * 5. Определить уровень уверенности
 */
@Service
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);
    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("8");
    private static final List<String> EPIC_TYPES = List.of("Epic", "Эпик");

    private final JiraIssueRepository issueRepository;
    private final TeamService teamService;
    private final TeamMemberRepository memberRepository;
    private final WorkCalendarService calendarService;

    public ForecastService(
            JiraIssueRepository issueRepository,
            TeamService teamService,
            TeamMemberRepository memberRepository,
            WorkCalendarService calendarService
    ) {
        this.issueRepository = issueRepository;
        this.teamService = teamService;
        this.memberRepository = memberRepository;
        this.calendarService = calendarService;
    }

    /**
     * Рассчитывает прогноз для всех эпиков команды.
     */
    public ForecastResponse calculateForecast(Long teamId) {
        return calculateForecast(teamId, null);
    }

    /**
     * Рассчитывает прогноз для эпиков команды с фильтрацией по статусам.
     */
    public ForecastResponse calculateForecast(Long teamId, List<String> statuses) {
        // Получаем конфигурацию планирования
        PlanningConfigDto config = teamService.getPlanningConfig(teamId);
        BigDecimal riskBuffer = config.riskBuffer() != null ? config.riskBuffer() : new BigDecimal("0.2");

        // Рассчитываем capacity команды
        TeamCapacity teamCapacity = calculateTeamCapacity(teamId, config);

        // Получаем эпики отсортированные по AutoScore
        List<JiraIssueEntity> epics = getEpicsSorted(teamId, statuses);

        // Рассчитываем прогноз для каждого эпика
        List<EpicForecast> forecasts = new ArrayList<>();
        LocalDate currentSaEndDate = LocalDate.now();
        LocalDate currentDevEndDate = LocalDate.now();
        LocalDate currentQaEndDate = LocalDate.now();

        for (JiraIssueEntity epic : epics) {
            EpicForecastCalculation calc = calculateEpicForecast(
                    epic,
                    teamCapacity,
                    riskBuffer,
                    currentSaEndDate,
                    currentDevEndDate,
                    currentQaEndDate
            );

            forecasts.add(calc.forecast);

            // Обновляем текущие даты окончания для следующего эпика
            // (для упрощения MVP - последовательная обработка)
            if (calc.forecast.phaseSchedule() != null) {
                PhaseSchedule schedule = calc.forecast.phaseSchedule();
                if (schedule.sa() != null && schedule.sa().endDate() != null) {
                    currentSaEndDate = maxDate(currentSaEndDate, schedule.sa().endDate());
                }
                if (schedule.dev() != null && schedule.dev().endDate() != null) {
                    currentDevEndDate = maxDate(currentDevEndDate, schedule.dev().endDate());
                }
                if (schedule.qa() != null && schedule.qa().endDate() != null) {
                    currentQaEndDate = maxDate(currentQaEndDate, schedule.qa().endDate());
                }
            }
        }

        return new ForecastResponse(
                OffsetDateTime.now(),
                teamId,
                teamCapacity,
                forecasts
        );
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
     * Рассчитывает прогноз для одного эпика.
     */
    private EpicForecastCalculation calculateEpicForecast(
            JiraIssueEntity epic,
            TeamCapacity capacity,
            BigDecimal riskBuffer,
            LocalDate saStartAfter,
            LocalDate devStartAfter,
            LocalDate qaStartAfter
    ) {
        // Получаем остаток работы
        RemainingByRole remaining = calculateRemaining(epic, riskBuffer);

        // Рассчитываем расписание фаз: SA → DEV → QA
        PhaseSchedule schedule = calculatePhaseSchedule(
                remaining,
                capacity,
                saStartAfter,
                devStartAfter,
                qaStartAfter
        );

        // Определяем Expected Done (конец QA фазы)
        LocalDate expectedDone = null;
        if (schedule != null && schedule.qa() != null) {
            expectedDone = schedule.qa().endDate();
        } else if (schedule != null && schedule.dev() != null) {
            expectedDone = schedule.dev().endDate();
        } else if (schedule != null && schedule.sa() != null) {
            expectedDone = schedule.sa().endDate();
        }

        // Рассчитываем дельту от due date
        Integer dueDateDelta = calculateDueDateDelta(expectedDone, epic.getDueDate());

        // Определяем уровень уверенности
        Confidence confidence = calculateConfidence(epic, remaining, capacity);

        EpicForecast forecast = new EpicForecast(
                epic.getIssueKey(),
                epic.getSummary(),
                epic.getAutoScore(),
                epic.getManualPriorityBoost(),
                expectedDone,
                confidence,
                dueDateDelta,
                epic.getDueDate(),
                remaining,
                schedule
        );

        return new EpicForecastCalculation(forecast);
    }

    /**
     * Рассчитывает остаток работы по ролям.
     */
    private RemainingByRole calculateRemaining(JiraIssueEntity epic, BigDecimal riskBuffer) {
        // Используем rough estimate если есть
        BigDecimal saDays = epic.getRoughEstimateSaDays();
        BigDecimal devDays = epic.getRoughEstimateDevDays();
        BigDecimal qaDays = epic.getRoughEstimateQaDays();

        // Если нет rough estimate, пробуем использовать original estimate
        if (saDays == null && devDays == null && qaDays == null) {
            Long estimateSeconds = epic.getOriginalEstimateSeconds();
            Long spentSeconds = epic.getTimeSpentSeconds();

            if (estimateSeconds != null && estimateSeconds > 0) {
                long remaining = estimateSeconds - (spentSeconds != null ? spentSeconds : 0);
                if (remaining > 0) {
                    // Распределяем по умолчанию: 10% SA, 70% DEV, 20% QA
                    BigDecimal totalHours = BigDecimal.valueOf(remaining / 3600.0);
                    BigDecimal saHours = totalHours.multiply(new BigDecimal("0.10"));
                    BigDecimal devHours = totalHours.multiply(new BigDecimal("0.70"));
                    BigDecimal qaHours = totalHours.multiply(new BigDecimal("0.20"));

                    saDays = saHours.divide(HOURS_PER_DAY, 1, RoundingMode.HALF_UP);
                    devDays = devHours.divide(HOURS_PER_DAY, 1, RoundingMode.HALF_UP);
                    qaDays = qaHours.divide(HOURS_PER_DAY, 1, RoundingMode.HALF_UP);
                }
            }
        }

        // Применяем буфер рисков
        BigDecimal multiplier = BigDecimal.ONE.add(riskBuffer);

        BigDecimal saFinal = applyRiskBuffer(saDays, multiplier);
        BigDecimal devFinal = applyRiskBuffer(devDays, multiplier);
        BigDecimal qaFinal = applyRiskBuffer(qaDays, multiplier);

        return new RemainingByRole(
                new RoleRemaining(saFinal.multiply(HOURS_PER_DAY), saFinal),
                new RoleRemaining(devFinal.multiply(HOURS_PER_DAY), devFinal),
                new RoleRemaining(qaFinal.multiply(HOURS_PER_DAY), qaFinal)
        );
    }

    private BigDecimal applyRiskBuffer(BigDecimal days, BigDecimal multiplier) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return days.multiply(multiplier).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Рассчитывает расписание фаз.
     * Последовательность: SA → DEV → QA
     */
    private PhaseSchedule calculatePhaseSchedule(
            RemainingByRole remaining,
            TeamCapacity capacity,
            LocalDate saStartAfter,
            LocalDate devStartAfter,
            LocalDate qaStartAfter
    ) {
        // SA фаза
        PhaseInfo saPhase = calculatePhase(
                remaining.sa().days(),
                capacity.saHoursPerDay(),
                saStartAfter
        );

        // DEV фаза начинается после SA
        LocalDate devStart = saPhase != null && saPhase.endDate() != null
                ? maxDate(devStartAfter, saPhase.endDate())
                : devStartAfter;
        PhaseInfo devPhase = calculatePhase(
                remaining.dev().days(),
                capacity.devHoursPerDay(),
                devStart
        );

        // QA фаза начинается после DEV
        LocalDate qaStart = devPhase != null && devPhase.endDate() != null
                ? maxDate(qaStartAfter, devPhase.endDate())
                : qaStartAfter;
        PhaseInfo qaPhase = calculatePhase(
                remaining.qa().days(),
                capacity.qaHoursPerDay(),
                qaStart
        );

        return new PhaseSchedule(saPhase, devPhase, qaPhase);
    }

    /**
     * Рассчитывает информацию о фазе.
     */
    private PhaseInfo calculatePhase(BigDecimal workDays, BigDecimal capacityPerDay, LocalDate startAfter) {
        if (workDays == null || workDays.compareTo(BigDecimal.ZERO) <= 0) {
            return new PhaseInfo(startAfter, startAfter, BigDecimal.ZERO);
        }

        if (capacityPerDay == null || capacityPerDay.compareTo(BigDecimal.ZERO) <= 0) {
            // Нет ресурсов - не можем рассчитать
            return new PhaseInfo(startAfter, null, workDays);
        }

        // Сколько рабочих дней нужно
        // workDays - это человеко-дни, capacityPerDay - часов в день
        // Конвертируем: дни * 8 часов / часов в день = календарных рабочих дней
        BigDecimal hoursNeeded = workDays.multiply(HOURS_PER_DAY);
        int calendarDaysNeeded = hoursNeeded
                .divide(capacityPerDay, 0, RoundingMode.CEILING)
                .intValue();

        LocalDate startDate = calendarService.addWorkdays(startAfter, 1);
        LocalDate endDate = calendarService.addWorkdays(startDate, Math.max(0, calendarDaysNeeded - 1));

        return new PhaseInfo(startDate, endDate, workDays);
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

    /**
     * Определяет уровень уверенности в прогнозе.
     */
    private Confidence calculateConfidence(JiraIssueEntity epic, RemainingByRole remaining, TeamCapacity capacity) {
        int issues = 0;

        // Нет оценок
        boolean hasEstimates = remaining.sa().days().compareTo(BigDecimal.ZERO) > 0
                || remaining.dev().days().compareTo(BigDecimal.ZERO) > 0
                || remaining.qa().days().compareTo(BigDecimal.ZERO) > 0;
        if (!hasEstimates) {
            return Confidence.LOW;
        }

        // Проверяем наличие ресурсов для каждой роли с работой
        if (remaining.sa().days().compareTo(BigDecimal.ZERO) > 0
                && (capacity.saHoursPerDay() == null || capacity.saHoursPerDay().compareTo(BigDecimal.ZERO) <= 0)) {
            issues++;
        }
        if (remaining.dev().days().compareTo(BigDecimal.ZERO) > 0
                && (capacity.devHoursPerDay() == null || capacity.devHoursPerDay().compareTo(BigDecimal.ZERO) <= 0)) {
            issues++;
        }
        if (remaining.qa().days().compareTo(BigDecimal.ZERO) > 0
                && (capacity.qaHoursPerDay() == null || capacity.qaHoursPerDay().compareTo(BigDecimal.ZERO) <= 0)) {
            issues++;
        }

        if (issues >= 2) {
            return Confidence.LOW;
        }
        if (issues == 1) {
            return Confidence.MEDIUM;
        }

        // Есть rough estimate - высокая уверенность
        if (epic.getRoughEstimateSaDays() != null
                || epic.getRoughEstimateDevDays() != null
                || epic.getRoughEstimateQaDays() != null) {
            return Confidence.HIGH;
        }

        return Confidence.MEDIUM;
    }

    /**
     * Получает эпики команды отсортированные по AutoScore.
     */
    private List<JiraIssueEntity> getEpicsSorted(Long teamId, List<String> statuses) {
        if (statuses != null && !statuses.isEmpty()) {
            return issueRepository.findByIssueTypeInAndTeamIdAndStatusInOrderByAutoScoreDesc(
                    EPIC_TYPES, teamId, statuses);
        }
        return issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(EPIC_TYPES, teamId);
    }

    private LocalDate maxDate(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    /**
     * Внутренний класс для результата расчёта.
     */
    private record EpicForecastCalculation(EpicForecast forecast) {}
}
