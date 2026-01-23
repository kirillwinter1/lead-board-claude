package com.leadboard.planning;

import com.leadboard.planning.dto.AutoScoreDto;
import com.leadboard.planning.dto.UpdateBoostRequest;
import com.leadboard.sync.JiraIssueEntity;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST API для AutoScore.
 */
@RestController
@RequestMapping("/api/planning/autoscore")
public class AutoScoreController {

    private final AutoScoreService autoScoreService;

    public AutoScoreController(AutoScoreService autoScoreService) {
        this.autoScoreService = autoScoreService;
    }

    /**
     * Получает AutoScore для эпика с детализацией факторов.
     */
    @GetMapping("/epics/{epicKey}")
    public ResponseEntity<AutoScoreDto> getEpicScore(@PathVariable String epicKey) {
        AutoScoreService.AutoScoreDetails details = autoScoreService.getScoreDetails(epicKey);
        if (details == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new AutoScoreDto(
                details.epicKey(),
                null, // summary не загружается в getScoreDetails
                details.totalScore(),
                null, // manualBoost не возвращается здесь
                details.calculatedAt(),
                details.factors()
        ));
    }

    /**
     * Получает список эпиков команды с AutoScore.
     */
    @GetMapping("/teams/{teamId}/epics")
    public ResponseEntity<List<AutoScoreDto>> getTeamEpics(
            @PathVariable Long teamId,
            @RequestParam(required = false) List<String> statuses) {

        List<JiraIssueEntity> epics;
        if (statuses != null && !statuses.isEmpty()) {
            epics = autoScoreService.getEpicsByPriorityAndStatus(teamId, statuses);
        } else {
            epics = autoScoreService.getEpicsByPriority(teamId);
        }

        List<AutoScoreDto> dtos = epics.stream()
                .map(epic -> AutoScoreDto.basic(
                        epic.getIssueKey(),
                        epic.getSummary(),
                        epic.getAutoScore(),
                        epic.getManualPriorityBoost(),
                        epic.getAutoScoreCalculatedAt()
                ))
                .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Обновляет ручной boost приоритета для эпика.
     */
    @PatchMapping("/epics/{epicKey}/boost")
    public ResponseEntity<Map<String, Object>> updateBoost(
            @PathVariable String epicKey,
            @RequestBody @Valid UpdateBoostRequest request) {

        BigDecimal newScore = autoScoreService.updateManualBoost(epicKey, request.boost());
        if (newScore == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "epicKey", epicKey,
                "boost", request.boost(),
                "newAutoScore", newScore
        ));
    }

    /**
     * Пересчитывает AutoScore для всех эпиков.
     */
    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculateAll() {
        int count = autoScoreService.recalculateAll();
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "epicsUpdated", count
        ));
    }

    /**
     * Пересчитывает AutoScore для эпиков команды.
     */
    @PostMapping("/teams/{teamId}/recalculate")
    public ResponseEntity<Map<String, Object>> recalculateForTeam(@PathVariable Long teamId) {
        int count = autoScoreService.recalculateForTeam(teamId);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "teamId", teamId,
                "epicsUpdated", count
        ));
    }

    /**
     * Пересчитывает AutoScore для одного эпика.
     */
    @PostMapping("/epics/{epicKey}/recalculate")
    public ResponseEntity<Map<String, Object>> recalculateForEpic(@PathVariable String epicKey) {
        BigDecimal score = autoScoreService.recalculateForEpic(epicKey);
        if (score == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "epicKey", epicKey,
                "autoScore", score
        ));
    }
}
