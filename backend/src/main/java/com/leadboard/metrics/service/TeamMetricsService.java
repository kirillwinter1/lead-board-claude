package com.leadboard.metrics.service;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.dto.*;
import com.leadboard.metrics.repository.MetricsQueryRepository;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.SyncService;
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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeamMetricsService {

    private static final Logger log = LoggerFactory.getLogger(TeamMetricsService.class);

    private final MetricsQueryRepository metricsRepository;
    private final StatusChangelogRepository changelogRepository;
    private final WorkflowConfigService workflowConfig;
    private final SyncService syncService;

    public TeamMetricsService(MetricsQueryRepository metricsRepository,
                              StatusChangelogRepository changelogRepository,
                              WorkflowConfigService workflowConfig,
                              SyncService syncService) {
        this.metricsRepository = metricsRepository;
        this.changelogRepository = changelogRepository;
        this.workflowConfig = workflowConfig;
        this.syncService = syncService;
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
                    int bugs = counts.getOrDefault("BUG", 0);
                    return new PeriodThroughput(
                            e.getKey(),
                            e.getKey().plusDays(6),
                            epics, stories, subtasks, bugs,
                            epics + stories + subtasks + bugs
                    );
                })
                .collect(Collectors.toList());

        // Calculate totals
        int totalEpics = byPeriod.stream().mapToInt(PeriodThroughput::epics).sum();
        int totalStories = byPeriod.stream().mapToInt(PeriodThroughput::stories).sum();
        int totalSubtasks = byPeriod.stream().mapToInt(PeriodThroughput::subtasks).sum();
        int totalBugs = byPeriod.stream().mapToInt(PeriodThroughput::bugs).sum();

        // Calculate moving average (4-week window)
        List<BigDecimal> movingAverage = calculateMovingAverage(byPeriod, 4);

        return new ThroughputResponse(
                totalEpics, totalStories, totalSubtasks, totalBugs,
                totalEpics + totalStories + totalSubtasks + totalBugs,
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
     * Shows ALL statuses from changelog data, excluding NEW/DONE.
     * Pipeline statuses are sorted by sortOrder first, others by transition count.
     */
    public List<TimeInStatusResponse> calculateTimeInStatuses(Long teamId, LocalDate from, LocalDate to) {
        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        // 1. Get pipeline statuses for color/sortOrder lookup
        var pipelineStatuses = workflowConfig.getStoryPipelineStatuses();
        Map<String, Integer> sortOrderMap = new HashMap<>();
        Map<String, String> colorMap = new HashMap<>();
        for (var ps : pipelineStatuses) {
            sortOrderMap.put(ps.statusName().toLowerCase(), ps.sortOrder());
            colorMap.put(ps.statusName().toLowerCase(), ps.color());
        }

        // 2. Query actual data from changelog (grouped by from_status)
        List<Object[]> data = changelogRepository.getTimeInStatusStats(teamId, fromDt, toDt);

        // 3. Build results from ALL statuses in data, excluding NEW/DONE
        BigDecimal divisor = BigDecimal.valueOf(3600);
        List<TimeInStatusResponse> result = new ArrayList<>();

        for (Object[] row : data) {
            String status = (String) row[0];
            if (status == null) continue;

            if (shouldExcludeTimeInStatus(status)) {
                continue;
            }

            BigDecimal avgSec = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            BigDecimal medSec = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            BigDecimal p85Sec = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            BigDecimal p99Sec = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
            int transitionsCount = ((Number) row[5]).intValue();

            BigDecimal avgHours = avgSec.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal medianHours = medSec.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal p85Hours = p85Sec.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal p99Hours = p99Sec.divide(divisor, 2, RoundingMode.HALF_UP);

            String lowerStatus = status.toLowerCase();
            int sortOrder = sortOrderMap.getOrDefault(lowerStatus, Integer.MAX_VALUE);
            String color = colorMap.getOrDefault(lowerStatus, null);

            result.add(new TimeInStatusResponse(
                    status, avgHours, medianHours,
                    p85Hours, p99Hours, transitionsCount,
                    sortOrder, color));
        }

        // 4. Sort: pipeline statuses first (by sortOrder), then remaining by transition count desc
        result.sort((a, b) -> {
            boolean aPipeline = a.sortOrder() < Integer.MAX_VALUE;
            boolean bPipeline = b.sortOrder() < Integer.MAX_VALUE;
            if (aPipeline && !bPipeline) return -1;
            if (!aPipeline && bPipeline) return 1;
            if (aPipeline) return Integer.compare(a.sortOrder(), b.sortOrder());
            return Integer.compare(b.transitionsCount(), a.transitionsCount());
        });

        return result;
    }

    private boolean shouldExcludeTimeInStatus(String status) {
        try {
            List<StatusCategory> categories = List.of(
                    workflowConfig.categorizeStory(status),
                    workflowConfig.categorizeSubtask(status),
                    workflowConfig.categorizeEpic(status)
            );

            boolean hasActiveCategory = categories.stream()
                    .anyMatch(cat -> cat == StatusCategory.IN_PROGRESS || cat == StatusCategory.PLANNED);
            if (hasActiveCategory) {
                return false;
            }

            return categories.stream()
                    .allMatch(cat -> cat == StatusCategory.NEW
                            || cat == StatusCategory.TODO
                            || cat == StatusCategory.DONE);
        } catch (Exception e) {
            log.debug("Failed to classify time-in-status category for '{}', keeping row", status, e);
            return false;
        }
    }

    /**
     * Calculate metrics by assignee with extended fields (DSR, velocity, trend, outlier).
     */
    public List<AssigneeMetrics> calculateByAssignee(Long teamId, LocalDate from, LocalDate to) {
        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        long periodDays = ChronoUnit.DAYS.between(from, to);
        LocalDate prevFrom = from.minusDays(periodDays);
        OffsetDateTime prevFromDt = prevFrom.atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Object[]> data = metricsRepository.getExtendedMetricsByAssigneeV2(teamId, fromDt, toDt, prevFromDt);

        // First pass: build list
        List<AssigneeMetrics> metrics = new ArrayList<>();
        for (Object[] row : data) {
            String accountId = (String) row[0];
            String displayName = (String) row[1];
            int issuesClosed = ((Number) row[2]).intValue();
            BigDecimal avgLeadTime = row[3] != null
                    ? new BigDecimal(row[3].toString()).setScale(1, RoundingMode.HALF_UP) : null;
            BigDecimal avgCycleTime = row[4] != null
                    ? new BigDecimal(row[4].toString()).setScale(1, RoundingMode.HALF_UP) : null;
            BigDecimal personalDsr = row[5] != null
                    ? new BigDecimal(row[5].toString()).setScale(2, RoundingMode.HALF_UP) : null;

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

            int issuesPrev = row[8] != null ? ((Number) row[8]).intValue() : 0;
            String trend = calculateTrend(issuesClosed, issuesPrev);

            BigDecimal avgCycleTimePrev = row[9] != null
                    ? new BigDecimal(row[9].toString()).setScale(1, RoundingMode.HALF_UP) : null;

            metrics.add(new AssigneeMetrics(
                    accountId,
                    displayName != null ? displayName : "Unknown",
                    issuesClosed, avgLeadTime, avgCycleTime,
                    personalDsr, velocityPercent, trend,
                    issuesPrev, avgCycleTimePrev, false
            ));
        }

        // Second pass: compute team median cycle time, flag outliers
        List<BigDecimal> cycleTimes = metrics.stream()
                .map(AssigneeMetrics::avgCycleTimeDays)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
        BigDecimal teamMedianCycleTime = cycleTimes.isEmpty() ? null : percentile(cycleTimes, 0.5);

        List<AssigneeMetrics> result = new ArrayList<>();
        for (AssigneeMetrics m : metrics) {
            boolean outlier = false;
            if (m.personalDsr() != null && m.personalDsr().compareTo(new BigDecimal("1.5")) > 0) {
                outlier = true;
            }
            if (teamMedianCycleTime != null && m.avgCycleTimeDays() != null
                    && m.avgCycleTimeDays().compareTo(teamMedianCycleTime.multiply(BigDecimal.valueOf(2))) > 0) {
                outlier = true;
            }

            result.add(new AssigneeMetrics(
                    m.accountId(), m.displayName(), m.issuesClosed(),
                    m.avgLeadTimeDays(), m.avgCycleTimeDays(),
                    m.personalDsr(), m.velocityPercent(), m.trend(),
                    m.issuesClosedPrev(), m.avgCycleTimePrev(), outlier
            ));
        }

        return result;
    }

    /**
     * Get data status for metrics dashboard.
     */
    public MetricsDataStatus getDataStatus(Long teamId, LocalDate from, LocalDate to) {
        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        OffsetDateTime lastSyncAt = null;
        boolean syncInProgress = false;
        try {
            SyncService.SyncStatus syncStatus = syncService.getSyncStatus();
            lastSyncAt = syncStatus.lastSyncCompletedAt();
            syncInProgress = syncStatus.syncInProgress();
        } catch (Exception e) {
            log.debug("Failed to get sync status for data-status bar", e);
        }

        int issuesInScope = 0;
        int issuesWithChangelog = 0;
        try {
            issuesInScope = metricsRepository.countIssuesInScope(teamId, fromDt, toDt);
            issuesWithChangelog = metricsRepository.countIssuesWithChangelog(teamId, fromDt, toDt);
        } catch (Exception e) {
            log.debug("Failed to count issues for data-status bar", e);
        }

        BigDecimal coverage = issuesInScope > 0
                ? BigDecimal.valueOf(issuesWithChangelog)
                    .divide(BigDecimal.valueOf(issuesInScope), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new MetricsDataStatus(
                lastSyncAt,
                syncInProgress,
                issuesInScope,
                issuesWithChangelog,
                coverage
        );
    }

    /**
     * Get executive summary with KPI cards and deltas vs previous period.
     */
    public ExecutiveSummary getExecutiveSummary(Long teamId, LocalDate from, LocalDate to,
                                                 DsrService dsrService, VelocityService velocityService) {
        long periodDays = ChronoUnit.DAYS.between(from, to);
        LocalDate prevFrom = from.minusDays(periodDays);
        LocalDate prevTo = from.minusDays(1);

        // Throughput
        ExecutiveSummary.KpiCard throughputKpi;
        try {
            ThroughputResponse currentThroughput = calculateThroughput(teamId, from, to, null, null, null);
            ThroughputResponse prevThroughput = calculateThroughput(teamId, prevFrom, prevTo, null, null, null);
            throughputKpi = buildKpiCard("Throughput",
                    String.valueOf(currentThroughput.total()),
                    BigDecimal.valueOf(currentThroughput.total()),
                    BigDecimal.valueOf(prevThroughput.total()),
                    currentThroughput.total(), null);
        } catch (Exception e) {
            log.debug("Failed to compute throughput KPI", e);
            throughputKpi = buildKpiCard("Throughput", "—", BigDecimal.ZERO, null, 0, null);
        }

        // Cycle time median
        ExecutiveSummary.KpiCard cycleKpi;
        try {
            CycleTimeResponse currentCycle = calculateCycleTime(teamId, from, to, null, null, null);
            CycleTimeResponse prevCycle = calculateCycleTime(teamId, prevFrom, prevTo, null, null, null);
            cycleKpi = buildKpiCard("Cycle Time",
                    currentCycle.sampleSize() > 0 ? currentCycle.medianDays() + "d" : "—",
                    currentCycle.medianDays(),
                    prevCycle.sampleSize() > 0 ? prevCycle.medianDays() : null,
                    currentCycle.sampleSize(), null);
        } catch (Exception e) {
            log.debug("Failed to compute cycle time KPI", e);
            cycleKpi = buildKpiCard("Cycle Time", "—", BigDecimal.ZERO, null, 0, null);
        }

        // Lead time median
        ExecutiveSummary.KpiCard leadKpi;
        try {
            LeadTimeResponse currentLead = calculateLeadTime(teamId, from, to, null, null, null);
            LeadTimeResponse prevLead = calculateLeadTime(teamId, prevFrom, prevTo, null, null, null);
            leadKpi = buildKpiCard("Lead Time",
                    currentLead.sampleSize() > 0 ? currentLead.medianDays() + "d" : "—",
                    currentLead.medianDays(),
                    prevLead.sampleSize() > 0 ? prevLead.medianDays() : null,
                    currentLead.sampleSize(), null);
        } catch (Exception e) {
            log.debug("Failed to compute lead time KPI", e);
            leadKpi = buildKpiCard("Lead Time", "—", BigDecimal.ZERO, null, 0, null);
        }

        // Predictability (on-time rate from DSR)
        ExecutiveSummary.KpiCard predictabilityKpi;
        try {
            var currentDsr = dsrService.calculateDsr(teamId, from, to);
            var prevDsr = dsrService.calculateDsr(teamId, prevFrom, prevTo);
            predictabilityKpi = buildKpiCard("Predictability",
                    currentDsr.totalEpics() > 0 ? currentDsr.onTimeRate().setScale(0, RoundingMode.HALF_UP) + "%" : "—",
                    currentDsr.onTimeRate(),
                    prevDsr.totalEpics() > 0 ? prevDsr.onTimeRate() : null,
                    currentDsr.totalEpics(), BigDecimal.valueOf(80));
        } catch (Exception e) {
            log.debug("Failed to compute predictability KPI", e);
            predictabilityKpi = buildKpiCard("Predictability", "—", BigDecimal.ZERO, null, 0, BigDecimal.valueOf(80));
        }

        // Capacity utilization (from velocity)
        ExecutiveSummary.KpiCard capacityKpi;
        try {
            var currentVelocity = velocityService.calculateVelocity(teamId, from, to);
            var prevVelocity = velocityService.calculateVelocity(teamId, prevFrom, prevTo);
            capacityKpi = buildKpiCard("Utilization",
                    currentVelocity.utilizationPercent() + "%",
                    currentVelocity.utilizationPercent(),
                    prevVelocity.utilizationPercent(),
                    currentVelocity.byWeek().size(), BigDecimal.valueOf(90));
        } catch (Exception e) {
            log.debug("Failed to compute capacity KPI", e);
            capacityKpi = buildKpiCard("Utilization", "—", BigDecimal.ZERO, null, 0, BigDecimal.valueOf(90));
        }

        // Blocked risk
        ExecutiveSummary.KpiCard blockedKpi;
        try {
            OffsetDateTime threshold = LocalDate.now().minusDays(14).atStartOfDay().atOffset(ZoneOffset.UTC);
            Object[] blocked = metricsRepository.countBlockedAndAgingIssues(teamId, threshold);
            int flaggedCount = blocked != null && blocked[0] != null ? ((Number) blocked[0]).intValue() : 0;
            int agingCount = blocked != null && blocked[1] != null ? ((Number) blocked[1]).intValue() : 0;
            int totalRisk = flaggedCount + agingCount;
            blockedKpi = new ExecutiveSummary.KpiCard(
                    "Blocked/Aging", String.valueOf(totalRisk),
                    BigDecimal.valueOf(totalRisk), null, null,
                    totalRisk > 0 ? "WARNING" : "STABLE",
                    totalRisk, BigDecimal.ZERO);
        } catch (Exception e) {
            log.debug("Failed to compute blocked/aging KPI", e);
            blockedKpi = new ExecutiveSummary.KpiCard(
                    "Blocked/Aging", "—", BigDecimal.ZERO, null, null,
                    "STABLE", 0, BigDecimal.ZERO);
        }

        return new ExecutiveSummary(throughputKpi, cycleKpi, leadKpi, predictabilityKpi, capacityKpi, blockedKpi);
    }

    private ExecutiveSummary.KpiCard buildKpiCard(String label, String formattedValue,
                                                    BigDecimal current, BigDecimal prev,
                                                    int sampleSize, BigDecimal target) {
        BigDecimal deltaPercent = null;
        String trend = "STABLE";

        if (prev != null && prev.compareTo(BigDecimal.ZERO) > 0) {
            deltaPercent = current.subtract(prev)
                    .divide(prev, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
            if (deltaPercent.compareTo(BigDecimal.valueOf(5)) > 0) {
                trend = "UP";
            } else if (deltaPercent.compareTo(BigDecimal.valueOf(-5)) < 0) {
                trend = "DOWN";
            }
        } else if (prev == null && current.compareTo(BigDecimal.ZERO) > 0) {
            trend = "UP";
        }

        return new ExecutiveSummary.KpiCard(label, formattedValue, current, prev, deltaPercent, trend, sampleSize, target);
    }

    /**
     * Calculate trend based on current vs previous period.
     */
    String calculateTrend(int current, int previous) {
        if (previous == 0) {
            return current > 0 ? "UP" : "STABLE";
        }
        double changePercent = ((double) (current - previous) / previous) * 100;
        if (changePercent > 5) {
            return "UP";
        } else if (changePercent < -5) {
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

        BigDecimal sum = sortedValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(size), 2, RoundingMode.HALF_UP);

        BigDecimal min = sortedValues.get(0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal max = sortedValues.get(size - 1).setScale(2, RoundingMode.HALF_UP);
        BigDecimal median = percentile(sortedValues, 0.5);
        BigDecimal p90 = percentile(sortedValues, 0.9);

        return new LeadTimeResponse(avg, median, p90, min, max, size);
    }

    BigDecimal percentile(List<BigDecimal> sortedValues, double p) {
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
