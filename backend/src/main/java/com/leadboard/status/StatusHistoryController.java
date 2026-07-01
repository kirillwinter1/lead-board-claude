package com.leadboard.status;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F81 — lazy per-issue status journey for the status-age tooltip.
 */
@RestController
@RequestMapping("/api/issues")
public class StatusHistoryController {

    private final StatusHistoryService statusHistoryService;

    public StatusHistoryController(StatusHistoryService statusHistoryService) {
        this.statusHistoryService = statusHistoryService;
    }

    @GetMapping("/{issueKey}/status-history")
    public ResponseEntity<StatusHistoryResponse> getStatusHistory(@PathVariable String issueKey) {
        return statusHistoryService.getHistory(issueKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
