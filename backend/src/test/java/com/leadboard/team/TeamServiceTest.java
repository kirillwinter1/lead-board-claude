package com.leadboard.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.team.dto.PlanningConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository memberRepository;

    private ObjectMapper objectMapper;
    private TeamService teamService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        teamService = new TeamService(teamRepository, memberRepository, objectMapper);
    }

    // ==================== Team Tests ====================

    @Test
    void getAllTeamsReturnsActiveTeams() {
        TeamEntity team1 = createTeamEntity(1L, "Team 1");
        TeamEntity team2 = createTeamEntity(2L, "Team 2");
        when(teamRepository.findByActiveTrue()).thenReturn(List.of(team1, team2));

        List<TeamDto> result = teamService.getAllTeams();

        assertEquals(2, result.size());
        assertEquals("Team 1", result.get(0).name());
        assertEquals("Team 2", result.get(1).name());
    }

    @Test
    void getTeamReturnsTeamWhenExists() {
        TeamEntity team = createTeamEntity(1L, "Backend Team");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        TeamDto result = teamService.getTeam(1L);

        assertEquals("Backend Team", result.name());
    }

    @Test
    void getTeamThrowsWhenNotFound() {
        when(teamRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        assertThrows(TeamService.TeamNotFoundException.class,
                () -> teamService.getTeam(999L));
    }

    @Test
    void createTeamSavesAndReturnsTeam() {
        CreateTeamRequest request = new CreateTeamRequest("New Team", "new-team");
        when(teamRepository.existsByNameAndActiveTrue("New Team")).thenReturn(false);
        when(teamRepository.save(any())).thenAnswer(invocation -> {
            TeamEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        TeamDto result = teamService.createTeam(request);

        assertEquals("New Team", result.name());
        assertEquals("new-team", result.jiraTeamValue());
        verify(teamRepository).save(any(TeamEntity.class));
    }

    @Test
    void createTeamThrowsWhenNameExists() {
        CreateTeamRequest request = new CreateTeamRequest("Existing Team", null);
        when(teamRepository.existsByNameAndActiveTrue("Existing Team")).thenReturn(true);

        assertThrows(TeamService.TeamAlreadyExistsException.class,
                () -> teamService.createTeam(request));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateTeamUpdatesFields() {
        TeamEntity team = createTeamEntity(1L, "Old Name");
        team.setJiraTeamValue("old-value");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(teamRepository.existsByNameAndActiveTrue("New Name")).thenReturn(false);
        when(teamRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTeamRequest request = new UpdateTeamRequest("New Name", "new-value");
        TeamDto result = teamService.updateTeam(1L, request);

        assertEquals("New Name", result.name());
        assertEquals("new-value", result.jiraTeamValue());
    }

    @Test
    void deactivateTeamSetsActiveToFalse() {
        TeamEntity team = createTeamEntity(1L, "Team to Deactivate");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(teamRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        teamService.deactivateTeam(1L);

        assertFalse(team.getActive());
        verify(teamRepository).save(team);
    }

    // ==================== Team Member Tests ====================

    @Test
    void getTeamMembersReturnsActiveMembers() {
        TeamEntity team = createTeamEntity(1L, "Team");
        TeamMemberEntity member = createMemberEntity(1L, team, "acc-123", "John Doe");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(member));

        List<TeamMemberDto> result = teamService.getTeamMembers(1L);

        assertEquals(1, result.size());
        assertEquals("John Doe", result.get(0).displayName());
    }

    @Test
    void addTeamMemberCreatesNewMember() {
        TeamEntity team = createTeamEntity(1L, "Team");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.existsByTeamIdAndJiraAccountIdAndActiveTrue(1L, "acc-456")).thenReturn(false);
        when(memberRepository.save(any())).thenAnswer(invocation -> {
            TeamMemberEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        CreateTeamMemberRequest request = new CreateTeamMemberRequest(
                "acc-456", "Jane Smith", Role.QA, Grade.SENIOR, new BigDecimal("7.0"));
        TeamMemberDto result = teamService.addTeamMember(1L, request);

        assertEquals("Jane Smith", result.displayName());
        assertEquals(Role.QA, result.role());
        assertEquals(Grade.SENIOR, result.grade());
        assertEquals(new BigDecimal("7.0"), result.hoursPerDay());
    }

    @Test
    void addTeamMemberUsesDefaultsWhenNull() {
        TeamEntity team = createTeamEntity(1L, "Team");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.existsByTeamIdAndJiraAccountIdAndActiveTrue(1L, "acc-789")).thenReturn(false);
        when(memberRepository.save(any())).thenAnswer(invocation -> {
            TeamMemberEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        CreateTeamMemberRequest request = new CreateTeamMemberRequest(
                "acc-789", "Default Member", null, null, null);
        TeamMemberDto result = teamService.addTeamMember(1L, request);

        assertEquals(Role.DEV, result.role());
        assertEquals(Grade.MIDDLE, result.grade());
        assertEquals(new BigDecimal("6.0"), result.hoursPerDay());
    }

    @Test
    void addTeamMemberThrowsWhenDuplicate() {
        TeamEntity team = createTeamEntity(1L, "Team");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.existsByTeamIdAndJiraAccountIdAndActiveTrue(1L, "acc-123")).thenReturn(true);

        CreateTeamMemberRequest request = new CreateTeamMemberRequest(
                "acc-123", "Duplicate", Role.DEV, Grade.JUNIOR, null);

        assertThrows(TeamService.TeamMemberAlreadyExistsException.class,
                () -> teamService.addTeamMember(1L, request));
    }

    @Test
    void updateTeamMemberUpdatesFields() {
        TeamEntity team = createTeamEntity(1L, "Team");
        TeamMemberEntity member = createMemberEntity(1L, team, "acc-123", "Old Name");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByIdAndTeamIdAndActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(memberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTeamMemberRequest request = new UpdateTeamMemberRequest(
                "New Name", Role.SA, Grade.SENIOR, new BigDecimal("8.0"));
        TeamMemberDto result = teamService.updateTeamMember(1L, 1L, request);

        assertEquals("New Name", result.displayName());
        assertEquals(Role.SA, result.role());
        assertEquals(Grade.SENIOR, result.grade());
        assertEquals(new BigDecimal("8.0"), result.hoursPerDay());
    }

    @Test
    void deactivateTeamMemberSetsActiveToFalse() {
        TeamEntity team = createTeamEntity(1L, "Team");
        TeamMemberEntity member = createMemberEntity(1L, team, "acc-123", "John Doe");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByIdAndTeamIdAndActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(memberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        teamService.deactivateTeamMember(1L, 1L);

        assertFalse(member.getActive());
        verify(memberRepository).save(member);
    }

    // ==================== Planning Config Tests ====================

    @Test
    void getPlanningConfigReturnsDefaultsWhenNull() {
        TeamEntity team = createTeamEntity(1L, "Team");
        team.setPlanningConfig(null);
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        PlanningConfigDto result = teamService.getPlanningConfig(1L);

        assertEquals(new BigDecimal("0.8"), result.gradeCoefficients().senior());
        assertEquals(new BigDecimal("1.0"), result.gradeCoefficients().middle());
        assertEquals(new BigDecimal("1.5"), result.gradeCoefficients().junior());
        assertEquals(new BigDecimal("0.2"), result.riskBuffer());
        assertEquals(6, result.wipLimits().team());
    }

    @Test
    void getPlanningConfigReturnsStoredConfig() throws Exception {
        TeamEntity team = createTeamEntity(1L, "Team");
        PlanningConfigDto config = new PlanningConfigDto(
                new PlanningConfigDto.GradeCoefficients(
                        new BigDecimal("0.7"),
                        new BigDecimal("1.0"),
                        new BigDecimal("1.8")
                ),
                new BigDecimal("0.3"),
                new PlanningConfigDto.WipLimits(8, 3, 4, 3),
                PlanningConfigDto.StoryDuration.defaults()
        );
        team.setPlanningConfig(objectMapper.writeValueAsString(config));
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        PlanningConfigDto result = teamService.getPlanningConfig(1L);

        assertEquals(new BigDecimal("0.7"), result.gradeCoefficients().senior());
        assertEquals(new BigDecimal("0.3"), result.riskBuffer());
        assertEquals(8, result.wipLimits().team());
    }

    @Test
    void updatePlanningConfigSavesConfig() {
        TeamEntity team = createTeamEntity(1L, "Team");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlanningConfigDto config = new PlanningConfigDto(
                new PlanningConfigDto.GradeCoefficients(
                        new BigDecimal("0.9"),
                        new BigDecimal("1.0"),
                        new BigDecimal("1.3")
                ),
                new BigDecimal("0.15"),
                new PlanningConfigDto.WipLimits(5, 2, 3, 2),
                PlanningConfigDto.StoryDuration.defaults()
        );

        PlanningConfigDto result = teamService.updatePlanningConfig(1L, config);

        assertEquals(new BigDecimal("0.9"), result.gradeCoefficients().senior());
        assertEquals(new BigDecimal("0.15"), result.riskBuffer());
        verify(teamRepository).save(team);
        assertNotNull(team.getPlanningConfig());
    }

    @Test
    void updatePlanningConfigThrowsOnNegativeRiskBuffer() {
        TeamEntity team = createTeamEntity(1L, "Team");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        PlanningConfigDto config = new PlanningConfigDto(
                PlanningConfigDto.GradeCoefficients.defaults(),
                new BigDecimal("-0.1"),
                PlanningConfigDto.WipLimits.defaults(),
                PlanningConfigDto.StoryDuration.defaults()
        );

        assertThrows(TeamService.InvalidPlanningConfigException.class,
                () -> teamService.updatePlanningConfig(1L, config));
    }

    @Test
    void updatePlanningConfigThrowsOnZeroGradeCoefficient() {
        TeamEntity team = createTeamEntity(1L, "Team");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        PlanningConfigDto config = new PlanningConfigDto(
                new PlanningConfigDto.GradeCoefficients(
                        BigDecimal.ZERO,
                        new BigDecimal("1.0"),
                        new BigDecimal("1.5")
                ),
                new BigDecimal("0.2"),
                PlanningConfigDto.WipLimits.defaults(),
                PlanningConfigDto.StoryDuration.defaults()
        );

        assertThrows(TeamService.InvalidPlanningConfigException.class,
                () -> teamService.updatePlanningConfig(1L, config));
    }

    @Test
    void updatePlanningConfigThrowsOnInvalidWipLimit() {
        TeamEntity team = createTeamEntity(1L, "Team");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        PlanningConfigDto config = new PlanningConfigDto(
                PlanningConfigDto.GradeCoefficients.defaults(),
                new BigDecimal("0.2"),
                new PlanningConfigDto.WipLimits(0, 2, 3, 2),
                PlanningConfigDto.StoryDuration.defaults()
        );

        assertThrows(TeamService.InvalidPlanningConfigException.class,
                () -> teamService.updatePlanningConfig(1L, config));
    }

    // ==================== Helper Methods ====================

    private TeamEntity createTeamEntity(Long id, String name) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setName(name);
        team.setActive(true);
        return team;
    }

    private TeamMemberEntity createMemberEntity(Long id, TeamEntity team, String accountId, String displayName) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(id);
        member.setTeam(team);
        member.setJiraAccountId(accountId);
        member.setDisplayName(displayName);
        member.setRole(Role.DEV);
        member.setGrade(Grade.MIDDLE);
        member.setHoursPerDay(new BigDecimal("6.0"));
        member.setActive(true);
        return member;
    }
}
