package com.leadboard.metrics.service;

import com.leadboard.metrics.dto.*;
import com.leadboard.metrics.repository.MetricsQueryRepository;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeamMetricsService {

    private static final Logger log = LoggerFactory.getLogger(TeamMetricsService.class);

    private final MetricsQueryRepository metricsRepository;
    private final StatusChangelogRepository changelogRepository;

    public TeamMetricsService(MetricsQueryRepository metricsRepository,
                              StatusChangelogRepository changelogRepository) {
        this.metricsRepository = metricsRepository;
        this.changelogRepository = changelogRepository;
    }

    /**
     * Calculate throughput (completed issues) for a period.
     */
    public ThroughputResponse calculateThroughput(Long teamId, LocalDate from, LocalDate to,
                                                   String issueType, String epicKey, String assigneeAccountId) {
        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        // Get throughput by week
        List<Object[]> weeklyData = metricsRepository.getThroughputByWeek(
                teamId, fromDt, toDt, issueType, epicKey, assigneeAccountId);

        // Group by period
        Map<LocalDate, Map<String, Integer>> periodMap = new LinkedHashMap<>();
        for (Object[] row : weeklyData) {
            Timestamp ts = (Timestamp) row[0];
            LocalDate periodStart = ts != null ? ts.toLocalDateTime().toLocalDate() : null;
            String type = (String) row[1];
            int count = ((Number) row[2]).intValue();

            if (periodStart != null) {
                periodMap.computeIfAbsent(periodStart, k -> new HashMap<>())
                        .merge(type, count, Integer::sum);
            }
        }

        // Build period throughput list
        List<PeriodThroughput> byPeriod = periodMap.entrySet().stream()
                .map(e -> {
                    Map<String, Integer> counts = e.getValue();
                    int epics = counts.getOrDefault("Epic", 0);
                    int stories = counts.getOrDefault("Story", 0);
                    int subtasks = counts.getOrDefault("Sub-task", 0);
                    return new PeriodThroughput(
                            e.getKey(),
                            e.getKey().plusDays(6),
                            epics, stories, subtasks,
                            epics + stories + subtasks
                    );
                })
                .collect(Collectors.toList());

        // Calculate totals
        int totalEpics = byPeriod.stream().mapToInt(PeriodThroughput::epics).sum();
        int totalStories = byPeriod.stream().mapToInt(PeriodThroughput::stories).sum();
        int totalSubtasks = byPeriod.stream().mapToInt(PeriodThroughput::subtasks).sum();

        return new ThroughputResponse(
                totalEpics, totalStories, totalSubtasks,
                totalEpics + totalStories + totalSubtasks,
                byPeriod
        );
    }

    /**
     * Calculate lead time (creation to completion).
     */
    public LeadTimeResponse calculateLeadTime(Long teamId, LocalDate from, LocalDate to,
                                               String issueType, String epicKey, String assigneeAccountId) {
        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Object[]> data = metricsRepository.getLeadTimeDays(
                teamId, fromDt, toDt, issueType, epicKey, assigneeAccountId);

        List<BigDecimal> leadTimes = data.stream()
                .map(row -> row[0] != null ? new BigDecimal(row[0].toString()) : null)
                .filter(Objects::nonNull)
                .filter(v -> v.compareTo(BigDecimal.ZERO) >= 0)
                .sorted()
                .collect(Collectors.toList());

        return calculateTimeStats(leadTimes);
    }

    /**
     * Calculate cycle time (start of work to completion).
     */
    public CycleTimeResponse calculateCycleTime(Long teamId, LocalDate from, LocalDate to,
                                                 String issueType, String epicKey, String assigneeAccountId) {
        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Object[]> data = metricsRepository.getCycleTimeDays(
                teamId, fromDt, toDt, issueType, epicKey, assigneeAccountId);

        List<BigDecimal> cycleTimes = data.stream()
                .map(row -> row[0] != null ? new BigDecimal(row[0].toString()) : null)
                .filter(Objects::nonNull)
                .filter(v -> v.compareTo(BigDecimal.ZERO) >= 0)
                .sorted()
                .collect(Collectors.toList());

        LeadTimeResponse stats = calculateTimeStats(cycleTimes);
        return new CycleTimeResponse(
                stats.avgDays(), stats.medianDays(), stats.p90Days(),
                stats.minDays(), stats.maxDays(), stats.sampleSize()
        );
    }

    /**
     * Calculate time spent in each status.
     */
    public List<TimeInStatusResponse> calculateTimeInStatuses(Long teamId, LocalDate from, LocalDate to) {
        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Object[]> data = changelogRepository.getTimeInStatusStats(teamId, fromDt, toDt);

        return data.stream()
                .map(row -> {
                    String status = (String) row[0];
                    BigDecimal avgSeconds = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
                    BigDecimal medianSeconds = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
                    int transitionsCount = ((Number) row[3]).intValue();

                    // Convert seconds to hours
                    BigDecimal avgHours = avgSeconds.divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
                    BigDecimal medianHours = medianSeconds.divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);

                    return new TimeInStatusResponse(status, avgHours, medianHours, transitionsCount);
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate metrics by assignee.
     */
    public List<AssigneeMetrics> calculateByAssignee(Long teamId, LocalDate from, LocalDate to) {
        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Object[]> data = metricsRepository.getMetricsByAssignee(teamId, fromDt, toDt);

        return data.stream()
                .map(row -> {
                    String accountId = (String) row[0];
                    String displayName = (String) row[1];
                    int issuesClosed = ((Number) row[2]).intValue();
                    BigDecimal avgLeadTime = row[3] != null
                            ? new BigDecimal(row[3].toString()).setScale(1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    BigDecimal avgCycleTime = row[4] != null
                            ? new BigDecimal(row[4].toString()).setScale(1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    return new AssigneeMetrics(
                            accountId,
                            displayName != null ? displayName : "Unknown",
                            issuesClosed,
                            avgLeadTime,
                            avgCycleTime
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Get complete metrics summary.
     */
    public TeamMetricsSummary getSummary(Long teamId, LocalDate from, LocalDate to,
                                          String issueType, String epicKey) {
        ThroughputResponse throughput = calculateThroughput(teamId, from, to, issueType, epicKey, null);
        LeadTimeResponse leadTime = calculateLeadTime(teamId, from, to, issueType, epicKey, null);
        CycleTimeResponse cycleTime = calculateCycleTime(teamId, from, to, issueType, epicKey, null);
        List<TimeInStatusResponse> timeInStatuses = calculateTimeInStatuses(teamId, from, to);
        List<AssigneeMetrics> byAssignee = calculateByAssignee(teamId, from, to);

        return new TeamMetricsSummary(
                from, to, teamId,
                throughput, leadTime, cycleTime, timeInStatuses, byAssignee
        );
    }

    private LeadTimeResponse calculateTimeStats(List<BigDecimal> sortedValues) {
        if (sortedValues.isEmpty()) {
            return new LeadTimeResponse(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0
            );
        }

        int size = sortedValues.size();

        // Average
        BigDecimal sum = sortedValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(size), 2, RoundingMode.HALF_UP);

        // Min and Max
        BigDecimal min = sortedValues.get(0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal max = sortedValues.get(size - 1).setScale(2, RoundingMode.HALF_UP);

        // Median (P50)
        BigDecimal median = percentile(sortedValues, 0.5);

        // P90
        BigDecimal p90 = percentile(sortedValues, 0.9);

        return new LeadTimeResponse(avg, median, p90, min, max, size);
    }

    private BigDecimal percentile(List<BigDecimal> sortedValues, double p) {
        if (sortedValues.isEmpty()) return BigDecimal.ZERO;
        int size = sortedValues.size();
        double index = p * (size - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);

        if (lower == upper || upper >= size) {
            return sortedValues.get(lower).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal lowerValue = sortedValues.get(lower);
        BigDecimal upperValue = sortedValues.get(upper);
        double fraction = index - lower;

        return lowerValue.add(
                upperValue.subtract(lowerValue).multiply(BigDecimal.valueOf(fraction))
        ).setScale(2, RoundingMode.HALF_UP);
    }
}
