package com.leadboard.sync;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/status")
    public ResponseEntity<SyncService.SyncStatus> getStatus() {
        return ResponseEntity.ok(syncService.getSyncStatus());
    }

    @PostMapping("/trigger")
    public ResponseEntity<SyncService.SyncStatus> triggerSync() {
        return ResponseEntity.ok(syncService.triggerSync());
    }
}
