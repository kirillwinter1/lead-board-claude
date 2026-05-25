package com.leadboard.auth;

import com.leadboard.team.TeamMemberRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;

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
     * Check if the current user has PROJECT_MANAGER role.
     */
    public boolean isProjectManager() {
        LeadBoardAuthentication auth = getCurrentAuth();
        return auth != null && auth.getRole() == AppRole.PROJECT_MANAGER;
    }

    /**
     * Check if the current user can manage projects (ADMIN or PROJECT_MANAGER).
     */
    public boolean canManageProjects() {
        LeadBoardAuthentication auth = getCurrentAuth();
        if (auth == null) {
            return false;
        }
        return auth.getRole() == AppRole.ADMIN || auth.getRole() == AppRole.PROJECT_MANAGER;
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
     * Get the set of team IDs the current user belongs to. Uses a JPQL
     * projection so we don't fan-out into N LAZY {@code team} proxy SELECTs.
     */
    public Set<Long> getUserTeamIds() {
        LeadBoardAuthentication auth = getCurrentAuth();
        if (auth == null) {
            return Set.of();
        }
        return teamMemberRepository.findTeamIdsByJiraAccountIdAndActiveTrue(auth.getAtlassianAccountId());
    }

    /**
     * Check if a user is a member of the given team. Uses a direct existence
     * check rather than loading memberships and walking the LAZY {@code team}
     * proxy (which caused an N+1 SELECT pattern on every reorder/sync call).
     */
    private boolean isUserInTeam(String atlassianAccountId, Long teamId) {
        return teamMemberRepository.existsByJiraAccountIdAndTeamIdAndActiveTrue(atlassianAccountId, teamId);
    }
}
