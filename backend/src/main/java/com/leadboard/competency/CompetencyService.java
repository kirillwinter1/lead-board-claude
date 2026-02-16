package com.leadboard.competency;

import com.leadboard.competency.dto.*;
import com.leadboard.config.JiraProperties;
import com.leadboard.jira.JiraClient;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompetencyService {

    private static final Logger log = LoggerFactory.getLogger(CompetencyService.class);

    private final MemberCompetencyRepository competencyRepository;
    private final TeamMemberRepository memberRepository;
    private final JiraClient jiraClient;
    private final JiraProperties jiraProperties;
    private final JiraIssueRepository issueRepository;

    public CompetencyService(MemberCompetencyRepository competencyRepository,
                             TeamMemberRepository memberRepository,
                             JiraClient jiraClient,
                             JiraProperties jiraProperties,
                             JiraIssueRepository issueRepository) {
        this.competencyRepository = competencyRepository;
        this.memberRepository = memberRepository;
        this.jiraClient = jiraClient;
        this.jiraProperties = jiraProperties;
        this.issueRepository = issueRepository;
    }

    public List<CompetencyLevelDto> getMemberCompetencies(Long memberId) {
        return competencyRepository.findByTeamMemberId(memberId).stream()
                .map(e -> new CompetencyLevelDto(e.getComponentName(), e.getLevel()))
                .toList();
    }

    @Transactional
    public List<CompetencyLevelDto> updateMemberCompetencies(Long memberId, List<CompetencyLevelDto> competencies) {
        TeamMemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        for (CompetencyLevelDto dto : competencies) {
            if (dto.level() < 1 || dto.level() > 5) {
                throw new IllegalArgumentException("Level must be 1-5, got: " + dto.level());
            }

            Optional<MemberCompetencyEntity> existing =
                    competencyRepository.findByTeamMemberIdAndComponentName(memberId, dto.componentName());

            if (existing.isPresent()) {
                existing.get().setLevel(dto.level());
                competencyRepository.save(existing.get());
            } else {
                MemberCompetencyEntity entity = new MemberCompetencyEntity();
                entity.setTeamMember(member);
                entity.setComponentName(dto.componentName());
                entity.setLevel(dto.level());
                competencyRepository.save(entity);
            }
        }

        return getMemberCompetencies(memberId);
    }

    public TeamCompetencyMatrixDto getTeamMatrix(Long teamId) {
        List<TeamMemberEntity> members = memberRepository.findByTeamIdAndActiveTrue(teamId);
        List<Long> memberIds = members.stream().map(TeamMemberEntity::getId).toList();

        // Get all competencies for team members
        List<MemberCompetencyEntity> allCompetencies = memberIds.isEmpty()
                ? List.of()
                : competencyRepository.findByTeamMemberIdIn(memberIds);

        // Collect all unique component names
        Set<String> componentSet = new TreeSet<>(allCompetencies.stream()
                .map(MemberCompetencyEntity::getComponentName)
                .toList());

        // Also include components from Jira issues for this team
        List<String> issueComponents = issueRepository.findDistinctComponentsByTeamId(teamId);
        componentSet.addAll(issueComponents);

        List<String> components = new ArrayList<>(componentSet);

        // Group competencies by member
        Map<Long, List<MemberCompetencyEntity>> byMember = allCompetencies.stream()
                .collect(Collectors.groupingBy(c -> c.getTeamMember().getId()));

        List<MemberCompetencyDto> memberDtos = members.stream()
                .map(m -> new MemberCompetencyDto(
                        m.getId(),
                        m.getDisplayName(),
                        byMember.getOrDefault(m.getId(), List.of()).stream()
                                .map(c -> new CompetencyLevelDto(c.getComponentName(), c.getLevel()))
                                .toList()
                ))
                .toList();

        return new TeamCompetencyMatrixDto(components, memberDtos);
    }

    public List<BusFactorAlertDto> getBusFactor(Long teamId) {
        TeamCompetencyMatrixDto matrix = getTeamMatrix(teamId);
        List<BusFactorAlertDto> alerts = new ArrayList<>();

        for (String component : matrix.components()) {
            List<String> experts = new ArrayList<>();
            for (MemberCompetencyDto member : matrix.members()) {
                for (CompetencyLevelDto comp : member.competencies()) {
                    if (comp.componentName().equals(component) && comp.level() >= 4) {
                        experts.add(member.displayName());
                    }
                }
            }

            String severity;
            if (experts.isEmpty()) {
                severity = "CRITICAL";
            } else if (experts.size() == 1) {
                severity = "WARNING";
            } else {
                severity = "OK";
            }

            alerts.add(new BusFactorAlertDto(component, severity, experts.size(), experts));
        }

        // Sort: CRITICAL first, then WARNING, then OK
        alerts.sort(Comparator.comparingInt(a -> switch (a.severity()) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        }));

        return alerts;
    }

    public List<String> getAvailableComponents() {
        try {
            String projectKey = jiraProperties.getProjectKey();
            if (projectKey != null && !projectKey.isEmpty()) {
                return jiraClient.getProjectComponents(projectKey);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch components from Jira, falling back to DB", e);
        }

        // Fallback: components from synced issues
        return issueRepository.findDistinctComponents();
    }
}
