package com.leadboard.controller;

import com.leadboard.board.BoardResponse;
import com.leadboard.board.BoardService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.AutoScoreService;
import com.leadboard.planning.StoryAutoScoreService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/board")
public class BoardController {

    private final BoardService boardService;
    private final AutoScoreService autoScoreService;
    private final StoryAutoScoreService storyAutoScoreService;
    private final WorkflowConfigService workflowConfigService;
    private final JiraIssueRepository issueRepository;

    public BoardController(BoardService boardService,
                          AutoScoreService autoScoreService,
                          StoryAutoScoreService storyAutoScoreService,
                          WorkflowConfigService workflowConfigService,
                          JiraIssueRepository issueRepository) {
        this.boardService = boardService;
        this.autoScoreService = autoScoreService;
        this.storyAutoScoreService = storyAutoScoreService;
        this.workflowConfigService = workflowConfigService;
        this.issueRepository = issueRepository;
    }

    @GetMapping
    public ResponseEntity<BoardResponse> getBoard(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) List<Long> teamIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        BoardResponse response = boardService.getBoard(query, statuses, teamIds, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Get AutoScore breakdown for any issue (epic or story).
     * Returns factor names and their contribution to the total score.
     *
     * @param issueKey Issue key (e.g., LB-123)
     * @return Map of factor names to their numeric values
     */
    @GetMapping("/{issueKey}/score-breakdown")
    public ResponseEntity<Map<String, Object>> getScoreBreakdown(@PathVariable String issueKey) {
        Optional<JiraIssueEntity> issueOpt = issueRepository.findByIssueKey(issueKey);
        if (issueOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        JiraIssueEntity issue = issueOpt.get();
        String issueType = issue.getIssueType();

        Map<String, Object> response = new HashMap<>();
        response.put("issueKey", issueKey);
        response.put("issueType", issueType);
        response.put("totalScore", issue.getAutoScore());

        // Check if epic
        if (issueType != null && workflowConfigService.isEpic(issueType)) {
            AutoScoreService.AutoScoreDetails details = autoScoreService.getScoreDetails(issueKey);
            if (details != null) {
                response.put("breakdown", details.factors());
            }
        } else {
            // Story or Bug - use StoryAutoScoreService
            Map<String, BigDecimal> breakdown = storyAutoScoreService.calculateScoreBreakdown(issue);
            response.put("breakdown", breakdown);
        }

        return ResponseEntity.ok(response);
    }
}
