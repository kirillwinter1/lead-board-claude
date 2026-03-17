package com.leadboard.metrics.controller;

import com.leadboard.metrics.dto.*;
import com.leadboard.metrics.dto.MonthlyDsrResponse;
import com.leadboard.metrics.service.DeliveryHealthService;
import com.leadboard.metrics.service.DsrService;
import com.leadboard.metrics.service.ForecastAccuracyService;
import com.leadboard.metrics.service.TeamMetricsService;
import com.leadboard.metrics.service.VelocityService;
import com.leadboard.metrics.service.EpicBurndownService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@PreAuthorize("isAuthenticated()")
public class TeamMetricsController {

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' date must not be after 'to' date");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private final TeamMetricsService metricsService;
    private final ForecastAccuracyService forecastAccuracyService;
    private final DsrService dsrService;
    private final VelocityService velocityService;
    private final EpicBurndownService burndownService;
    private final DeliveryHealthService deliveryHealthService;

    public TeamMetricsController(
            TeamMetricsService metricsService,
            ForecastAccuracyService forecastAccuracyService,
            DsrService dsrService,
            VelocityService velocityService,
            EpicBurndownService burndownService,
            DeliveryHealthService deliveryHealthService
    ) {
        this.metricsService = metricsService;
        this.forecastAccuracyService = forecastAccuracyService;
        this.dsrService = dsrService;
        this.velocityService = velocityService;
        this.burndownService = burndownService;
        this.deliveryHealthService = deliveryHealthService;
    }

    @GetMapping("/throughput")
    public ThroughputResponse getThroughput(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String epicKey,
            @RequestParam(required = false) String assigneeAccountId) {
        validateDateRange(from, to);
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
        validateDateRange(from, to);
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
        validateDateRange(from, to);
        return metricsService.calculateCycleTime(teamId, from, to, issueType, epicKey, assigneeAccountId);
    }

    @GetMapping("/time-in-status")
    public List<TimeInStatusResponse> getTimeInStatus(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateDateRange(from, to);
        return metricsService.calculateTimeInStatuses(teamId, from, to);
    }

    @GetMapping("/by-assignee")
    public List<AssigneeMetrics> getByAssignee(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateDateRange(from, to);
        return metricsService.calculateByAssignee(teamId, from, to);
    }

    @GetMapping("/summary")
    public TeamMetricsSummary getSummary(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String epicKey) {
        validateDateRange(from, to);
        return metricsService.getSummary(teamId, from, to, issueType, epicKey);
    }

    @GetMapping("/forecast-accuracy")
    public ForecastAccuracyResponse getForecastAccuracy(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateDateRange(from, to);
        return forecastAccuracyService.calculateAccuracy(teamId, from, to);
    }

    @GetMapping("/dsr")
    public DsrResponse getDsr(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateDateRange(from, to);
        return dsrService.calculateDsr(teamId, from, to);
    }

    @GetMapping("/dsr/monthly")
    public MonthlyDsrResponse getMonthlyDsr(
            @RequestParam Long teamId,
            @RequestParam(defaultValue = "12") int months) {
        return dsrService.calculateMonthlyDsr(teamId, months);
    }

    @GetMapping("/velocity")
    public VelocityResponse getVelocity(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateDateRange(from, to);
        return velocityService.calculateVelocity(teamId, from, to);
    }

    @GetMapping("/epic-burndown")
    public ResponseEntity<?> getEpicBurndown(@RequestParam String epicKey) {
        try {
            return ResponseEntity.ok(burndownService.calculateBurndown(epicKey));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/epics-for-burndown")
    public List<EpicBurndownService.EpicInfo> getEpicsForBurndown(@RequestParam Long teamId) {
        return burndownService.getEpicsForTeam(teamId);
    }

    @GetMapping("/data-status")
    public MetricsDataStatus getDataStatus(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateDateRange(from, to);
        return metricsService.getDataStatus(teamId, from, to);
    }

    @GetMapping("/executive-summary")
    public ExecutiveSummary getExecutiveSummary(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateDateRange(from, to);
        return metricsService.getExecutiveSummary(teamId, from, to, dsrService, velocityService);
    }

    @GetMapping("/delivery-health")
    public DeliveryHealth getDeliveryHealth(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateDateRange(from, to);
        return deliveryHealthService.calculateHealth(teamId, from, to);
    }
}
