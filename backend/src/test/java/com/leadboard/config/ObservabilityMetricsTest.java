package com.leadboard.config;

import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.tenant.TenantRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservabilityMetricsTest {

    private MeterRegistry registry;
    private ObservabilityMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        JiraIssueRepository issueRepo = mock(JiraIssueRepository.class);
        TenantRepository tenantRepo = mock(TenantRepository.class);
        when(tenantRepo.findAllActive()).thenReturn(Collections.emptyList());
        metrics = new ObservabilityMetrics(registry, issueRepo, tenantRepo);
    }

    @Test
    @DisplayName("recordSyncDetails increments created and updated counters")
    void recordSyncDetails() {
        metrics.recordSyncDetails(5, 10);

        double created = registry.counter("leadboard.sync.issues_created").count();
        double updated = registry.counter("leadboard.sync.issues_updated").count();
        assertEquals(5.0, created);
        assertEquals(10.0, updated);
    }

    @Test
    @DisplayName("recordSyncSuccess sets last success timestamp")
    void recordSyncSuccess() {
        metrics.recordSyncSuccess();

        double timestamp = registry.get("leadboard.sync.last_success_timestamp").gauge().value();
        assertTrue(timestamp > 0, "Timestamp should be set to current epoch seconds");
    }

    @Test
    @DisplayName("recordRateLimitHit increments rate limit counter")
    void recordRateLimitHit() {
        metrics.recordRateLimitHit();
        metrics.recordRateLimitHit();

        double hits = registry.counter("leadboard.rate_limit.hits").count();
        assertEquals(2.0, hits);
    }

    @Test
    @DisplayName("recordError increments error counter with type tag")
    void recordError() {
        metrics.recordError("jira_api");
        metrics.recordError("jira_api");
        metrics.recordError("db");

        double jiraErrors = registry.counter("leadboard.errors.total", "type", "jira_api").count();
        double dbErrors = registry.counter("leadboard.errors.total", "type", "db").count();
        assertEquals(2.0, jiraErrors);
        assertEquals(1.0, dbErrors);
    }

    @Test
    @DisplayName("recordSyncError increments both sync errors and error counter")
    void recordSyncError() {
        metrics.recordSyncError();

        double syncErrors = registry.counter("leadboard.sync.errors").count();
        double jiraApiErrors = registry.counter("leadboard.errors.total", "type", "jira_api").count();
        assertEquals(1.0, syncErrors);
        assertEquals(1.0, jiraApiErrors);
    }

    @Test
    @DisplayName("existing metrics still work - issues synced")
    void existingMetrics() {
        metrics.recordIssuesSynced(42);

        double synced = registry.counter("leadboard.sync.issues_synced").count();
        assertEquals(42.0, synced);
    }
}
