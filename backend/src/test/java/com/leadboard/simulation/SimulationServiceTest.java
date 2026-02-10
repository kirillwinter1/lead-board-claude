package com.leadboard.simulation;

import com.leadboard.simulation.dto.SimulationAction;
import com.leadboard.simulation.dto.SimulationLogDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimulationServiceTest {

    @Mock private SimulationPlanner planner;
    @Mock private SimulationExecutor executor;
    @Mock private SimulationLogRepository logRepository;

    private SimulationService service;

    private static final Long TEAM_ID = 1L;
    private static final LocalDate SIM_DATE = LocalDate.of(2025, 6, 2);

    @BeforeEach
    void setUp() {
        service = new SimulationService(planner, executor, logRepository);

        // No concurrent runs
        when(logRepository.existsByStatus("RUNNING")).thenReturn(false);

        // Save returns the entity as-is (simulate JPA)
        when(logRepository.save(any())).thenAnswer(inv -> {
            SimulationLogEntity entity = inv.getArgument(0);
            if (entity.getId() == null) entity.setId(1L);
            return entity;
        });
    }

    @Test
    void runSimulation_dryRun_plansButDoesNotExecute() {
        List<SimulationAction> planned = List.of(
                SimulationAction.transition("PROJ-11", "Sub-task", "Dev One",
                        "New", "In Progress", "Starting"),
                SimulationAction.worklog("PROJ-12", "Sub-task", "Dev One",
                        6.0, "Work")
        );
        when(planner.planDay(TEAM_ID, SIM_DATE)).thenReturn(planned);

        SimulationLogDto result = service.runSimulation(TEAM_ID, SIM_DATE, true);

        assertNotNull(result);
        assertEquals(TEAM_ID, result.teamId());
        assertEquals(SIM_DATE, result.simDate());
        assertTrue(result.dryRun());
        assertEquals("COMPLETED", result.status());
        assertEquals(2, result.actions().size());
        assertNotNull(result.summary());
        assertEquals(2, result.summary().totalActions());
        assertEquals(0, result.summary().transitionsExecuted());

        // Executor should NOT be called for dry run
        verifyNoInteractions(executor);
    }

    @Test
    void runSimulation_realRun_executesActions() {
        List<SimulationAction> planned = List.of(
                SimulationAction.worklog("PROJ-11", "Sub-task", "Dev One",
                        6.0, "Work")
        );
        when(planner.planDay(TEAM_ID, SIM_DATE)).thenReturn(planned);

        List<SimulationAction> executed = List.of(
                planned.get(0).withExecuted()
        );
        when(executor.execute(planned, SIM_DATE, TEAM_ID)).thenReturn(executed);

        SimulationLogDto result = service.runSimulation(TEAM_ID, SIM_DATE, false);

        assertNotNull(result);
        assertFalse(result.dryRun());
        assertEquals("COMPLETED", result.status());
        assertEquals(1, result.summary().worklogsExecuted());
        assertEquals(6.0, result.summary().totalHoursLogged(), 0.01);

        verify(executor).execute(planned, SIM_DATE, TEAM_ID);
    }

    @Test
    void runSimulation_concurrentGuard_rejectsSecondRun() {
        when(logRepository.existsByStatus("RUNNING")).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.runSimulation(TEAM_ID, SIM_DATE, false));
    }

    @Test
    void runSimulation_plannerFails_logsError() {
        when(planner.planDay(TEAM_ID, SIM_DATE)).thenThrow(new RuntimeException("Planning failed"));

        assertThrows(RuntimeException.class,
                () -> service.runSimulation(TEAM_ID, SIM_DATE, false));

        // Verify log was updated with FAILED status
        ArgumentCaptor<SimulationLogEntity> captor = ArgumentCaptor.forClass(SimulationLogEntity.class);
        verify(logRepository, atLeast(2)).save(captor.capture());

        SimulationLogEntity lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("FAILED", lastSave.getStatus());
        assertNotNull(lastSave.getError());
    }

    @Test
    void runSimulation_noDate_usesToday() {
        when(planner.planDay(eq(TEAM_ID), any())).thenReturn(List.of());

        SimulationLogDto result = service.runSimulation(TEAM_ID, null, true);

        assertNotNull(result);
        assertEquals(LocalDate.now(), result.simDate());
    }

    @Test
    void isRunning_delegatesToRepository() {
        when(logRepository.existsByStatus("RUNNING")).thenReturn(true);
        assertTrue(service.isRunning());

        when(logRepository.existsByStatus("RUNNING")).thenReturn(false);
        assertFalse(service.isRunning());
    }
}
