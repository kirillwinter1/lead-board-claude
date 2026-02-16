package com.leadboard.competency;

import com.leadboard.competency.dto.*;
import com.leadboard.config.JiraProperties;
import com.leadboard.jira.JiraClient;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetencyServiceTest {

    @Mock private MemberCompetencyRepository competencyRepository;
    @Mock private TeamMemberRepository memberRepository;
    @Mock private JiraClient jiraClient;
    @Mock private JiraProperties jiraProperties;
    @Mock private JiraIssueRepository issueRepository;

    private CompetencyService service;

    @BeforeEach
    void setUp() {
        service = new CompetencyService(
                competencyRepository, memberRepository, jiraClient, jiraProperties, issueRepository);
    }

    @Test
    void getMemberCompetencies_returnsMapped() {
        MemberCompetencyEntity e1 = new MemberCompetencyEntity();
        e1.setComponentName("Frontend");
        e1.setLevel(4);

        MemberCompetencyEntity e2 = new MemberCompetencyEntity();
        e2.setComponentName("Backend");
        e2.setLevel(2);

        when(competencyRepository.findByTeamMemberId(1L)).thenReturn(List.of(e1, e2));

        List<CompetencyLevelDto> result = service.getMemberCompetencies(1L);
        assertEquals(2, result.size());
        assertEquals("Frontend", result.get(0).componentName());
        assertEquals(4, result.get(0).level());
    }

    @Test
    void updateMemberCompetencies_createsNew() {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(competencyRepository.findByTeamMemberIdAndComponentName(1L, "Frontend"))
                .thenReturn(Optional.empty());
        when(competencyRepository.findByTeamMemberId(1L)).thenReturn(List.of());

        service.updateMemberCompetencies(1L, List.of(new CompetencyLevelDto("Frontend", 5)));

        verify(competencyRepository).save(argThat(e ->
                e.getComponentName().equals("Frontend") && e.getLevel() == 5));
    }

    @Test
    void updateMemberCompetencies_updatesExisting() {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(1L);
        MemberCompetencyEntity existing = new MemberCompetencyEntity();
        existing.setTeamMember(member);
        existing.setComponentName("Frontend");
        existing.setLevel(3);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(competencyRepository.findByTeamMemberIdAndComponentName(1L, "Frontend"))
                .thenReturn(Optional.of(existing));
        when(competencyRepository.findByTeamMemberId(1L)).thenReturn(List.of());

        service.updateMemberCompetencies(1L, List.of(new CompetencyLevelDto("Frontend", 5)));

        assertEquals(5, existing.getLevel());
        verify(competencyRepository).save(existing);
    }

    @Test
    void updateMemberCompetencies_invalidLevel_throws() {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThrows(IllegalArgumentException.class, () ->
                service.updateMemberCompetencies(1L, List.of(new CompetencyLevelDto("X", 0))));

        assertThrows(IllegalArgumentException.class, () ->
                service.updateMemberCompetencies(1L, List.of(new CompetencyLevelDto("X", 6))));
    }

    @Test
    void updateMemberCompetencies_memberNotFound_throws() {
        when(memberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.updateMemberCompetencies(999L, List.of(new CompetencyLevelDto("X", 3))));
    }

    @Test
    void getBusFactor_criticalWhenNoExperts() {
        TeamMemberEntity m1 = new TeamMemberEntity();
        m1.setId(1L);
        m1.setDisplayName("Alice");

        MemberCompetencyEntity c1 = new MemberCompetencyEntity();
        c1.setTeamMember(m1);
        c1.setComponentName("Frontend");
        c1.setLevel(2); // beginner, not an expert

        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(m1));
        when(competencyRepository.findByTeamMemberIdIn(List.of(1L))).thenReturn(List.of(c1));
        when(issueRepository.findDistinctComponentsByTeamId(1L)).thenReturn(List.of());

        List<BusFactorAlertDto> alerts = service.getBusFactor(1L);
        assertEquals(1, alerts.size());
        assertEquals("CRITICAL", alerts.get(0).severity());
        assertEquals("Frontend", alerts.get(0).componentName());
        assertEquals(0, alerts.get(0).expertCount());
    }

    @Test
    void getBusFactor_warningWhenOneExpert() {
        TeamMemberEntity m1 = new TeamMemberEntity();
        m1.setId(1L);
        m1.setDisplayName("Alice");

        MemberCompetencyEntity c1 = new MemberCompetencyEntity();
        c1.setTeamMember(m1);
        c1.setComponentName("Frontend");
        c1.setLevel(4); // proficient = expert

        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(m1));
        when(competencyRepository.findByTeamMemberIdIn(List.of(1L))).thenReturn(List.of(c1));
        when(issueRepository.findDistinctComponentsByTeamId(1L)).thenReturn(List.of());

        List<BusFactorAlertDto> alerts = service.getBusFactor(1L);
        assertEquals(1, alerts.size());
        assertEquals("WARNING", alerts.get(0).severity());
        assertEquals(1, alerts.get(0).expertCount());
        assertEquals("Alice", alerts.get(0).experts().get(0));
    }

    @Test
    void getBusFactor_okWhenMultipleExperts() {
        TeamMemberEntity m1 = new TeamMemberEntity();
        m1.setId(1L);
        m1.setDisplayName("Alice");
        TeamMemberEntity m2 = new TeamMemberEntity();
        m2.setId(2L);
        m2.setDisplayName("Bob");

        MemberCompetencyEntity c1 = new MemberCompetencyEntity();
        c1.setTeamMember(m1);
        c1.setComponentName("Frontend");
        c1.setLevel(5);

        MemberCompetencyEntity c2 = new MemberCompetencyEntity();
        c2.setTeamMember(m2);
        c2.setComponentName("Frontend");
        c2.setLevel(4);

        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(m1, m2));
        when(competencyRepository.findByTeamMemberIdIn(List.of(1L, 2L))).thenReturn(List.of(c1, c2));
        when(issueRepository.findDistinctComponentsByTeamId(1L)).thenReturn(List.of());

        List<BusFactorAlertDto> alerts = service.getBusFactor(1L);
        assertEquals(1, alerts.size());
        assertEquals("OK", alerts.get(0).severity());
        assertEquals(2, alerts.get(0).expertCount());
    }

    @Test
    void getAvailableComponents_fromJira() {
        when(jiraProperties.getProjectKey()).thenReturn("PROJ");
        when(jiraClient.getProjectComponents("PROJ")).thenReturn(List.of("Frontend", "Backend"));

        List<String> result = service.getAvailableComponents();
        assertEquals(List.of("Frontend", "Backend"), result);
    }

    @Test
    void getAvailableComponents_fallbackToDb() {
        when(jiraProperties.getProjectKey()).thenReturn("PROJ");
        when(jiraClient.getProjectComponents("PROJ")).thenThrow(new RuntimeException("fail"));
        when(issueRepository.findDistinctComponents()).thenReturn(List.of("API", "Mobile"));

        List<String> result = service.getAvailableComponents();
        assertEquals(List.of("API", "Mobile"), result);
    }
}
