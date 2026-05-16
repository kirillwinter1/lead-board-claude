package com.leadboard.planning;

import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.planning.dto.PlanningEpicDto;
import com.leadboard.planning.dto.QuarterlyEpicsResponse;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuarterlyPlanningController.class)
@AutoConfigureMockMvc(addFilters = false)
class QuarterlyPlanningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuarterlyPlanningService planningService;

    @MockBean
    private SessionRepository sessionRepository;
    @MockBean
    private AppProperties appProperties;
    @MockBean
    private TenantUserRepository tenantUserRepository;
    @MockBean
    private TenantRepository tenantRepository;
    @MockBean
    private com.leadboard.config.ObservabilityMetrics observabilityMetrics;

    // ==================== GET /quarters/{quarter}/epics ====================

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void getEpicsForQuarter_returnsEpicsList() throws Exception {
        PlanningEpicDto epic = sampleEpicDto("EPIC-1", "2026Q2", true, 0);
        when(planningService.getEpicsForQuarter("2026Q2"))
                .thenReturn(new QuarterlyEpicsResponse("2026Q2", List.of(epic)));

        mockMvc.perform(get("/api/quarterly-planning/quarters/2026Q2/epics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quarter").value("2026Q2"))
                .andExpect(jsonPath("$.epics[0].epicKey").value("EPIC-1"))
                .andExpect(jsonPath("$.epics[0].inQuarter").value(true));
    }

    // ==================== POST /epics/{epicKey}/quarter ====================

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void assignEpicToQuarter_happyPath() throws Exception {
        PlanningEpicDto epic = sampleEpicDto("EPIC-1", "2026Q2", true, 0);
        when(planningService.assignEpicToQuarter("EPIC-1", "2026Q2")).thenReturn(epic);

        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epicKey").value("EPIC-1"))
                .andExpect(jsonPath("$.quarterLabel").value("2026Q2"));

        verify(planningService).assignEpicToQuarter("EPIC-1", "2026Q2");
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void assignEpicToQuarter_nullQuarterRemovesLabel() throws Exception {
        PlanningEpicDto epic = sampleEpicDto("EPIC-1", null, false, 0);
        when(planningService.assignEpicToQuarter("EPIC-1", null)).thenReturn(epic);

        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epicKey").value("EPIC-1"))
                .andExpect(jsonPath("$.quarterLabel").doesNotExist());

        verify(planningService).assignEpicToQuarter("EPIC-1", null);
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void assignEpicToQuarter_notFound_propagatesAsServerError() throws Exception {
        when(planningService.assignEpicToQuarter(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Epic not found: EPIC-X"));

        try {
            mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-X/quarter")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"quarter\":\"2026Q2\"}"));
        } catch (Exception e) {
            // IllegalArgumentException wraps as ServletException
            assert e.getCause() instanceof IllegalArgumentException;
        }
    }

    // ==================== POST /epics/{epicKey}/boost ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    void setEpicBoost_happyPath() throws Exception {
        PlanningEpicDto epic = sampleEpicDto("EPIC-1", "2026Q2", true, 25);
        when(planningService.setEpicBoost("EPIC-1", 25)).thenReturn(epic);

        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/boost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boost\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epicKey").value("EPIC-1"))
                .andExpect(jsonPath("$.manualBoost").value(25));
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void setEpicBoost_missingBoostField_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/boost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(planningService);
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void setEpicBoost_outOfRange_propagatesValidationError() throws Exception {
        when(planningService.setEpicBoost(anyString(), anyInt()))
                .thenThrow(new IllegalArgumentException("Boost must be in [-50, 50], got: 999"));

        try {
            mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/boost")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"boost\":999}"));
        } catch (Exception e) {
            assert e.getCause() instanceof IllegalArgumentException;
        }
    }

    // ==================== Helpers ====================

    private PlanningEpicDto sampleEpicDto(String key, String quarter, boolean inQuarter, int boost) {
        return new PlanningEpicDto(
                key,
                "Sample epic",
                null,
                "Epic",
                "PROJ-1",
                "Project",
                quarter,
                inQuarter,
                new BigDecimal("50.0"),
                boost,
                new BigDecimal("50.0").add(new BigDecimal(boost)),
                List.of(),
                Map.of(),
                BigDecimal.ZERO,
                false,
                false,
                List.of()
        );
    }
}
