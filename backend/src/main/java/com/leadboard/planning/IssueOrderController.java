package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for manual ordering of epics and stories.
 */
@RestController
@RequestMapping("/api")
public class IssueOrderController {

    private final IssueOrderService orderService;

    public IssueOrderController(IssueOrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Update epic position within its team.
     *
     * @param epicKey the epic key
     * @param request the new position (1-based)
     * @return updated epic data
     */
    @PutMapping("/epics/{epicKey}/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<OrderResponse> updateEpicOrder(
            @PathVariable String epicKey,
            @RequestBody OrderRequest request
    ) {
        JiraIssueEntity updated = orderService.reorderEpic(epicKey, request.position());
        return ResponseEntity.ok(new OrderResponse(
                updated.getIssueKey(),
                updated.getManualOrder(),
                updated.getAutoScore() != null ? updated.getAutoScore().doubleValue() : null
        ));
    }

    /**
     * Update story/bug position within its parent epic.
     *
     * @param storyKey the story/bug key
     * @param request the new position (1-based)
     * @return updated story data
     */
    @PutMapping("/stories/{storyKey}/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<OrderResponse> updateStoryOrder(
            @PathVariable String storyKey,
            @RequestBody OrderRequest request
    ) {
        JiraIssueEntity updated = orderService.reorderStory(storyKey, request.position());
        return ResponseEntity.ok(new OrderResponse(
                updated.getIssueKey(),
                updated.getManualOrder(),
                updated.getAutoScore() != null ? updated.getAutoScore().doubleValue() : null
        ));
    }

    public record OrderRequest(int position) {}

    public record OrderResponse(
            String issueKey,
            Integer manualOrder,
            Double autoScore
    ) {}
}
