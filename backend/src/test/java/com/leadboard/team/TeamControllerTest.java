package com.leadboard.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.config.JiraProperties;
import com.leadboard.planning.AutoScoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TeamController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TeamService teamService;

    @MockBean
    private TeamSyncService teamSyncService;

    @MockBean
    private MemberProfileService memberProfileService;

    @MockBean
    private JiraProperties jiraProperties;

    @MockBean
    private AutoScoreService autoScoreService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private SessionRepository sessionRepository;
    @MockBean
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        // By default, allow all team management operations
        when(authorizationService.canManageTeam(anyLong())).thenReturn(true);
    }

    // ==================== Team Tests ====================

    @Test
    void getAllTeamsReturnsEmptyList() throws Exception {
        when(teamService.getAllTeams()).thenReturn(List.of());

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAllTeamsReturnsList() throws Exception {
        TeamDto team = new TeamDto(1L, "Backend Team", "backend", true, 3,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(teamService.getAllTeams()).thenReturn(List.of(team));

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Backend Team"))
                .andExpect(jsonPath("$[0].memberCount").value(3));
    }

    @Test
    void getTeamReturnsTeam() throws Exception {
        TeamDto team = new TeamDto(1L, "Backend Team", "backend", true, 3,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(teamService.getTeam(1L)).thenReturn(team);

        mockMvc.perform(get("/api/teams/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Backend Team"));
    }

    @Test
    void getTeamReturns404WhenNotFound() throws Exception {
        when(teamService.getTeam(999L))
                .thenThrow(new TeamService.TeamNotFoundException("Team not found: 999"));

        mockMvc.perform(get("/api/teams/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Team not found: 999"));
    }

    @Test
    void createTeamReturns201() throws Exception {
        CreateTeamRequest request = new CreateTeamRequest("New Team", "new-team");
        TeamDto team = new TeamDto(1L, "New Team", "new-team", true, 0,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(teamService.createTeam(any())).thenReturn(team);

        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("New Team"));
    }

    @Test
    void createTeamReturns409WhenDuplicate() throws Exception {
        CreateTeamRequest request = new CreateTeamRequest("Existing Team", null);
        when(teamService.createTeam(any()))
                .thenThrow(new TeamService.TeamAlreadyExistsException("Team with name already exists: Existing Team"));

        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void createTeamReturns400WhenNameBlank() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTeamReturnsUpdatedTeam() throws Exception {
        UpdateTeamRequest request = new UpdateTeamRequest("Updated Name", "updated-value");
        TeamDto team = new TeamDto(1L, "Updated Name", "updated-value", true, 3,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(teamService.updateTeam(eq(1L), any())).thenReturn(team);

        mockMvc.perform(put("/api/teams/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void deleteTeamReturns204() throws Exception {
        doNothing().when(teamService).deactivateTeam(1L);

        mockMvc.perform(delete("/api/teams/1"))
                .andExpect(status().isNoContent());

        verify(teamService).deactivateTeam(1L);
    }

    // ==================== Team Member Tests ====================

    @Test
    void getTeamMembersReturnsList() throws Exception {
        TeamMemberDto member = new TeamMemberDto(1L, 1L, "acc-123", "John Doe",
                "DEV", Grade.SENIOR, new BigDecimal("8.0"), true,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(teamService.getTeamMembers(1L)).thenReturn(List.of(member));

        mockMvc.perform(get("/api/teams/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].displayName").value("John Doe"))
                .andExpect(jsonPath("$[0].role").value("DEV"))
                .andExpect(jsonPath("$[0].grade").value("SENIOR"));
    }

    @Test
    void addTeamMemberReturns201() throws Exception {
        CreateTeamMemberRequest request = new CreateTeamMemberRequest(
                "acc-456", "Jane Smith", "QA", Grade.MIDDLE, new BigDecimal("6.0"));
        TeamMemberDto member = new TeamMemberDto(2L, 1L, "acc-456", "Jane Smith",
                "QA", Grade.MIDDLE, new BigDecimal("6.0"), true,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(teamService.addTeamMember(eq(1L), any())).thenReturn(member);

        mockMvc.perform(post("/api/teams/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Jane Smith"))
                .andExpect(jsonPath("$.role").value("QA"));
    }

    @Test
    void addTeamMemberReturns409WhenDuplicate() throws Exception {
        CreateTeamMemberRequest request = new CreateTeamMemberRequest(
                "acc-123", "Duplicate", "DEV", Grade.JUNIOR, null);
        when(teamService.addTeamMember(eq(1L), any()))
                .thenThrow(new TeamService.TeamMemberAlreadyExistsException(
                        "Member with Jira account ID already exists in team: acc-123"));

        mockMvc.perform(post("/api/teams/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateTeamMemberReturnsUpdatedMember() throws Exception {
        UpdateTeamMemberRequest request = new UpdateTeamMemberRequest(
                "Updated Name", "SA", Grade.SENIOR, new BigDecimal("7.5"));
        TeamMemberDto member = new TeamMemberDto(1L, 1L, "acc-123", "Updated Name",
                "SA", Grade.SENIOR, new BigDecimal("7.5"), true,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(teamService.updateTeamMember(eq(1L), eq(1L), any())).thenReturn(member);

        mockMvc.perform(put("/api/teams/1/members/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Name"))
                .andExpect(jsonPath("$.role").value("SA"));
    }

    @Test
    void deactivateTeamMemberReturns204() throws Exception {
        doNothing().when(teamService).deactivateTeamMember(1L, 1L);

        mockMvc.perform(post("/api/teams/1/members/1/deactivate"))
                .andExpect(status().isNoContent());

        verify(teamService).deactivateTeamMember(1L, 1L);
    }

    @Test
    void addTeamMemberReturns400WhenHoursExceed12() throws Exception {
        CreateTeamMemberRequest request = new CreateTeamMemberRequest(
                "acc-789", "Overworker", "DEV", Grade.MIDDLE, new BigDecimal("15.0"));

        mockMvc.perform(post("/api/teams/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
