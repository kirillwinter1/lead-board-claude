package com.leadboard.metrics.service;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.dto.*;
import com.leadboard.metrics.repository.MetricsQueryRepository;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeamMetricsService {

    private static final Logger log = LoggerFactory.getLogger(TeamMetricsService.class);

    private final MetricsQueryRepository metricsRepository;
    private final StatusChangelogRepository changelogRepository;
    private final WorkflowConfigService workflowConfig;

    public TeamMetricsService(MetricsQueryRepository metricsRepository,
                              StatusChangelogRepository changelogRepository,
                              WorkflowConfigService workflowConfig) {
        this.metricsRepository = metricsRepository;
        this.changelogRepository = changelogRepository;
        this.workflowConfig = workflowConfig;
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
            LocalDate periodStart = parseLocalDate(row[0]);
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
                    int epics = counts.getOrDefault("EPIC", 0);
                    int stories = counts.getOrDefault("STORY", 0);
                    int subtasks = counts.getOrDefault("SUBTASK", 0);
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

        // Calculate moving average (4-week window)
        List<BigDecimal> movingAverage = calculateMovingAverage(byPeriod, 4);

        return new ThroughputResponse(
                totalEpics, totalStories, totalSubtasks,
                totalEpics + totalStories + totalSubtasks,
                byPeriod,
                movingAverage
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

        // Get STORY status names for filtering
        List<String> storyStatuses = workflowConfig.getStoryTypeNames().stream()
                .findFirst()
                .map(t -> {
                    // Collect all STORY-category status names
                    List<String> names = new ArrayList<>();
                    for (var cat : com.leadboard.status.StatusCategory.values()) {
                        names.addAll(workflowConfig.getStatusNames(
                                com.leadboard.config.entity.BoardCategory.STORY, cat));
                    }
                    return names;
                })
                .orElse(List.of());

        return data.stream()
                .map(row -> {
                    String status = (String) row[0];
                    BigDecimal avgSeconds = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
                    BigDecimal medianSeconds = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
                    BigDecimal p85Seconds = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
                    BigDecimal p99Seconds = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
                    int transitionsCount = ((Number) row[5]).intValue();

                    BigDecimal divisor = BigDecimal.valueOf(3600);
                    BigDecimal avgHours = avgSeconds.divide(divisor, 2, RoundingMode.HALF_UP);
                    BigDecimal medianHours = medianSeconds.divide(divisor, 2, RoundingMode.HALF_UP);
                    BigDecimal p85Hours = p85Seconds.divide(divisor, 2, RoundingMode.HALF_UP);
                    BigDecimal p99Hours = p99Seconds.divide(divisor, 2, RoundingMode.HALF_UP);

                    int sortOrder = workflowConfig.getStoryStatusSortOrder(status);
                    String color = workflowConfig.getStoryStatusColor(status);

                    return new TimeInStatusResponse(status, avgHours, medianHours,
                            p85Hours, p99Hours, transitionsCount, sortOrder, color);
                })
                .filter(r -> {
                    // Filter to STORY statuses only if we have config
                    if (storyStatuses.isEmpty()) return true;
                    return storyStatuses.stream()
                            .anyMatch(s -> s.equalsIgnoreCase(r.status()));
                })
                .sorted(Comparator.comparingInt(TimeInStatusResponse::sortOrder))
                .collect(Collectors.toList());
    }

    /**
     * Calculate metrics by assignee with extended fields (DSR, velocity, trend).
     */
    public List<AssigneeMetrics> calculateByAssignee(Long teamId, LocalDate from, LocalDate to) {
        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        // Previous period of the same length for trend calculation
        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        LocalDate prevFrom = from.minusDays(periodDays);
        OffsetDateTime prevFromDt = prevFrom.atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Object[]> data = metricsRepository.getExtendedMetricsByAssignee(teamId, fromDt, toDt, prevFromDt);

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

                    // Personal DSR (time_spent / original_estimate)
                    BigDecimal personalDsr = row[5] != null
                            ? new BigDecimal(row[5].toString()).setScale(2, RoundingMode.HALF_UP)
                            : null;

                    // Velocity = (time_spent / estimate) * 100
                    BigDecimal velocityPercent = null;
                    if (row[6] != null && row[7] != null) {
                        BigDecimal timeSpent = new BigDecimal(row[6].toString());
                        BigDecimal estimate = new BigDecimal(row[7].toString());
                        if (estimate.compareTo(BigDecimal.ZERO) > 0) {
                            velocityPercent = timeSpent.divide(estimate, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(0, RoundingMode.HALF_UP);
                        }
                    }

                    // Trend calculation (compare with previous period)
                    int issuesPrev = row[8] != null ? ((Number) row[8]).intValue() : 0;
                    String trend = calculateTrend(issuesClosed, issuesPrev);

                    return new AssigneeMetrics(
                            accountId,
                            displayName != null ? displayName : "Unknown",
                            issuesClosed,
                            avgLeadTime,
                            avgCycleTime,
                            personalDsr,
                            velocityPercent,
                            trend
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate trend based on current vs previous period.
     */
    private String calculateTrend(int current, int previous) {
        if (previous == 0) {
            return current > 0 ? "UP" : "STABLE";
        }
        double changePercent = ((double) (current - previous) / previous) * 100;
        if (changePercent > 10) {
            return "UP";
        } else if (changePercent < -10) {
            return "DOWN";
        }
        return "STABLE";
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

    /**
     * Calculate moving average for throughput data.
     * @param byPeriod list of period throughputs
     * @param window number of periods for the window
     * @return list of moving average values (same length as input)
     */
    private List<BigDecimal> calculateMovingAverage(List<PeriodThroughput> byPeriod, int window) {
        List<BigDecimal> result = new ArrayList<>();
        if (byPeriod.isEmpty()) {
            return result;
        }

        for (int i = 0; i < byPeriod.size(); i++) {
            int start = Math.max(0, i - window + 1);
            int count = i - start + 1;

            BigDecimal sum = BigDecimal.ZERO;
            for (int j = start; j <= i; j++) {
                sum = sum.add(BigDecimal.valueOf(byPeriod.get(j).total()));
            }

            BigDecimal avg = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            result.add(avg);
        }

        return result;
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

    /**
     * Parse LocalDate from various database types (Timestamp, Instant, OffsetDateTime).
     */
    private LocalDate parseLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toLocalDate();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        log.warn("Unknown date type: {}", value.getClass().getName());
        return null;
    }
}
