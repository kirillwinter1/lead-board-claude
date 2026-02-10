package com.leadboard.simulation;

import com.leadboard.simulation.dto.SimulationLogDto;
import com.leadboard.simulation.dto.SimulationRunRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@PreAuthorize("hasRole('ADMIN')")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/run")
    public ResponseEntity<SimulationLogDto> run(@RequestBody SimulationRunRequest request) {
        SimulationLogDto result = simulationService.runSimulation(
                request.teamId(), request.date(), false);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/dry-run")
    public ResponseEntity<SimulationLogDto> dryRun(@RequestBody SimulationRunRequest request) {
        SimulationLogDto result = simulationService.runSimulation(
                request.teamId(), request.date(), true);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/logs")
    public ResponseEntity<List<SimulationLogDto>> getLogs(
            @RequestParam Long teamId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(simulationService.getLogs(teamId, from, to));
    }

    @GetMapping("/logs/{id}")
    public ResponseEntity<SimulationLogDto> getLog(@PathVariable Long id) {
        return ResponseEntity.ok(simulationService.getLog(id));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "running", simulationService.isRunning()
        ));
    }
}
