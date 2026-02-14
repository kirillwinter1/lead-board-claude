package com.leadboard.team;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.team.dto.PlanningConfigDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final com.leadboard.sync.JiraIssueRepository issueRepository;

    public TeamService(TeamRepository teamRepository, TeamMemberRepository memberRepository,
                       ObjectMapper objectMapper, com.leadboard.sync.JiraIssueRepository issueRepository) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
        this.issueRepository = issueRepository;
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
        linkIssuesToTeam(saved);
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
        linkIssuesToTeam(saved);
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
        member.setRole(request.role() != null ? request.role() : "DEV");
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

    // ==================== Planning Config Operations ====================

    @Transactional(readOnly = true)
    public PlanningConfigDto getPlanningConfig(Long teamId) {
        TeamEntity team = teamRepository.findByIdAndActiveTrue(teamId)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + teamId));

        String configJson = team.getPlanningConfig();
        if (configJson == null || configJson.isBlank()) {
            return PlanningConfigDto.defaults();
        }

        try {
            return objectMapper.readValue(configJson, PlanningConfigDto.class);
        } catch (JsonProcessingException e) {
            // If parsing fails, return defaults
            return PlanningConfigDto.defaults();
        }
    }

    public PlanningConfigDto updatePlanningConfig(Long teamId, PlanningConfigDto config) {
        TeamEntity team = teamRepository.findByIdAndActiveTrue(teamId)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + teamId));

        // Validate config
        validatePlanningConfig(config);

        try {
            String configJson = objectMapper.writeValueAsString(config);
            team.setPlanningConfig(configJson);
            teamRepository.save(team);
            return config;
        } catch (JsonProcessingException e) {
            throw new InvalidPlanningConfigException("Failed to serialize planning config: " + e.getMessage());
        }
    }

    private void validatePlanningConfig(PlanningConfigDto config) {
        if (config.gradeCoefficients() != null) {
            var gc = config.gradeCoefficients();
            if (gc.senior() != null && gc.senior().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidPlanningConfigException("Senior grade coefficient must be positive");
            }
            if (gc.middle() != null && gc.middle().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidPlanningConfigException("Middle grade coefficient must be positive");
            }
            if (gc.junior() != null && gc.junior().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidPlanningConfigException("Junior grade coefficient must be positive");
            }
        }

        if (config.riskBuffer() != null && config.riskBuffer().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidPlanningConfigException("Risk buffer cannot be negative");
        }

        if (config.wipLimits() != null) {
            var wip = config.wipLimits();
            if (wip.team() != null && wip.team() < 1) {
                throw new InvalidPlanningConfigException("Team WIP limit must be at least 1");
            }
            if (wip.roleLimits() != null) {
                for (var entry : wip.roleLimits().entrySet()) {
                    if (entry.getValue() != null && entry.getValue() < 1) {
                        throw new InvalidPlanningConfigException(
                                entry.getKey() + " WIP limit must be at least 1");
                    }
                }
            }
        }
    }

    private void linkIssuesToTeam(TeamEntity team) {
        if (team.getJiraTeamValue() != null && !team.getJiraTeamValue().isEmpty()) {
            int linked = issueRepository.linkIssuesToTeam(team.getId(), team.getJiraTeamValue());
            if (linked > 0) {
                // Cascade: children inherit team from their parent
                int inherited = issueRepository.inheritTeamFromParent();
                // Second pass for subtasks (subtask → story → epic)
                if (inherited > 0) {
                    issueRepository.inheritTeamFromParent();
                }
            }
        }
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

    public static class InvalidPlanningConfigException extends RuntimeException {
        public InvalidPlanningConfigException(String message) {
            super(message);
        }
    }
}
