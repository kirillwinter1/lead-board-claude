package com.leadboard.team;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;

    public TeamService(TeamRepository teamRepository, TeamMemberRepository memberRepository) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
    }

    // ==================== Team Operations ====================

    @Transactional(readOnly = true)
    public List<TeamDto> getAllTeams() {
        return teamRepository.findByActiveTrue().stream()
                .map(TeamDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamDto getTeam(Long id) {
        TeamEntity team = teamRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + id));
        return TeamDto.from(team);
    }

    public TeamDto createTeam(CreateTeamRequest request) {
        if (teamRepository.existsByNameAndActiveTrue(request.name())) {
            throw new TeamAlreadyExistsException("Team with name already exists: " + request.name());
        }

        TeamEntity team = new TeamEntity();
        team.setName(request.name());
        team.setJiraTeamValue(request.jiraTeamValue());

        TeamEntity saved = teamRepository.save(team);
        return TeamDto.from(saved);
    }

    public TeamDto updateTeam(Long id, UpdateTeamRequest request) {
        TeamEntity team = teamRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + id));

        if (request.name() != null && !request.name().isBlank()) {
            // Check if another team has this name
            if (!team.getName().equals(request.name()) &&
                teamRepository.existsByNameAndActiveTrue(request.name())) {
                throw new TeamAlreadyExistsException("Team with name already exists: " + request.name());
            }
            team.setName(request.name());
        }

        if (request.jiraTeamValue() != null) {
            team.setJiraTeamValue(request.jiraTeamValue());
        }

        TeamEntity saved = teamRepository.save(team);
        return TeamDto.from(saved);
    }

    public void deactivateTeam(Long id) {
        TeamEntity team = teamRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + id));
        team.setActive(false);
        teamRepository.save(team);
    }

    // ==================== Team Member Operations ====================

    @Transactional(readOnly = true)
    public List<TeamMemberDto> getTeamMembers(Long teamId) {
        // Verify team exists
        if (teamRepository.findByIdAndActiveTrue(teamId).isEmpty()) {
            throw new TeamNotFoundException("Team not found: " + teamId);
        }

        return memberRepository.findByTeamIdAndActiveTrue(teamId).stream()
                .map(TeamMemberDto::from)
                .toList();
    }

    public TeamMemberDto addTeamMember(Long teamId, CreateTeamMemberRequest request) {
        TeamEntity team = teamRepository.findByIdAndActiveTrue(teamId)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + teamId));

        // Check for duplicate jiraAccountId in this team
        if (memberRepository.existsByTeamIdAndJiraAccountIdAndActiveTrue(teamId, request.jiraAccountId())) {
            throw new TeamMemberAlreadyExistsException(
                    "Member with Jira account ID already exists in team: " + request.jiraAccountId());
        }

        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeam(team);
        member.setJiraAccountId(request.jiraAccountId());
        member.setDisplayName(request.displayName());
        member.setRole(request.role() != null ? request.role() : Role.DEV);
        member.setGrade(request.grade() != null ? request.grade() : Grade.MIDDLE);
        if (request.hoursPerDay() != null) {
            member.setHoursPerDay(request.hoursPerDay());
        }

        TeamMemberEntity saved = memberRepository.save(member);
        return TeamMemberDto.from(saved);
    }

    public TeamMemberDto updateTeamMember(Long teamId, Long memberId, UpdateTeamMemberRequest request) {
        // Verify team exists
        if (teamRepository.findByIdAndActiveTrue(teamId).isEmpty()) {
            throw new TeamNotFoundException("Team not found: " + teamId);
        }

        TeamMemberEntity member = memberRepository.findByIdAndTeamIdAndActiveTrue(memberId, teamId)
                .orElseThrow(() -> new TeamMemberNotFoundException("Team member not found: " + memberId));

        if (request.displayName() != null) {
            member.setDisplayName(request.displayName());
        }
        if (request.role() != null) {
            member.setRole(request.role());
        }
        if (request.grade() != null) {
            member.setGrade(request.grade());
        }
        if (request.hoursPerDay() != null) {
            member.setHoursPerDay(request.hoursPerDay());
        }

        TeamMemberEntity saved = memberRepository.save(member);
        return TeamMemberDto.from(saved);
    }

    public void deactivateTeamMember(Long teamId, Long memberId) {
        // Verify team exists
        if (teamRepository.findByIdAndActiveTrue(teamId).isEmpty()) {
            throw new TeamNotFoundException("Team not found: " + teamId);
        }

        TeamMemberEntity member = memberRepository.findByIdAndTeamIdAndActiveTrue(memberId, teamId)
                .orElseThrow(() -> new TeamMemberNotFoundException("Team member not found: " + memberId));

        member.setActive(false);
        memberRepository.save(member);
    }

    // ==================== Exceptions ====================

    public static class TeamNotFoundException extends RuntimeException {
        public TeamNotFoundException(String message) {
            super(message);
        }
    }

    public static class TeamAlreadyExistsException extends RuntimeException {
        public TeamAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class TeamMemberNotFoundException extends RuntimeException {
        public TeamMemberNotFoundException(String message) {
            super(message);
        }
    }

    public static class TeamMemberAlreadyExistsException extends RuntimeException {
        public TeamMemberAlreadyExistsException(String message) {
            super(message);
        }
    }
}
