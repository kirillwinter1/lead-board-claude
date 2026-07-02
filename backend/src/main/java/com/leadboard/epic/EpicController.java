package com.leadboard.epic;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/epics")
public class EpicController {

    private final EpicService epicService;

    public EpicController(EpicService epicService) {
        this.epicService = epicService;
    }

    @GetMapping("/config/rough-estimate")
    public ResponseEntity<RoughEstimateConfigDto> getRoughEstimateConfig() {
        return ResponseEntity.ok(epicService.getRoughEstimateConfig());
    }

    @GetMapping("/{epicKey}/detail")
    public ResponseEntity<EpicDetailDto> getEpicDetail(@PathVariable String epicKey) {
        try {
            return ResponseEntity.ok(epicService.getEpicDetail(epicKey));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{epicKey}/rough-estimate/{role}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')")
    public ResponseEntity<?> updateRoughEstimate(
            @PathVariable String epicKey,
            @PathVariable String role,
            @RequestBody RoughEstimateRequestDto request) {
        try {
            RoughEstimateResponseDto response = epicService.updateRoughEstimate(epicKey, role, request);
            return ResponseEntity.ok(response);
        } catch (RoughEstimateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
