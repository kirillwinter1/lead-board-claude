package com.leadboard.metrics.controller;

import com.leadboard.metrics.dto.*;
import com.leadboard.metrics.service.DsrService;
import com.leadboard.metrics.service.ForecastAccuracyService;
import com.leadboard.metrics.service.TeamMetricsService;
import com.leadboard.metrics.service.VelocityService;
import com.leadboard.metrics.service.EpicBurndownService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/metrics")
public class TeamMetricsController {

    private final TeamMetricsService metricsService;
    private final ForecastAccuracyService forecastAccuracyService;
    private final DsrService dsrService;
    private final VelocityService velocityService;
    private final EpicBurndownService burndownService;

    public TeamMetricsController(
            TeamMetricsService metricsService,
            ForecastAccuracyService forecastAccuracyService,
            DsrService dsrService,
            VelocityService velocityService,
            EpicBurndownService burndownService
    ) {
        this.metricsService = metricsService;
        this.forecastAccuracyService = forecastAccuracyService;
        this.dsrService = dsrService;
        this.velocityService = velocityService;
        this.burndownService = burndownService;
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

    /**
     * Get Delivery Speed Ratio (DSR) metrics.
     * DSR = actual working days / estimate days
     * 1.0 = baseline, < 1.0 = faster, > 1.0 = slower
     */
    @GetMapping("/dsr")
    public DsrResponse getDsr(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dsrService.calculateDsr(teamId, from, to);
    }

    /**
     * Get team velocity metrics (logged hours vs capacity).
     */
    @GetMapping("/velocity")
    public VelocityResponse getVelocity(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return velocityService.calculateVelocity(teamId, from, to);
    }

    /**
     * Get epic burndown chart data.
     */
    @GetMapping("/epic-burndown")
    public EpicBurndownResponse getEpicBurndown(@RequestParam String epicKey) {
        return burndownService.calculateBurndown(epicKey);
    }

    /**
     * Get list of epics for burndown selector.
     */
    @GetMapping("/epics-for-burndown")
    public List<EpicBurndownService.EpicInfo> getEpicsForBurndown(@RequestParam Long teamId) {
        return burndownService.getEpicsForTeam(teamId);
    }
}
