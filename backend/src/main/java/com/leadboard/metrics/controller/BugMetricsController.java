package com.leadboard.metrics.controller;

import com.leadboard.metrics.dto.BugMetricsResponse;
import com.leadboard.metrics.service.BugMetricsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metrics/bugs")
public class BugMetricsController {

    private final BugMetricsService bugMetricsService;

    public BugMetricsController(BugMetricsService bugMetricsService) {
        this.bugMetricsService = bugMetricsService;
    }

    @GetMapping
    public BugMetricsResponse getBugMetrics(@RequestParam(required = false) Long teamId) {
        return bugMetricsService.getBugMetrics(teamId);
    }
}
