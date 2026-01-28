package com.leadboard.metrics.controller;

import com.leadboard.metrics.dto.*;
import com.leadboard.metrics.service.ForecastAccuracyService;
import com.leadboard.metrics.service.LtcService;
import com.leadboard.metrics.service.TeamMetricsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/metrics")
public class TeamMetricsController {

    private final TeamMetricsService metricsService;
    private final ForecastAccuracyService forecastAccuracyService;
    private final LtcService ltcService;

    public TeamMetricsController(
            TeamMetricsService metricsService,
            ForecastAccuracyService forecastAccuracyService,
            LtcService ltcService
    ) {
        this.metricsService = metricsService;
        this.forecastAccuracyService = forecastAccuracyService;
        this.ltcService = ltcService;
    }

    @GetMapping("/throughput")
    public ThroughputResponse getThroughput(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String epicKey,
            @RequestParam(required = false) String assigneeAccountId) {
        return metricsService.calculateThroughput(teamId, from, to, issueType, epicKey, assigneeAccountId);
    }

    @GetMapping("/lead-time")
    public LeadTimeResponse getLeadTime(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String epicKey,
            @RequestParam(required = false) String assigneeAccountId) {
        return metricsService.calculateLeadTime(teamId, from, to, issueType, epicKey, assigneeAccountId);
    }

    @GetMapping("/cycle-time")
    public CycleTimeResponse getCycleTime(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String epicKey,
            @RequestParam(required = false) String assigneeAccountId) {
        return metricsService.calculateCycleTime(teamId, from, to, issueType, epicKey, assigneeAccountId);
    }

    @GetMapping("/time-in-status")
    public List<TimeInStatusResponse> getTimeInStatus(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return metricsService.calculateTimeInStatuses(teamId, from, to);
    }

    @GetMapping("/by-assignee")
    public List<AssigneeMetrics> getByAssignee(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return metricsService.calculateByAssignee(teamId, from, to);
    }

    @GetMapping("/summary")
    public TeamMetricsSummary getSummary(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String epicKey) {
        return metricsService.getSummary(teamId, from, to, issueType, epicKey);
    }

    /**
     * Get forecast accuracy metrics - compares planned vs actual completion dates.
     */
    @GetMapping("/forecast-accuracy")
    public ForecastAccuracyResponse getForecastAccuracy(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return forecastAccuracyService.calculateAccuracy(teamId, from, to);
    }

    @GetMapping("/ltc")
    public LtcResponse getLtc(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ltcService.calculateLtc(teamId, from, to);
    }
}
