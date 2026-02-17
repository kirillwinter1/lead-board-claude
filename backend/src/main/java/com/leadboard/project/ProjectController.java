package com.leadboard.project;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectAlignmentService alignmentService;

    public ProjectController(ProjectService projectService,
                             ProjectAlignmentService alignmentService) {
        this.projectService = projectService;
        this.alignmentService = alignmentService;
    }

    @GetMapping
    public ResponseEntity<List<ProjectDto>> listProjects() {
        return ResponseEntity.ok(projectService.listProjects());
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<ProjectTimelineDto>> getTimeline() {
        return ResponseEntity.ok(projectService.getTimelineData());
    }

    @GetMapping("/{issueKey}")
    public ResponseEntity<ProjectDetailDto> getProject(@PathVariable String issueKey) {
        try {
            return ResponseEntity.ok(projectService.getProjectWithEpics(issueKey));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD')")
    @GetMapping("/{issueKey}/recommendations")
    public ResponseEntity<List<ProjectRecommendation>> getRecommendations(@PathVariable String issueKey) {
        try {
            return ResponseEntity.ok(alignmentService.getRecommendations(issueKey));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
