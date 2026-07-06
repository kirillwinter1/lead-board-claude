package com.leadboard.planning;

import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.jira.JiraClientException;
import com.leadboard.planning.dto.PlanningEpicDto;
import com.leadboard.planning.dto.ProjectQuarterCommitmentDto;
import com.leadboard.planning.dto.QuarterlyEpicsResponse;
import com.leadboard.planning.dto.TeamCommitmentDto;
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

import static org.hamcrest.Matchers.containsString;
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
    private com.leadboard.auth.AuthorizationService authorizationService;

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
        // F70: the controller now calls the two-arg overload with onlyDesired
        // defaulting to true. The default semantically matches "show what PM asked for".
        PlanningEpicDto epic = sampleEpicDto("EPIC-1", "2026Q2", true, 0);
        when(planningService.getEpicsForQuarter("2026Q2", true, null))
                .thenReturn(new QuarterlyEpicsResponse("2026Q2", List.of(epic)));

        mockMvc.perform(get("/api/quarterly-planning/quarters/2026Q2/epics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quarter").value("2026Q2"))
                .andExpect(jsonPath("$.epics[0].epicKey").value("EPIC-1"))
                .andExpect(jsonPath("$.epics[0].inQuarter").value(true));

        verify(planningService).getEpicsForQuarter("2026Q2", true, null);
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void getEpicsForQuarter_onlyDesiredFalse_isPassedToService() throws Exception {
        // The query string toggle must reach the service unchanged — frontends
        // rely on it to render the "Show all" view.
        PlanningEpicDto epic = sampleEpicDto("EPIC-1", "2026Q2", true, 0);
        when(planningService.getEpicsForQuarter("2026Q2", false, null))
                .thenReturn(new QuarterlyEpicsResponse("2026Q2", List.of(epic)));

        mockMvc.perform(get("/api/quarterly-planning/quarters/2026Q2/epics?onlyDesired=false"))
                .andExpect(status().isOk());

        verify(planningService).getEpicsForQuarter("2026Q2", false, null);
    }

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void getEpicsForQuarter_teamLead_isScopedToUserTeams() throws Exception {
        // TEAM_LEAD callers must see only epics from their own team(s). The
        // controller derives the scope from AuthorizationService and forwards
        // it to the service layer — the service applies the filter.
        java.util.Set<Long> teams = java.util.Set.of(1L, 2L);
        when(authorizationService.isTeamLead()).thenReturn(true);
        when(authorizationService.getUserTeamIds()).thenReturn(teams);
        when(planningService.getEpicsForQuarter("2026Q2", true, teams))
                .thenReturn(new QuarterlyEpicsResponse("2026Q2", List.of()));

        mockMvc.perform(get("/api/quarterly-planning/quarters/2026Q2/epics"))
                .andExpect(status().isOk());

        verify(planningService).getEpicsForQuarter("2026Q2", true, teams);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEpicsForQuarter_admin_isNotScoped() throws Exception {
        // ADMIN must keep the cross-team view — controller must NOT call
        // getUserTeamIds() and must pass null to the service.
        when(authorizationService.isTeamLead()).thenReturn(false);
        when(planningService.getEpicsForQuarter("2026Q2", true, null))
                .thenReturn(new QuarterlyEpicsResponse("2026Q2", List.of()));

        mockMvc.perform(get("/api/quarterly-planning/quarters/2026Q2/epics"))
                .andExpect(status().isOk());

        verify(planningService).getEpicsForQuarter("2026Q2", true, null);
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
    void assignEpicToQuarter_notFound_returnsNotFound() throws Exception {
        // EpicNotFoundException carries @ResponseStatus(NOT_FOUND) so Spring maps it to 404.
        when(planningService.assignEpicToQuarter(anyString(), anyString()))
                .thenThrow(new EpicNotFoundException("Epic not found: EPIC-X"));

        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-X/quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void assignEpicToQuarter_whenJiraFails_returns502() throws Exception {
        // JiraClientException carries @ResponseStatus(BAD_GATEWAY) — upstream Jira failure
        // surfaces as 502, not 500, so the frontend can distinguish service bugs from
        // upstream outages.
        when(planningService.assignEpicToQuarter(anyString(), anyString()))
                .thenThrow(new JiraClientException("Jira 4xx (403) for issue LB-9: forbidden"));

        mockMvc.perform(post("/api/quarterly-planning/epics/LB-9/quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isBadGateway());
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
    void setEpicBoost_outOfRange_returnsBadRequest() throws Exception {
        // GlobalExceptionHandler maps IllegalArgumentException → 400 with a JSON body
        // shaped as {"error": "<message>"}.
        when(planningService.setEpicBoost(anyString(), anyInt()))
                .thenThrow(new IllegalArgumentException("Boost must be in [-50, 50], got: 999"));

        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/boost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boost\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Boost must be in")));
    }

    // ==================== F70: POST /projects/{key}/desired-quarter ====================

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void setProjectDesiredQuarter_happyPath_returnsCommitmentDto() throws Exception {
        ProjectQuarterCommitmentDto commitment = new ProjectQuarterCommitmentDto(
                "PROJ-1", "Project Alpha", "2026Q2",
                List.of(new TeamCommitmentDto(1L, "Alpha", "#1558BC", 3, 2, 1, 0))
        );
        when(planningService.setProjectDesiredQuarter("PROJ-1", "2026Q2")).thenReturn(commitment);

        mockMvc.perform(post("/api/quarterly-planning/projects/PROJ-1/desired-quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectKey").value("PROJ-1"))
                .andExpect(jsonPath("$.desiredQuarter").value("2026Q2"))
                .andExpect(jsonPath("$.commitmentByTeam[0].teamName").value("Alpha"))
                .andExpect(jsonPath("$.commitmentByTeam[0].committedEpics").value(2));

        verify(planningService).setProjectDesiredQuarter("PROJ-1", "2026Q2");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setProjectDesiredQuarter_nullQuarter_clearsLabel() throws Exception {
        // null in body clears the desired quarter; the service is responsible for
        // dropping the YYYYQn label from Jira and the DB mirror.
        ProjectQuarterCommitmentDto commitment = new ProjectQuarterCommitmentDto(
                "PROJ-1", "Project Alpha", null, List.of()
        );
        when(planningService.setProjectDesiredQuarter("PROJ-1", null)).thenReturn(commitment);

        mockMvc.perform(post("/api/quarterly-planning/projects/PROJ-1/desired-quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredQuarter").doesNotExist());

        verify(planningService).setProjectDesiredQuarter("PROJ-1", null);
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void setProjectDesiredQuarter_invalidQuarter_returnsBadRequest() throws Exception {
        when(planningService.setProjectDesiredQuarter(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Invalid quarter label: not-a-quarter"));

        mockMvc.perform(post("/api/quarterly-planning/projects/PROJ-1/desired-quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"not-a-quarter\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Invalid quarter")));
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void setProjectDesiredQuarter_projectNotFound_returns404() throws Exception {
        // ProjectNotFoundException carries @ResponseStatus(NOT_FOUND).
        when(planningService.setProjectDesiredQuarter(anyString(), anyString()))
                .thenThrow(new ProjectNotFoundException("Project not found: PROJ-X"));

        mockMvc.perform(post("/api/quarterly-planning/projects/PROJ-X/desired-quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isNotFound());
    }

    // ==================== F70: GET /projects/{key}/quarter-commitment ====================

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void getQuarterCommitment_happyPath() throws Exception {
        ProjectQuarterCommitmentDto commitment = new ProjectQuarterCommitmentDto(
                "PROJ-1", "Project Alpha", "2026Q2",
                List.of(
                        new TeamCommitmentDto(1L, "Alpha", "#1558BC", 4, 3, 1, 0),
                        new TeamCommitmentDto(2L, "Beta", "#FF0000", 2, 0, 0, 2)
                )
        );
        when(planningService.getProjectCommitment("PROJ-1")).thenReturn(commitment);

        mockMvc.perform(get("/api/quarterly-planning/projects/PROJ-1/quarter-commitment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitmentByTeam.length()").value(2))
                .andExpect(jsonPath("$.commitmentByTeam[1].uncommittedEpics").value(2));
    }

    // ==================== Helpers ====================

    private PlanningEpicDto sampleEpicDto(String key, String quarter, boolean inQuarter, int boost) {
        return new PlanningEpicDto(
                key,
                "Sample epic",
                "In Progress",
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
                Map.of(),
                BigDecimal.ZERO,
                false,
                false,
                List.of(),
                null,
                false
        );
    }
}
