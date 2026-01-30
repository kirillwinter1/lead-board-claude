package com.leadboard.planning;

import com.leadboard.planning.dto.RecalculateResponse;
import com.leadboard.planning.dto.StoriesResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Story AutoScore and prioritization.
 */
@RestController
@RequestMapping("/api")
public class StoryController {

    private final StoryPriorityService storyPriorityService;

    public StoryController(StoryPriorityService storyPriorityService) {
        this.storyPriorityService = storyPriorityService;
    }

    /**
     * Get stories for an epic sorted by AutoScore.
     *
     * GET /api/epics/{epicKey}/stories?sort=autoscore
     */
    @GetMapping("/epics/{epicKey}/stories")
    public ResponseEntity<StoriesResponse> getStoriesWithScore(@PathVariable String epicKey) {
        StoriesResponse response = storyPriorityService.getStoriesWithScore(epicKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Recalculate AutoScore for all stories or stories in a specific epic.
     *
     * POST /api/planning/recalculate-stories?epicKey=LB-100
     */
    @PostMapping("/planning/recalculate-stories")
    public ResponseEntity<RecalculateResponse> recalculateStories(
            @RequestParam(required = false) String epicKey
    ) {
        RecalculateResponse response = storyPriorityService.recalculateStories(epicKey);
        return ResponseEntity.ok(response);
    }
}
