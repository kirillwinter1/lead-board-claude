package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.planning.dto.EpicForecast;
import com.leadboard.planning.dto.EpicForecast.*;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.ForecastResponse.TeamCapacity;
import com.leadboard.planning.dto.ForecastResponse.WipStatus;
import com.leadboard.planning.dto.ForecastResponse.RoleWipStatus;
import com.leadboard.quality.DataQualityService;
import com.leadboard.status.StatusMappingConfig;
import com.leadboard.status.StatusMappingService;
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
import java.util.Objects;
import java.util.stream.Stream;

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
    private final StatusMappingService statusMappingService;
    private final DataQualityService dataQualityService;

    public ForecastService(
            JiraIssueRepository issueRepository,
            TeamService teamService,
            TeamMemberRepository memberRepository,
            WorkCalendarService calendarService,
            StatusMappingService statusMappingService,
            DataQualityService dataQualityService
    ) {
        this.issueRepository = issueRepository;
        this.teamService = teamService;
        this.memberRepository = memberRepository;
        this.calendarService = calendarService;
        this.statusMappingService = statusMappingService;
        this.dataQualityService = dataQualityService;
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

        // Получаем StatusMappingConfig (для определения статусов)
        StatusMappingConfig statusMapping = config.statusMapping();

        // Получаем WIP лимиты
        PlanningConfigDto.WipLimits wipLimits = config.wipLimits() != null
                ? config.wipLimits()
                : PlanningConfigDto.WipLimits.defaults();

        int teamWipLimit = wipLimits.team() != null ? wipLimits.team() : 6;
        int saWipLimit = wipLimits.sa() != null ? wipLimits.sa() : 2;
        int devWipLimit = wipLimits.dev() != null ? wipLimits.dev() : 3;
        int qaWipLimit = wipLimits.qa() != null ? wipLimits.qa() : 2;

        // Рассчитываем capacity команды
        TeamCapacity teamCapacity = calculateTeamCapacity(teamId, config);

        // Получаем эпики отсортированные по AutoScore
        List<JiraIssueEntity> epics = getEpicsSorted(teamId, statuses);

        // Filter epics: only planning-allowed statuses and no blocking errors
        epics = epics.stream()
                .filter(e -> statusMappingService.isPlanningAllowed(e.getStatus(), statusMapping))
                .filter(e -> !dataQualityService.hasBlockingErrors(e, statusMapping))
                .toList();

        // Рассчитываем прогноз с учётом WIP лимитов (team + role-specific)
        WipCalculationResult wipResult = calculateForecastsWithWip(
                epics, teamCapacity, riskBuffer, storyDuration,
                teamWipLimit, saWipLimit, devWipLimit, qaWipLimit, statusMapping);

        // Формируем WIP статус с информацией по ролям
        int currentTeamWip = Math.min(epics.size(), teamWipLimit);
        WipStatus wipStatus = WipStatus.of(
                teamWipLimit, currentTeamWip,
                RoleWipStatus.of(saWipLimit, wipResult.saActive),
                RoleWipStatus.of(devWipLimit, wipResult.devActive),
                RoleWipStatus.of(qaWipLimit, wipResult.qaActive)
        );

        return new ForecastResponse(
                OffsetDateTime.now(),
                teamId,
                teamCapacity,
                wipStatus,
                wipResult.forecasts
        );
    }

    /**
     * Результат расчёта с WIP информацией.
     */
    private record WipCalculationResult(
            List<EpicForecast> forecasts,
            int saActive,      // Количество активных эпиков в SA фазе
            int devActive,     // Количество активных эпиков в DEV фазе
            int qaActive       // Количество активных эпиков в QA фазе
    ) {}

    /**
     * Рассчитывает прогнозы с учётом Pipeline WIP модели.
     *
     * PIPELINE WIP АЛГОРИТМ:
     * Каждая роль (SA/DEV/QA) работает независимо со своим WIP лимитом.
     * SA может начать Epic 2 сразу после завершения SA Epic 1, не дожидаясь DEV/QA.
     *
     * Принцип:
     * 1. SA имеет очередь эпиков ограниченную SA WIP
     * 2. DEV имеет свою очередь, эпик входит когда:
     *    - SA фаза этого эпика завершена (pipeline offset)
     *    - Есть свободный слот в DEV WIP
     * 3. QA аналогично - ждёт завершения DEV фазы эпика + слот в QA WIP
     *
     * Team WIP - это опциональное ограничение на общее количество "активных" эпиков
     * (эпик активен если хотя бы одна его фаза в работе)
     */
    private WipCalculationResult calculateForecastsWithWip(
            List<JiraIssueEntity> epics,
            TeamCapacity teamCapacity,
            BigDecimal riskBuffer,
            PlanningConfigDto.StoryDuration storyDuration,
            int teamWipLimit,
            int saWipLimit,
            int devWipLimit,
            int qaWipLimit,
            StatusMappingConfig statusMapping
    ) {
        if (epics.isEmpty()) {
            return new WipCalculationResult(new ArrayList<>(), 0, 0, 0);
        }

        List<EpicForecast> forecasts = new ArrayList<>();

        // Трекеры дат завершения для каждой роли (когда освободится слот)
        // PriorityQueue хранит даты завершения фаз, самая ранняя первой
        java.util.PriorityQueue<LocalDate> saPhaseEndDates = new java.util.PriorityQueue<>();
        java.util.PriorityQueue<LocalDate> devPhaseEndDates = new java.util.PriorityQueue<>();
        java.util.PriorityQueue<LocalDate> qaPhaseEndDates = new java.util.PriorityQueue<>();

        // Счётчики текущего WIP по ролям (на момент "сейчас")
        int saActive = 0;
        int devActive = 0;
        int qaActive = 0;

        LocalDate today = LocalDate.now();

        for (int i = 0; i < epics.size(); i++) {
            JiraIssueEntity epic = epics.get(i);

            // === SA ФАЗА (Pipeline независимый) ===
            // SA начинает когда есть свободный слот в SA WIP
            LocalDate saStartDate = today;
            EpicForecast.RoleWaitInfo saWait = EpicForecast.RoleWaitInfo.none();

            if (saPhaseEndDates.size() >= saWipLimit) {
                // Нет свободного слота, ждём завершения самой ранней SA фазы
                LocalDate saSlotAvailable = saPhaseEndDates.poll();
                if (saSlotAvailable.isAfter(saStartDate)) {
                    int saQueuePos = i - saWipLimit + 1;
                    saWait = EpicForecast.RoleWaitInfo.waiting(saSlotAvailable, Math.max(1, saQueuePos));
                    saStartDate = saSlotAvailable;
                }
            }

            // Рассчитываем SA фазу
            RemainingByRole remaining = calculateRemaining(epic, riskBuffer, statusMapping);
            PhaseInfo saPhase = calculatePhase(
                    remaining.sa().days(),
                    teamCapacity.saHoursPerDay(),
                    saStartDate
            );

            // === DEV ФАЗА (Pipeline от SA + DEV WIP) ===
            // DEV начинает когда:
            // 1. SA фаза этого эпика завершена (с pipeline offset)
            // 2. Есть свободный слот в DEV WIP
            LocalDate devEarliestByPipeline = today;
            if (saPhase != null && saPhase.endDate() != null && remaining.sa().days().compareTo(BigDecimal.ZERO) > 0) {
                // Pipeline offset: DEV может начать когда первая история SA готова
                // offsetDays-1 потому что calculatePhase добавит +1 к startAfter
                int offsetDays = calculateOffsetDays(storyDuration.sa(), teamCapacity.saHoursPerDay());
                LocalDate afterFirstStory = calendarService.addWorkdays(saPhase.startDate(), Math.max(0, offsetDays - 1));
                // Если SA короче storyDuration, DEV начинает сразу после SA
                devEarliestByPipeline = afterFirstStory.isAfter(saPhase.endDate())
                        ? saPhase.endDate()
                        : afterFirstStory;
            }

            LocalDate devStartDate = devEarliestByPipeline;
            EpicForecast.RoleWaitInfo devWait = EpicForecast.RoleWaitInfo.none();

            if (devPhaseEndDates.size() >= devWipLimit) {
                // Нет свободного слота, ждём завершения самой ранней DEV фазы
                LocalDate devSlotAvailable = devPhaseEndDates.poll();
                if (devSlotAvailable.isAfter(devStartDate)) {
                    int devQueuePos = i - devWipLimit + 1;
                    devWait = EpicForecast.RoleWaitInfo.waiting(devSlotAvailable, Math.max(1, devQueuePos));
                    devStartDate = devSlotAvailable;
                }
            }

            // Рассчитываем DEV фазу
            PhaseInfo devPhase = calculatePhase(
                    remaining.dev().days(),
                    teamCapacity.devHoursPerDay(),
                    devStartDate
            );

            // === QA ФАЗА (Pipeline от DEV + QA WIP) ===
            // QA начинает когда:
            // 1. DEV фаза этого эпика завершена (с pipeline offset)
            // 2. Есть свободный слот в QA WIP
            LocalDate qaEarliestByPipeline = today;
            if (devPhase != null && devPhase.endDate() != null && remaining.dev().days().compareTo(BigDecimal.ZERO) > 0) {
                // Pipeline offset: QA может начать когда первая история DEV готова
                // offsetDays-1 потому что calculatePhase добавит +1 к startAfter
                int offsetDays = calculateOffsetDays(storyDuration.dev(), teamCapacity.devHoursPerDay());
                LocalDate afterFirstStory = calendarService.addWorkdays(devPhase.startDate(), Math.max(0, offsetDays - 1));
                // Если DEV короче storyDuration, QA начинает сразу после DEV
                qaEarliestByPipeline = afterFirstStory.isAfter(devPhase.endDate())
                        ? devPhase.endDate()
                        : afterFirstStory;
            }

            LocalDate qaStartDate = qaEarliestByPipeline;
            EpicForecast.RoleWaitInfo qaWait = EpicForecast.RoleWaitInfo.none();

            if (qaPhaseEndDates.size() >= qaWipLimit) {
                // Нет свободного слота, ждём завершения самой ранней QA фазы
                LocalDate qaSlotAvailable = qaPhaseEndDates.poll();
                if (qaSlotAvailable.isAfter(qaStartDate)) {
                    int qaQueuePos = i - qaWipLimit + 1;
                    qaWait = EpicForecast.RoleWaitInfo.waiting(qaSlotAvailable, Math.max(1, qaQueuePos));
                    qaStartDate = qaSlotAvailable;
                }
            }

            // Рассчитываем QA фазу
            PhaseInfo qaPhase = calculatePhase(
                    remaining.qa().days(),
                    teamCapacity.qaHoursPerDay(),
                    qaStartDate
            );

            // Собираем расписание фаз
            PhaseSchedule schedule = new PhaseSchedule(saPhase, devPhase, qaPhase);

            // Определяем Expected Done (максимальная дата окончания среди фаз с реальной работой)
            LocalDate expectedDone = Stream.of(saPhase, devPhase, qaPhase)
                    .filter(phase -> phase != null && phase.workDays() != null
                            && phase.workDays().compareTo(BigDecimal.ZERO) > 0)
                    .map(PhaseInfo::endDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);

            // Fallback: если нет фаз с работой, берём любую ненулевую дату
            if (expectedDone == null) {
                expectedDone = Stream.of(qaPhase, devPhase, saPhase)
                        .filter(phase -> phase != null && phase.endDate() != null)
                        .map(PhaseInfo::endDate)
                        .findFirst()
                        .orElse(null);
            }

            // === Team WIP (опционально) ===
            // Эпик считается "в WIP" если SA фаза не ждёт (т.е. он уже начался или начнётся сразу)
            boolean isWithinTeamWip = i < teamWipLimit;
            Integer queuePosition = isWithinTeamWip ? null : (i - teamWipLimit + 1);
            LocalDate queuedUntil = isWithinTeamWip ? null : (saWait.waiting() ? saWait.waitingUntil() : null);

            // Рассчитываем дельту от due date
            Integer dueDateDelta = calculateDueDateDelta(expectedDone, epic.getDueDate());

            // Определяем уровень уверенности
            Confidence confidence = calculateConfidence(epic, remaining, teamCapacity);

            // Создаём PhaseWaitInfo
            EpicForecast.PhaseWaitInfo phaseWaitInfo = new EpicForecast.PhaseWaitInfo(saWait, devWait, qaWait);

            // Создаём forecast
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
                    schedule,
                    queuePosition,
                    queuedUntil,
                    isWithinTeamWip,
                    phaseWaitInfo
            );

            forecasts.add(forecast);

            // === Обновляем трекеры для следующих эпиков ===
            if (saPhase != null && saPhase.endDate() != null) {
                saPhaseEndDates.add(saPhase.endDate());
                // Считаем активных на сегодня
                if (saPhase.startDate() != null && !saPhase.startDate().isAfter(today)
                        && !saPhase.endDate().isBefore(today)) {
                    saActive++;
                }
            }

            if (devPhase != null && devPhase.endDate() != null) {
                devPhaseEndDates.add(devPhase.endDate());
                if (devPhase.startDate() != null && !devPhase.startDate().isAfter(today)
                        && !devPhase.endDate().isBefore(today)) {
                    devActive++;
                }
            }

            if (qaPhase != null && qaPhase.endDate() != null) {
                qaPhaseEndDates.add(qaPhase.endDate());
                if (qaPhase.startDate() != null && !qaPhase.startDate().isAfter(today)
                        && !qaPhase.endDate().isBefore(today)) {
                    qaActive++;
                }
            }
        }

        return new WipCalculationResult(forecasts, saActive, devActive, qaActive);
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
     * Рассчитывает остаток работы по ролям.
     * Приоритет источников данных:
     * 1. Rough estimate на эпике (если есть)
     * 2. Агрегация по child issues (stories/tasks) с учётом статусов и time spent
     * 3. Original estimate на эпике (fallback)
     */
    private RemainingByRole calculateRemaining(JiraIssueEntity epic, BigDecimal riskBuffer, StatusMappingConfig statusMapping) {
        // 1. Используем rough estimate если есть
        BigDecimal saDays = epic.getRoughEstimateSaDays();
        BigDecimal devDays = epic.getRoughEstimateDevDays();
        BigDecimal qaDays = epic.getRoughEstimateQaDays();

        // 2. Если нет rough estimate, агрегируем по child issues
        if (saDays == null && devDays == null && qaDays == null) {
            List<JiraIssueEntity> childIssues = issueRepository.findByParentKey(epic.getIssueKey());

            if (!childIssues.isEmpty()) {
                // Считаем remaining по каждой фазе из child issues
                long saRemainingSeconds = 0;
                long devRemainingSeconds = 0;
                long qaRemainingSeconds = 0;

                for (JiraIssueEntity child : childIssues) {
                    // Пропускаем Done задачи - они уже выполнены
                    if (statusMappingService.isDone(child.getStatus(), statusMapping)) {
                        continue;
                    }

                    // Рассчитываем remaining для этой задачи
                    long estimate = child.getOriginalEstimateSeconds() != null ? child.getOriginalEstimateSeconds() : 0;
                    long spent = child.getTimeSpentSeconds() != null ? child.getTimeSpentSeconds() : 0;
                    long remaining = Math.max(0, estimate - spent);

                    // Если нет эстимейта, но есть залогированное время - оцениваем что осталось ещё столько же
                    if (estimate == 0 && spent > 0 && !statusMappingService.isInProgress(child.getStatus(), statusMapping)) {
                        remaining = spent; // Fallback: предполагаем что осталось примерно столько же
                    }

                    // Определяем фазу по типу задачи или статусу
                    String phase = statusMappingService.determinePhase(child.getStatus(), child.getIssueType(), statusMapping);

                    switch (phase) {
                        case "SA" -> saRemainingSeconds += remaining;
                        case "QA" -> qaRemainingSeconds += remaining;
                        default -> devRemainingSeconds += remaining;
                    }
                }

                // Конвертируем секунды в дни
                if (saRemainingSeconds > 0 || devRemainingSeconds > 0 || qaRemainingSeconds > 0) {
                    saDays = BigDecimal.valueOf(saRemainingSeconds / 3600.0).divide(HOURS_PER_DAY, 1, RoundingMode.HALF_UP);
                    devDays = BigDecimal.valueOf(devRemainingSeconds / 3600.0).divide(HOURS_PER_DAY, 1, RoundingMode.HALF_UP);
                    qaDays = BigDecimal.valueOf(qaRemainingSeconds / 3600.0).divide(HOURS_PER_DAY, 1, RoundingMode.HALF_UP);
                }
            }
        }

        // 3. Fallback: original estimate на эпике
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
}
