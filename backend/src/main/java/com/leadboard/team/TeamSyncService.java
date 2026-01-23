package com.leadboard.team;

import com.leadboard.config.JiraProperties;
import com.leadboard.jira.AtlassianTeamsClient;
import com.leadboard.jira.AtlassianTeamsClient.AtlassianTeam;
import com.leadboard.jira.AtlassianTeamsClient.AtlassianUser;
import com.leadboard.jira.AtlassianTeamsClient.TeamMember;
import com.leadboard.jira.AtlassianTeamsClient.TeamMembersResponse;
import com.leadboard.jira.AtlassianTeamsClient.TeamsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TeamSyncService {

    private static final Logger log = LoggerFactory.getLogger(TeamSyncService.class);

    private final AtlassianTeamsClient teamsClient;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final JiraProperties jiraProperties;

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private volatile String lastSyncError = null;
    private volatile java.time.OffsetDateTime lastSyncTime = null;

    public TeamSyncService(
            AtlassianTeamsClient teamsClient,
            TeamRepository teamRepository,
            TeamMemberRepository memberRepository,
            JiraProperties jiraProperties) {
        this.teamsClient = teamsClient;
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.jiraProperties = jiraProperties;
    }

    public TeamSyncStatus getStatus() {
        return new TeamSyncStatus(
                syncInProgress.get(),
                lastSyncTime,
                lastSyncError
        );
    }

    @Transactional
    public TeamSyncStatus syncTeams() {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.warn("Team sync already in progress");
            return getStatus();
        }

        lastSyncError = null;
        log.info("Starting team sync from Atlassian");

        try {
            String orgId = jiraProperties.getOrganizationId();
            if (orgId == null || orgId.isEmpty()) {
                throw new IllegalStateException("Organization ID is not configured. Set JIRA_ORGANIZATION_ID in .env");
            }

            // Fetch teams from Atlassian
            TeamsResponse teamsResponse = teamsClient.getTeams();
            List<AtlassianTeam> atlassianTeams = teamsResponse.getEntities();

            log.info("Found {} teams in Atlassian", atlassianTeams.size());

            Set<String> syncedTeamIds = new HashSet<>();

            for (AtlassianTeam atlassianTeam : atlassianTeams) {
                if (!"ACTIVE".equals(atlassianTeam.getState())) {
                    log.debug("Skipping inactive team: {}", atlassianTeam.getDisplayName());
                    continue;
                }

                syncedTeamIds.add(atlassianTeam.getTeamId());
                TeamEntity team = syncTeam(atlassianTeam);
                syncTeamMembers(team, atlassianTeam.getTeamId());
            }

            // Deactivate teams that no longer exist in Atlassian
            List<TeamEntity> allTeams = teamRepository.findByActiveTrue();
            for (TeamEntity team : allTeams) {
                if (team.getAtlassianTeamId() != null && !syncedTeamIds.contains(team.getAtlassianTeamId())) {
                    log.info("Deactivating team not found in Atlassian: {}", team.getName());
                    team.setActive(false);
                    teamRepository.save(team);
                }
            }

            lastSyncTime = java.time.OffsetDateTime.now();
            log.info("Team sync completed successfully");

        } catch (Exception e) {
            lastSyncError = e.getMessage();
            log.error("Team sync failed: {}", e.getMessage(), e);
        } finally {
            syncInProgress.set(false);
        }

        return getStatus();
    }

    private TeamEntity syncTeam(AtlassianTeam atlassianTeam) {
        Optional<TeamEntity> existing = teamRepository.findByAtlassianTeamId(atlassianTeam.getTeamId());

        TeamEntity team;
        if (existing.isPresent()) {
            team = existing.get();
            log.debug("Updating existing team: {}", team.getName());
        } else {
            team = new TeamEntity();
            team.setAtlassianTeamId(atlassianTeam.getTeamId());
            log.info("Creating new team: {}", atlassianTeam.getDisplayName());
        }

        team.setName(atlassianTeam.getDisplayName());
        team.setJiraTeamValue(atlassianTeam.getDisplayName()); // Use display name as jira team value
        team.setActive(true);

        return teamRepository.save(team);
    }

    private void syncTeamMembers(TeamEntity team, String atlassianTeamId) {
        try {
            TeamMembersResponse membersResponse = teamsClient.getTeamMembers(atlassianTeamId);
            List<TeamMember> atlassianMembers = membersResponse.getResults();

            log.debug("Found {} members in team {}", atlassianMembers.size(), team.getName());

            Set<String> syncedAccountIds = new HashSet<>();

            for (TeamMember atlassianMember : atlassianMembers) {
                syncedAccountIds.add(atlassianMember.getAccountId());
                syncTeamMember(team, atlassianMember.getAccountId());
            }

            // Deactivate members no longer in Atlassian team
            List<TeamMemberEntity> currentMembers = memberRepository.findByTeamIdAndActiveTrue(team.getId());
            for (TeamMemberEntity member : currentMembers) {
                if (!syncedAccountIds.contains(member.getJiraAccountId())) {
                    log.info("Deactivating member {} from team {}", member.getDisplayName(), team.getName());
                    member.setActive(false);
                    memberRepository.save(member);
                }
            }

        } catch (Exception e) {
            log.error("Failed to sync members for team {}: {}", team.getName(), e.getMessage());
        }
    }

    private void syncTeamMember(TeamEntity team, String accountId) {
        // Check if member already exists in this team
        Optional<TeamMemberEntity> existingInTeam = memberRepository.findByTeamIdAndActiveTrue(team.getId())
                .stream()
                .filter(m -> accountId.equals(m.getJiraAccountId()))
                .findFirst();

        if (existingInTeam.isPresent()) {
            // Member exists - don't overwrite local fields (role, grade, hoursPerDay)
            TeamMemberEntity member = existingInTeam.get();
            // Just update display name if needed
            try {
                AtlassianUser user = teamsClient.getUser(accountId);
                if (user != null && user.getDisplayName() != null) {
                    member.setDisplayName(user.getDisplayName());
                    memberRepository.save(member);
                }
            } catch (Exception e) {
                log.debug("Could not fetch user info for {}: {}", accountId, e.getMessage());
            }
            return;
        }

        // Check if member exists in another team (by accountId)
        Optional<TeamMemberEntity> existingElsewhere = memberRepository.findByJiraAccountIdAndActiveTrue(accountId);

        // Create new member with defaults
        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeam(team);
        member.setJiraAccountId(accountId);
        member.setRole(Role.DEV); // Default role
        member.setGrade(Grade.MIDDLE); // Default grade
        member.setHoursPerDay(new BigDecimal("6.0")); // Default hours
        member.setActive(true);

        // Try to get display name from Atlassian
        try {
            AtlassianUser user = teamsClient.getUser(accountId);
            if (user != null) {
                member.setDisplayName(user.getDisplayName());
            }
        } catch (Exception e) {
            log.debug("Could not fetch user info for {}: {}", accountId, e.getMessage());
            member.setDisplayName(accountId); // Use accountId as fallback
        }

        log.info("Adding member {} to team {}", member.getDisplayName(), team.getName());
        memberRepository.save(member);
    }

    public record TeamSyncStatus(
            boolean syncInProgress,
            java.time.OffsetDateTime lastSyncTime,
            String error
    ) {}
}
