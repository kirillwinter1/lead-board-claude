package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.planning.dto.EpicForecast;
import com.leadboard.planning.dto.EpicForecast.*;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.ForecastResponse.TeamCapacity;
import com.leadboard.planning.dto.ForecastResponse.WipStatus;
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
 * Алгоритм (с учётом WIP лимитов):
 * 1. Получить эпики команды отсортированные по AutoScore
 * 2. Получить WIP лимит команды
 * 3. Первые N эпиков (N = WIP лимит) начинают сразу, capacity делится между ними
 * 4. Эпики за пределами WIP ждут в очереди пока не освободится слот
 * 5. Для каждого эпика:
 *    - Рассчитать остаток работы по ролям
 *    - Распределить capacity по конвейерной модели (pipeline)
 *    - Рассчитать даты завершения с учётом рабочего календаря
 * 6. Определить уровень уверенности
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
        PlanningConfigDto.StoryDuration storyDuration = config.storyDuration() != null
                ? config.storyDuration()
                : PlanningConfigDto.StoryDuration.defaults();

        // Получаем WIP лимит
        int wipLimit = config.wipLimits() != null && config.wipLimits().team() != null
                ? config.wipLimits().team()
                : 6; // default

        // Рассчитываем capacity команды
        TeamCapacity teamCapacity = calculateTeamCapacity(teamId, config);

        // Получаем эпики отсортированные по AutoScore
        List<JiraIssueEntity> epics = getEpicsSorted(teamId, statuses);

        // Рассчитываем прогноз с учётом WIP лимитов
        List<EpicForecast> forecasts = calculateForecastsWithWip(
                epics, teamCapacity, riskBuffer, storyDuration, wipLimit);

        // Считаем текущий WIP (эпики в работе)
        int currentWip = Math.min(epics.size(), wipLimit);
        WipStatus wipStatus = WipStatus.of(wipLimit, currentWip);

        return new ForecastResponse(
                OffsetDateTime.now(),
                teamId,
                teamCapacity,
                wipStatus,
                forecasts
        );
    }

    /**
     * Рассчитывает прогнозы с учётом WIP лимитов.
     *
     * Алгоритм:
     * 1. Первые N эпиков (N = wipLimit) работаются параллельно
     * 2. Эпики за пределами WIP ждут пока освободится слот
     * 3. Когда эпик из WIP завершается, следующий из очереди занимает его место
     */
    private List<EpicForecast> calculateForecastsWithWip(
            List<JiraIssueEntity> epics,
            TeamCapacity teamCapacity,
            BigDecimal riskBuffer,
            PlanningConfigDto.StoryDuration storyDuration,
            int wipLimit
    ) {
        if (epics.isEmpty()) {
            return new ArrayList<>();
        }

        List<EpicForecast> forecasts = new ArrayList<>();

        // Трекер дат завершения активных эпиков (для определения когда освободится слот)
        // Используем PriorityQueue для эффективного получения ближайшей даты завершения
        java.util.PriorityQueue<LocalDate> activeEpicEndDates = new java.util.PriorityQueue<>();

        LocalDate currentSaEndDate = LocalDate.now();
        LocalDate currentDevEndDate = LocalDate.now();
        LocalDate currentQaEndDate = LocalDate.now();

        for (int i = 0; i < epics.size(); i++) {
            JiraIssueEntity epic = epics.get(i);
            boolean isWithinWip = i < wipLimit;
            Integer queuePosition = isWithinWip ? null : (i - wipLimit + 1);
            LocalDate queuedUntil = null;

            // Если эпик за пределами WIP, нужно ждать освобождения слота
            if (!isWithinWip && !activeEpicEndDates.isEmpty()) {
                // Ждём пока освободится слот (ближайший эпик завершится)
                queuedUntil = activeEpicEndDates.poll();

                // Сдвигаем начальные даты на момент освобождения слота
                currentSaEndDate = maxDate(currentSaEndDate, queuedUntil);
                currentDevEndDate = maxDate(currentDevEndDate, queuedUntil);
                currentQaEndDate = maxDate(currentQaEndDate, queuedUntil);
            }

            EpicForecastCalculation calc = calculateEpicForecast(
                    epic,
                    teamCapacity,
                    riskBuffer,
                    storyDuration,
                    currentSaEndDate,
                    currentDevEndDate,
                    currentQaEndDate
            );

            // Создаём forecast с WIP информацией
            EpicForecast forecastWithWip = new EpicForecast(
                    calc.forecast.epicKey(),
                    calc.forecast.summary(),
                    calc.forecast.autoScore(),
                    calc.forecast.manualPriorityBoost(),
                    calc.forecast.expectedDone(),
                    calc.forecast.confidence(),
                    calc.forecast.dueDateDeltaDays(),
                    calc.forecast.dueDate(),
                    calc.forecast.remainingByRole(),
                    calc.forecast.phaseSchedule(),
                    queuePosition,
                    queuedUntil,
                    isWithinWip
            );

            forecasts.add(forecastWithWip);

            // Добавляем дату завершения в очередь активных
            if (forecastWithWip.expectedDone() != null) {
                activeEpicEndDates.add(forecastWithWip.expectedDone());
            }

            // Обновляем текущие даты окончания для следующего эпика
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

        return forecasts;
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
            PlanningConfigDto.StoryDuration storyDuration,
            LocalDate saStartAfter,
            LocalDate devStartAfter,
            LocalDate qaStartAfter
    ) {
        // Получаем остаток работы
        RemainingByRole remaining = calculateRemaining(epic, riskBuffer);

        // Рассчитываем расписание фаз по конвейерной модели (pipeline)
        PhaseSchedule schedule = calculatePhaseSchedule(
                remaining,
                capacity,
                storyDuration,
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
     * Рассчитывает расписание фаз по конвейерной модели (pipeline).
     *
     * В отличие от последовательной модели (SA весь эпик → DEV весь эпик → QA весь эпик),
     * здесь работа идёт по сторям параллельно:
     * - SA начинает работу над эпиком
     * - DEV начинает после завершения первой стори SA (offset = storyDuration.sa)
     * - QA начинает после завершения первой стори DEV (offset = storyDuration.dev)
     *
     * Это более реалистичная модель, так как команда работает над сторями,
     * а не ждёт завершения всей аналитики перед началом разработки.
     */
    private PhaseSchedule calculatePhaseSchedule(
            RemainingByRole remaining,
            TeamCapacity capacity,
            PlanningConfigDto.StoryDuration storyDuration,
            LocalDate saStartAfter,
            LocalDate devStartAfter,
            LocalDate qaStartAfter
    ) {
        // SA фаза - начинается сразу
        PhaseInfo saPhase = calculatePhase(
                remaining.sa().days(),
                capacity.saHoursPerDay(),
                saStartAfter
        );

        // DEV фаза - начинается после завершения первой стори SA
        // offset = время на одну сторю SA (storyDuration.sa) в календарных днях
        LocalDate devStart;
        if (saPhase != null && saPhase.startDate() != null && remaining.sa().days().compareTo(BigDecimal.ZERO) > 0) {
            // DEV начинает через storyDuration.sa дней после начала SA
            int offsetDays = calculateOffsetDays(storyDuration.sa(), capacity.saHoursPerDay());
            LocalDate devStartByPipeline = calendarService.addWorkdays(saPhase.startDate(), offsetDays);
            devStart = maxDate(devStartAfter, devStartByPipeline);
        } else {
            devStart = devStartAfter;
        }
        PhaseInfo devPhase = calculatePhase(
                remaining.dev().days(),
                capacity.devHoursPerDay(),
                devStart
        );

        // QA фаза - начинается после завершения первой стори DEV
        // offset = время на одну сторю DEV (storyDuration.dev) в календарных днях
        LocalDate qaStart;
        if (devPhase != null && devPhase.startDate() != null && remaining.dev().days().compareTo(BigDecimal.ZERO) > 0) {
            // QA начинает через storyDuration.dev дней после начала DEV
            int offsetDays = calculateOffsetDays(storyDuration.dev(), capacity.devHoursPerDay());
            LocalDate qaStartByPipeline = calendarService.addWorkdays(devPhase.startDate(), offsetDays);
            qaStart = maxDate(qaStartAfter, qaStartByPipeline);
        } else {
            qaStart = qaStartAfter;
        }
        PhaseInfo qaPhase = calculatePhase(
                remaining.qa().days(),
                capacity.qaHoursPerDay(),
                qaStart
        );

        return new PhaseSchedule(saPhase, devPhase, qaPhase);
    }

    /**
     * Рассчитывает offset в календарных днях для pipeline.
     * storyDuration - человеко-дни на одну сторю
     * capacityPerDay - часов в день у команды для этой роли
     */
    private int calculateOffsetDays(BigDecimal storyDuration, BigDecimal capacityPerDay) {
        if (storyDuration == null || storyDuration.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        if (capacityPerDay == null || capacityPerDay.compareTo(BigDecimal.ZERO) <= 0) {
            return storyDuration.intValue(); // Если нет capacity, используем как есть
        }
        // Конвертируем человеко-дни в календарные дни
        BigDecimal hoursNeeded = storyDuration.multiply(HOURS_PER_DAY);
        return hoursNeeded.divide(capacityPerDay, 0, RoundingMode.CEILING).intValue();
    }

    /**
     * Рассчитывает информацию о фазе.
     */
    private PhaseInfo calculatePhase(BigDecimal workDays, BigDecimal capacityPerDay, LocalDate startAfter) {
        if (workDays == null || workDays.compareTo(BigDecimal.ZERO) <= 0) {
            return new PhaseInfo(startAfter, startAfter, BigDecimal.ZERO, false);
        }

        boolean noCapacity = capacityPerDay == null || capacityPerDay.compareTo(BigDecimal.ZERO) <= 0;

        // Если нет ресурсов, используем fallback: 8 часов/день (1 человек)
        BigDecimal effectiveCapacity = noCapacity ? HOURS_PER_DAY : capacityPerDay;

        // Сколько рабочих дней нужно
        // workDays - это человеко-дни, capacityPerDay - часов в день
        // Конвертируем: дни * 8 часов / часов в день = календарных рабочих дней
        BigDecimal hoursNeeded = workDays.multiply(HOURS_PER_DAY);
        int calendarDaysNeeded = hoursNeeded
                .divide(effectiveCapacity, 0, RoundingMode.CEILING)
                .intValue();

        LocalDate startDate = calendarService.addWorkdays(startAfter, 1);
        LocalDate endDate = calendarService.addWorkdays(startDate, Math.max(0, calendarDaysNeeded - 1));

        return new PhaseInfo(startDate, endDate, workDays, noCapacity);
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
