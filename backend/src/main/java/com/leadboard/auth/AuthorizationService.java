package com.leadboard.auth;

import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for authorization checks beyond simple role-based access.
 */
@Service
public class AuthorizationService {

    private final TeamMemberRepository teamMemberRepository;

    public AuthorizationService(TeamMemberRepository teamMemberRepository) {
        this.teamMemberRepository = teamMemberRepository;
    }

    /**
     * Get the current authenticated user.
     */
    public LeadBoardAuthentication getCurrentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof LeadBoardAuthentication) {
            return (LeadBoardAuthentication) auth;
        }
        return null;
    }

    /**
     * Check if the current user is authenticated.
     */
    public boolean isAuthenticated() {
        return getCurrentAuth() != null;
    }

    /**
     * Check if the current user has ADMIN role.
     */
    public boolean isAdmin() {
        LeadBoardAuthentication auth = getCurrentAuth();
        return auth != null && auth.getRole() == AppRole.ADMIN;
    }

    /**
     * Check if the current user is a Team Lead.
     */
    public boolean isTeamLead() {
        LeadBoardAuthentication auth = getCurrentAuth();
        return auth != null && auth.getRole() == AppRole.TEAM_LEAD;
    }

    /**
     * Check if the current user can manage the given team.
     * - ADMIN can manage any team
     * - TEAM_LEAD can manage teams they belong to
     */
    public boolean canManageTeam(Long teamId) {
        LeadBoardAuthentication auth = getCurrentAuth();
        if (auth == null) {
            return false;
        }

        if (auth.getRole() == AppRole.ADMIN) {
            return true;
        }

        if (auth.getRole() == AppRole.TEAM_LEAD) {
            return isUserInTeam(auth.getAtlassianAccountId(), teamId);
        }

        return false;
    }

    /**
     * Check if the current user can edit priorities.
     */
    public boolean canEditPriorities() {
        LeadBoardAuthentication auth = getCurrentAuth();
        if (auth == null) {
            return false;
        }
        return auth.getRole() == AppRole.ADMIN || auth.getRole() == AppRole.TEAM_LEAD;
    }

    /**
     * Check if the current user can participate in Poker.
     */
    public boolean canParticipateInPoker() {
        LeadBoardAuthentication auth = getCurrentAuth();
        if (auth == null) {
            return false;
        }
        return auth.getRole() != AppRole.VIEWER;
    }

    /**
     * Get the set of team IDs the current user belongs to.
     */
    public Set<Long> getUserTeamIds() {
        LeadBoardAuthentication auth = getCurrentAuth();
        if (auth == null) {
            return Set.of();
        }

        List<TeamMemberEntity> memberships = teamMemberRepository.findAllByJiraAccountIdAndActiveTrue(
                auth.getAtlassianAccountId()
        );

        return memberships.stream()
                .map(m -> m.getTeam().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Check if a user is a member of the given team.
     */
    private boolean isUserInTeam(String atlassianAccountId, Long teamId) {
        List<TeamMemberEntity> memberships = teamMemberRepository.findAllByJiraAccountIdAndActiveTrue(atlassianAccountId);
        return memberships.stream()
                .anyMatch(m -> m.getTeam().getId().equals(teamId));
    }
}
