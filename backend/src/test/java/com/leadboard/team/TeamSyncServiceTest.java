package com.leadboard.team;

import com.leadboard.config.JiraProperties;
import com.leadboard.jira.AtlassianTeamsClient;
import com.leadboard.jira.AtlassianTeamsClient.*;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamSyncServiceTest {

    @Mock
    private AtlassianTeamsClient teamsClient;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository memberRepository;

    @Mock
    private JiraProperties jiraProperties;

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private TeamService teamService;

    private TeamSyncService teamSyncService;

    @BeforeEach
    void setUp() {
        when(teamService.nextAutoColor()).thenReturn("#0052CC");
        teamSyncService = new TeamSyncService(
                teamsClient,
                teamRepository,
                memberRepository,
                jiraProperties,
                issueRepository,
                teamService
        );

        // Common setup
        when(jiraProperties.getOrganizationId()).thenReturn("org-123");
        when(teamRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
    }

    // ==================== getStatus() Tests ====================

    @Nested
    @DisplayName("getStatus()")
    class GetStatusTests {

        @Test
        @DisplayName("should return initial status")
        void shouldReturnInitialStatus() {
            TeamSyncService.TeamSyncStatus status = teamSyncService.getStatus();

            assertFalse(status.syncInProgress());
            assertNull(status.lastSyncTime());
            assertNull(status.error());
        }
    }

    // ==================== syncTeams() Tests ====================

    @Nested
    @DisplayName("syncTeams()")
    class SyncTeamsTests {

        @Test
        @DisplayName("should create new teams from Atlassian")
        void shouldCreateNewTeams() {
            AtlassianTeam team = createAtlassianTeam("team-1", "Alpha Team", "ACTIVE");
            TeamsResponse response = createTeamsResponse(List.of(team));

            when(teamsClient.getTeams()).thenReturn(response);
            when(teamsClient.getTeamMembers("team-1")).thenReturn(createMembersResponse(Collections.emptyList()));
            when(teamRepository.findByAtlassianTeamId("team-1")).thenReturn(Optional.empty());
            when(teamRepository.save(any(TeamEntity.class))).thenAnswer(i -> i.getArgument(0));

            teamSyncService.syncTeams();

            ArgumentCaptor<TeamEntity> captor = ArgumentCaptor.forClass(TeamEntity.class);
            verify(teamRepository).save(captor.capture());

            assertEquals("Alpha Team", captor.getValue().getName());
            assertEquals("Alpha Team", captor.getValue().getJiraTeamValue());
            assertTrue(captor.getValue().getActive());
        }

        @Test
        @DisplayName("should update existing teams")
        void shouldUpdateExistingTeams() {
            AtlassianTeam atlassianTeam = createAtlassianTeam("team-1", "Updated Team Name", "ACTIVE");
            TeamsResponse response = createTeamsResponse(List.of(atlassianTeam));

            TeamEntity existingTeam = new TeamEntity();
            existingTeam.setId(1L);
            existingTeam.setAtlassianTeamId("team-1");
            existingTeam.setName("Old Team Name");
            existingTeam.setActive(true);

            when(teamsClient.getTeams()).thenReturn(response);
            when(teamsClient.getTeamMembers("team-1")).thenReturn(createMembersResponse(Collections.emptyList()));
            when(teamRepository.findByAtlassianTeamId("team-1")).thenReturn(Optional.of(existingTeam));
            when(teamRepository.save(any(TeamEntity.class))).thenAnswer(i -> i.getArgument(0));

            teamSyncService.syncTeams();

            ArgumentCaptor<TeamEntity> captor = ArgumentCaptor.forClass(TeamEntity.class);
            verify(teamRepository).save(captor.capture());

            assertEquals("Updated Team Name", captor.getValue().getName());
        }

        @Test
        @DisplayName("should skip inactive teams")
        void shouldSkipInactiveTeams() {
            AtlassianTeam activeTeam = createAtlassianTeam("team-1", "Active Team", "ACTIVE");
            AtlassianTeam inactiveTeam = createAtlassianTeam("team-2", "Inactive Team", "INACTIVE");
            TeamsResponse response = createTeamsResponse(List.of(activeTeam, inactiveTeam));

            when(teamsClient.getTeams()).thenReturn(response);
            when(teamsClient.getTeamMembers("team-1")).thenReturn(createMembersResponse(Collections.emptyList()));
            when(teamRepository.findByAtlassianTeamId("team-1")).thenReturn(Optional.empty());
            when(teamRepository.save(any(TeamEntity.class))).thenAnswer(i -> i.getArgument(0));

            teamSyncService.syncTeams();

            // Only active team should be saved
            verify(teamRepository, times(1)).save(any(TeamEntity.class));
        }

        @Test
        @DisplayName("should deactivate teams not in Atlassian")
        void shouldDeactivateTeamsNotInAtlassian() {
            TeamsResponse response = createTeamsResponse(Collections.emptyList());

            TeamEntity orphanTeam = new TeamEntity();
            orphanTeam.setId(1L);
            orphanTeam.setAtlassianTeamId("deleted-team");
            orphanTeam.setName("Orphan Team");
            orphanTeam.setActive(true);

            when(teamsClient.getTeams()).thenReturn(response);
            when(teamRepository.findByActiveTrue()).thenReturn(List.of(orphanTeam));

            teamSyncService.syncTeams();

            ArgumentCaptor<TeamEntity> captor = ArgumentCaptor.forClass(TeamEntity.class);
            verify(teamRepository).save(captor.capture());

            assertFalse(captor.getValue().getActive());
        }

        @Test
        @DisplayName("should handle empty response")
        void shouldHandleEmptyResponse() {
            TeamsResponse response = createTeamsResponse(Collections.emptyList());
            when(teamsClient.getTeams()).thenReturn(response);

            TeamSyncService.TeamSyncStatus status = teamSyncService.syncTeams();

            assertFalse(status.syncInProgress());
            assertNotNull(status.lastSyncTime());
            assertNull(status.error());
        }

        @Test
        @DisplayName("should handle API error")
        void shouldHandleApiError() {
            when(teamsClient.getTeams()).thenThrow(new RuntimeException("API error"));

            TeamSyncService.TeamSyncStatus status = teamSyncService.syncTeams();

            assertFalse(status.syncInProgress());
            assertNotNull(status.error());
            assertTrue(status.error().contains("API error"));
        }

        @Test
        @DisplayName("should handle missing organization ID")
        void shouldHandleMissingOrgId() {
            when(jiraProperties.getOrganizationId()).thenReturn(null);

            TeamSyncService.TeamSyncStatus status = teamSyncService.syncTeams();

            assertNotNull(status.error());
            assertTrue(status.error().contains("Organization ID"));
        }

        @Test
        @DisplayName("should not delete teams with issues")
        void shouldNotDeleteTeamsWithNullAtlassianId() {
            TeamsResponse response = createTeamsResponse(Collections.emptyList());

            // Team without atlassianTeamId - should NOT be deactivated
            TeamEntity localTeam = new TeamEntity();
            localTeam.setId(1L);
            localTeam.setAtlassianTeamId(null);
            localTeam.setName("Local Team");
            localTeam.setActive(true);

            when(teamsClient.getTeams()).thenReturn(response);
            when(teamRepository.findByActiveTrue()).thenReturn(List.of(localTeam));

            teamSyncService.syncTeams();

            // Should not save (deactivate) local team without atlassianTeamId
            verify(teamRepository, never()).save(any(TeamEntity.class));
        }
    }

    // ==================== Team Member Sync Tests ====================

    @Nested
    @DisplayName("syncTeams() - member sync")
    class TeamMemberSyncTests {

        @Test
        @DisplayName("should create new members")
        void shouldCreateNewMembers() {
            AtlassianTeam team = createAtlassianTeam("team-1", "Test Team", "ACTIVE");
            TeamsResponse teamsResponse = createTeamsResponse(List.of(team));

            TeamMember member = createTeamMember("account-123");
            TeamMembersResponse membersResponse = createMembersResponse(List.of(member));

            TeamEntity savedTeam = new TeamEntity();
            savedTeam.setId(1L);
            savedTeam.setAtlassianTeamId("team-1");
            savedTeam.setName("Test Team");

            AtlassianUser user = createAtlassianUser("account-123", "John Doe");

            when(teamsClient.getTeams()).thenReturn(teamsResponse);
            when(teamsClient.getTeamMembers("team-1")).thenReturn(membersResponse);
            when(teamsClient.getUser("account-123")).thenReturn(user);
            when(teamRepository.findByAtlassianTeamId("team-1")).thenReturn(Optional.empty());
            when(teamRepository.save(any(TeamEntity.class))).thenReturn(savedTeam);
            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(Collections.emptyList());
            when(memberRepository.findFirstByJiraAccountIdAndActiveTrue("account-123")).thenReturn(Optional.empty());

            teamSyncService.syncTeams();

            ArgumentCaptor<TeamMemberEntity> captor = ArgumentCaptor.forClass(TeamMemberEntity.class);
            verify(memberRepository).save(captor.capture());

            assertEquals("John Doe", captor.getValue().getDisplayName());
            assertEquals("account-123", captor.getValue().getJiraAccountId());
            assertEquals("DEV", captor.getValue().getRole()); // Default
            assertEquals(Grade.MIDDLE, captor.getValue().getGrade()); // Default
        }

        @Test
        @DisplayName("should deactivate members not in Atlassian team")
        void shouldDeactivateMembersNotInAtlassian() {
            AtlassianTeam team = createAtlassianTeam("team-1", "Test Team", "ACTIVE");
            TeamsResponse teamsResponse = createTeamsResponse(List.of(team));
            TeamMembersResponse membersResponse = createMembersResponse(Collections.emptyList());

            TeamEntity savedTeam = new TeamEntity();
            savedTeam.setId(1L);
            savedTeam.setAtlassianTeamId("team-1");

            TeamMemberEntity orphanMember = new TeamMemberEntity();
            orphanMember.setJiraAccountId("removed-member");
            orphanMember.setDisplayName("Removed Member");
            orphanMember.setActive(true);

            when(teamsClient.getTeams()).thenReturn(teamsResponse);
            when(teamsClient.getTeamMembers("team-1")).thenReturn(membersResponse);
            when(teamRepository.findByAtlassianTeamId("team-1")).thenReturn(Optional.of(savedTeam));
            when(teamRepository.save(any(TeamEntity.class))).thenReturn(savedTeam);
            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(orphanMember));

            teamSyncService.syncTeams();

            ArgumentCaptor<TeamMemberEntity> captor = ArgumentCaptor.forClass(TeamMemberEntity.class);
            verify(memberRepository).save(captor.capture());

            assertFalse(captor.getValue().getActive());
        }
    }

    // ==================== Helper Methods ====================

    private AtlassianTeam createAtlassianTeam(String teamId, String displayName, String state) {
        AtlassianTeam team = new AtlassianTeam();
        team.setTeamId(teamId);
        team.setDisplayName(displayName);
        team.setState(state);
        return team;
    }

    private TeamsResponse createTeamsResponse(List<AtlassianTeam> teams) {
        TeamsResponse response = new TeamsResponse();
        response.setEntities(teams);
        return response;
    }

    private TeamMember createTeamMember(String accountId) {
        TeamMember member = new TeamMember();
        member.setAccountId(accountId);
        return member;
    }

    private TeamMembersResponse createMembersResponse(List<TeamMember> members) {
        TeamMembersResponse response = new TeamMembersResponse();
        response.setResults(members);
        return response;
    }

    private AtlassianUser createAtlassianUser(String accountId, String displayName) {
        AtlassianUser user = new AtlassianUser();
        user.setAccountId(accountId);
        user.setDisplayName(displayName);
        return user;
    }
}
