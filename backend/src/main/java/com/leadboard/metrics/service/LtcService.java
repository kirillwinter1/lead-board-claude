package com.leadboard.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.EpicLtc;
import com.leadboard.metrics.dto.LtcResponse;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class LtcService {

    private static final Logger log = LoggerFactory.getLogger(LtcService.class);
    private static final BigDecimal ON_TIME_THRESHOLD = new BigDecimal("1.1");

    private final JiraIssueRepository issueRepository;
    private final WorkCalendarService workCalendarService;
    private final ForecastSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public LtcService(
            JiraIssueRepository issueRepository,
            WorkCalendarService workCalendarService,
            ForecastSnapshotRepository snapshotRepository
    ) {
        this.issueRepository = issueRepository;
        this.workCalendarService = workCalendarService;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public LtcResponse calculateLtc(Long teamId, LocalDate from, LocalDate to) {
        List<JiraIssueEntity> completedEpics = issueRepository.findCompletedEpicsInPeriod(
                teamId,
                from.atStartOfDay().atOffset(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        );

        log.info("LTC: Found {} completed epics for team {} between {} and {}",
                completedEpics.size(), teamId, from, to);

        if (completedEpics.isEmpty()) {
            return new LtcResponse(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, List.of());
        }

        // Load forecast snapshots
        LocalDate snapshotFrom = from.minusDays(180);
        List<ForecastSnapshotEntity> snapshots = snapshotRepository.findByTeamIdAndDateRange(teamId, snapshotFrom, to);
        Map<LocalDate, UnifiedPlanningResult> snapshotsByDate = parseSnapshots(snapshots);

        List<EpicLtc> epicLtcs = new ArrayList<>();
        BigDecimal totalLtcActual = BigDecimal.ZERO;
        BigDecimal totalLtcForecast = BigDecimal.ZERO;
        int ltcActualCount = 0;
        int ltcForecastCount = 0;
        int onTimeCount = 0;

        for (JiraIssueEntity epic : completedEpics) {
            EpicLtc ltc = calculateEpicLtc(epic, snapshotsByDate);
            if (ltc != null) {
                epicLtcs.add(ltc);
                if (ltc.ltcActual() != null) {
                    totalLtcActual = totalLtcActual.add(ltc.ltcActual());
                    ltcActualCount++;
                    if (ltc.ltcActual().compareTo(ON_TIME_THRESHOLD) <= 0) {
                        onTimeCount++;
                    }
                }
                if (ltc.ltcForecast() != null) {
                    totalLtcForecast = totalLtcForecast.add(ltc.ltcForecast());
                    ltcForecastCount++;
                }
            }
        }

        int totalEpics = epicLtcs.size();
        BigDecimal avgLtcActual = ltcActualCount > 0
                ? totalLtcActual.divide(BigDecimal.valueOf(ltcActualCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgLtcForecast = ltcForecastCount > 0
                ? totalLtcForecast.divide(BigDecimal.valueOf(ltcForecastCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal onTimeRate = totalEpics > 0
                ? BigDecimal.valueOf(onTimeCount * 100.0 / totalEpics).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new LtcResponse(avgLtcActual, avgLtcForecast, totalEpics, onTimeCount, onTimeRate, epicLtcs);
    }

    private EpicLtc calculateEpicLtc(JiraIssueEntity epic, Map<LocalDate, UnifiedPlanningResult> snapshotsByDate) {
        if (epic.getDoneAt() == null) return null;

        LocalDate doneDate = epic.getDoneAt().toLocalDate();
        LocalDate startDate = epic.getStartedAt() != null
                ? epic.getStartedAt().toLocalDate()
                : (epic.getJiraCreatedAt() != null ? epic.getJiraCreatedAt().toLocalDate() : null);

        if (startDate == null) return null;

        int workingDays = workCalendarService.countWorkdays(startDate, doneDate);
        if (workingDays <= 0) workingDays = 1;

        // Calculate estimateDays from subtasks
        BigDecimal estimateDays = calculateEstimateDays(epic);

        // Calculate forecastDays from snapshots
        BigDecimal forecastDays = calculateForecastDays(epic, snapshotsByDate);

        BigDecimal ltcActual = estimateDays != null && estimateDays.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(workingDays).divide(estimateDays, 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal ltcForecast = forecastDays != null && forecastDays.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(workingDays).divide(forecastDays, 2, RoundingMode.HALF_UP)
                : null;

        return new EpicLtc(
                epic.getIssueKey(),
                epic.getSummary(),
                workingDays,
                estimateDays,
                forecastDays,
                ltcActual,
                ltcForecast
        );
    }

    private BigDecimal calculateEstimateDays(JiraIssueEntity epic) {
        // Sum subtask estimates
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(epic.getIssueKey());
        long totalEstimateSeconds = subtasks.stream()
                .mapToLong(st -> st.getOriginalEstimateSeconds() != null ? st.getOriginalEstimateSeconds() : 0)
                .sum();

        // Fallback to epic's own estimate
        if (totalEstimateSeconds == 0 && epic.getOriginalEstimateSeconds() != null) {
            totalEstimateSeconds = epic.getOriginalEstimateSeconds();
        }

        if (totalEstimateSeconds <= 0) return null;

        return BigDecimal.valueOf(totalEstimateSeconds)
                .divide(BigDecimal.valueOf(3600 * 8), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateForecastDays(JiraIssueEntity epic, Map<LocalDate, UnifiedPlanningResult> snapshotsByDate) {
        for (Map.Entry<LocalDate, UnifiedPlanningResult> entry : snapshotsByDate.entrySet()) {
            UnifiedPlanningResult plan = entry.getValue();
            Optional<UnifiedPlanningResult.PlannedEpic> plannedEpic = plan.epics().stream()
                    .filter(e -> e.epicKey().equals(epic.getIssueKey()))
                    .findFirst();

            if (plannedEpic.isPresent()) {
                UnifiedPlanningResult.PlannedEpic pe = plannedEpic.get();
                if (pe.startDate() != null && pe.endDate() != null) {
                    int forecastWorkdays = workCalendarService.countWorkdays(pe.startDate(), pe.endDate());
                    if (forecastWorkdays > 0) {
                        return BigDecimal.valueOf(forecastWorkdays);
                    }
                }
            }
        }
        return null;
    }

    private Map<LocalDate, UnifiedPlanningResult> parseSnapshots(List<ForecastSnapshotEntity> snapshots) {
        Map<LocalDate, UnifiedPlanningResult> result = new LinkedHashMap<>();
        for (ForecastSnapshotEntity snapshot : snapshots) {
            try {
                UnifiedPlanningResult plan = objectMapper.readValue(
                        snapshot.getUnifiedPlanningJson(), UnifiedPlanningResult.class
                );
                result.put(snapshot.getSnapshotDate(), plan);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse snapshot for date {}: {}", snapshot.getSnapshotDate(), e.getMessage());
            }
        }
        return result;
    }
}
