package com.leadboard.simulation;

import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationSchedulerTest {

    @Mock
    private SimulationService simulationService;

    @Mock
    private SimulationRecoveryService recoveryService;

    @Mock
    private SimulationProperties properties;

    @Mock
    private TenantRepository tenantRepository;

    @AfterEach
    void tearDown() {
        // TenantContext is a ThreadLocal — ensure it never leaks to other test classes.
        TenantContext.clear();
    }

    private SimulationScheduler newScheduler() {
        return new SimulationScheduler(simulationService, recoveryService, properties, tenantRepository);
    }

    private TenantEntity tenant(long id, String schema, String slug) {
        TenantEntity t = new TenantEntity();
        t.setId(id);
        t.setSchemaName(schema);
        t.setSlug(slug);
        return t;
    }

    @Test
    @DisplayName("BUG-77: sets TenantContext per tenant and clears it afterwards")
    void shouldSetTenantContextPerTenant() {
        when(properties.getTeamIds()).thenReturn(List.of(1L));
        when(tenantRepository.findAllActive()).thenReturn(List.of(
                tenant(10L, "schema_a", "tenant-a"),
                tenant(20L, "schema_b", "tenant-b")
        ));

        // Capture the tenant id active AT THE MOMENT the inner simulation runs.
        List<Long> seenTenantIds = new ArrayList<>();
        doAnswer(inv -> {
            seenTenantIds.add(TenantContext.getCurrentTenantId());
            return null;
        }).when(simulationService).runSimulation(eq(1L), any(), eq(false));

        newScheduler().runScheduled();

        assertEquals(List.of(10L, 20L), seenTenantIds);
        assertNull(TenantContext.getCurrentTenantId(), "TenantContext must be cleared after the run");
        verify(recoveryService, times(2)).recoverStuckSimulations();
        verify(simulationService, times(2)).runSimulation(eq(1L), any(), eq(false));
    }

    @Test
    @DisplayName("single-tenant fallback: runs once without TenantContext when no active tenants")
    void shouldRunSingleTenantFallbackWithoutContext() {
        when(properties.getTeamIds()).thenReturn(List.of(1L));
        when(tenantRepository.findAllActive()).thenReturn(List.of());

        List<Long> seenTenantIds = new ArrayList<>();
        doAnswer(inv -> {
            seenTenantIds.add(TenantContext.getCurrentTenantId());
            return null;
        }).when(simulationService).runSimulation(eq(1L), any(), eq(false));

        newScheduler().runScheduled();

        assertEquals(1, seenTenantIds.size());
        assertNull(seenTenantIds.get(0), "Single-tenant fallback must run without a TenantContext");
        verify(simulationService, times(1)).runSimulation(eq(1L), any(), eq(false));
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    @DisplayName("tenant failure is isolated: loop continues and context is cleared in finally")
    void shouldIsolateTenantFailureAndClearContext() {
        when(properties.getTeamIds()).thenReturn(List.of(1L));
        when(tenantRepository.findAllActive()).thenReturn(List.of(
                tenant(10L, "schema_a", "tenant-a"),
                tenant(20L, "schema_b", "tenant-b")
        ));

        // First tenant's recovery throws; second must still be processed.
        doThrow(new RuntimeException("boom")).doNothing()
                .when(recoveryService).recoverStuckSimulations();

        List<Long> seenTenantIds = new ArrayList<>();
        doAnswer(inv -> {
            seenTenantIds.add(TenantContext.getCurrentTenantId());
            return null;
        }).when(simulationService).runSimulation(eq(1L), any(), eq(false));

        assertDoesNotThrow(() -> newScheduler().runScheduled());

        // Tenant 10 failed before runSimulation; tenant 20 still ran.
        assertEquals(List.of(20L), seenTenantIds);
        assertNull(TenantContext.getCurrentTenantId(), "finally must clear context even when a tenant fails");
        verify(recoveryService, times(2)).recoverStuckSimulations();
    }

    @Test
    @DisplayName("empty teamIds: returns early without touching tenants or services")
    void shouldReturnEarlyWhenNoTeamIds() {
        when(properties.getTeamIds()).thenReturn(List.of());

        newScheduler().runScheduled();

        verifyNoInteractions(tenantRepository);
        verifyNoInteractions(simulationService);
        verifyNoInteractions(recoveryService);
    }
}
