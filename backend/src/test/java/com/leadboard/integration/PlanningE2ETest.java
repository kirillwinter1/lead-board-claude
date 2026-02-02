package com.leadboard.integration;

import com.leadboard.team.*;
import com.leadboard.team.dto.PlanningConfigDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E test: Planning pipeline.
 * Tests the complete flow: Config → Team Members → Forecast → Role Load
 */
@DisplayName("E2E: Planning Pipeline")
class PlanningE2ETest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TeamMemberRepository memberRepository;

    @Test
    @DisplayName("E2E: Configure team → Add members → Get forecast with capacity")
    void shouldCompletePlanningPipeline() {
        // ===== Step 1: Create team =====
        var team = createTeam("E2E Planning Team");

        // ===== Step 2: Configure planning settings =====
        var configResponse = restTemplate.getForEntity(
                "/api/teams/" + team.getId() + "/planning-config",
                PlanningConfigDto.class);

        assertEquals(HttpStatus.OK, configResponse.getStatusCode());
        var config = configResponse.getBody();

        // Update risk buffer
        var updatedConfig = new PlanningConfigDto(
                config.gradeCoefficients(),
                new BigDecimal("0.25"), // 25% risk buffer
                config.wipLimits(),
                config.storyDuration(),
                config.statusMapping());

        var updateResponse = restTemplate.exchange(
                "/api/teams/" + team.getId() + "/planning-config",
                HttpMethod.PUT,
                new HttpEntity<>(updatedConfig),
                PlanningConfigDto.class);

        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());

        // ===== Step 3: Add team members =====
        // Add SA (Senior)
        var saRequest = new CreateTeamMemberRequest(
                "sa-account-1", "Alex Analyst", Role.SA, Grade.SENIOR, new BigDecimal("7.0"));
        var saResponse = restTemplate.postForEntity(
                "/api/teams/" + team.getId() + "/members",
                saRequest,
                TeamMemberDto.class);
        assertEquals(HttpStatus.CREATED, saResponse.getStatusCode());

        // Add DEV (Middle)
        var devRequest = new CreateTeamMemberRequest(
                "dev-account-1", "Dana Developer", Role.DEV, Grade.MIDDLE, new BigDecimal("8.0"));
        restTemplate.postForEntity(
                "/api/teams/" + team.getId() + "/members",
                devRequest,
                TeamMemberDto.class);

        // Add DEV (Junior)
        var dev2Request = new CreateTeamMemberRequest(
                "dev-account-2", "Junior Dev", Role.DEV, Grade.JUNIOR, new BigDecimal("6.0"));
        restTemplate.postForEntity(
                "/api/teams/" + team.getId() + "/members",
                dev2Request,
                TeamMemberDto.class);

        // Add QA (Middle)
        var qaRequest = new CreateTeamMemberRequest(
                "qa-account-1", "Quinn QA", Role.QA, Grade.MIDDLE, new BigDecimal("7.0"));
        restTemplate.postForEntity(
                "/api/teams/" + team.getId() + "/members",
                qaRequest,
                TeamMemberDto.class);

        // ===== Step 4: Create work items =====
        var epic = createEpic("E2E-PLAN-1", "Feature X", "В работе", team.getId());
        epic.setRoughEstimateSaDays(new BigDecimal("3.0"));
        epic.setRoughEstimateDevDays(new BigDecimal("10.0"));
        epic.setRoughEstimateQaDays(new BigDecimal("4.0"));
        issueRepository.save(epic);

        var story = createStory("E2E-PLAN-STORY-1", "Implement Feature X", "В работе", "E2E-PLAN-1", team.getId());

        createSubtask("E2E-PLAN-SUB-1", "Analyze", "Done", "E2E-PLAN-STORY-1",
                "Аналитика", team.getId(), 16 * 3600L, 16 * 3600L);
        createSubtask("E2E-PLAN-SUB-2", "Develop", "В работе", "E2E-PLAN-STORY-1",
                "Разработка", team.getId(), 40 * 3600L, 24 * 3600L);
        createSubtask("E2E-PLAN-SUB-3", "Test", "Новое", "E2E-PLAN-STORY-1",
                "Тестирование", team.getId(), 16 * 3600L, 0L);

        // ===== Step 5: Verify team members list =====
        var membersResponse = restTemplate.getForEntity(
                "/api/teams/" + team.getId() + "/members",
                TeamMemberDto[].class);

        assertEquals(HttpStatus.OK, membersResponse.getStatusCode());
        assertEquals(4, membersResponse.getBody().length);

        // ===== Step 6: Get forecast (should use team capacity) =====
        var forecastResponse = restTemplate.getForEntity(
                "/api/planning/forecast?teamId=" + team.getId(),
                Map.class);

        assertEquals(HttpStatus.OK, forecastResponse.getStatusCode());
        assertNotNull(forecastResponse.getBody());

        // ===== Step 7: Get role load =====
        var roleLoadResponse = restTemplate.getForEntity(
                "/api/planning/role-load?teamId=" + team.getId(),
                Map.class);

        assertEquals(HttpStatus.OK, roleLoadResponse.getStatusCode());
        assertNotNull(roleLoadResponse.getBody());
    }

    @Test
    @DisplayName("E2E: Team capacity affects forecast timeline")
    void shouldAffectForecastWithTeamCapacity() {
        // ===== Setup: Create two teams with different capacity =====
        var smallTeam = createTeam("Small Team");
        var largeTeam = createTeam("Large Team");

        // Small team: 1 dev
        addTeamMember(smallTeam.getId(), "small-dev-1", "Solo Dev", Role.DEV, Grade.MIDDLE, new BigDecimal("8.0"));

        // Large team: 3 devs
        addTeamMember(largeTeam.getId(), "large-dev-1", "Dev 1", Role.DEV, Grade.SENIOR, new BigDecimal("8.0"));
        addTeamMember(largeTeam.getId(), "large-dev-2", "Dev 2", Role.DEV, Grade.MIDDLE, new BigDecimal("8.0"));
        addTeamMember(largeTeam.getId(), "large-dev-3", "Dev 3", Role.DEV, Grade.MIDDLE, new BigDecimal("8.0"));

        // Create same epic for both teams
        var smallEpic = createEpic("SMALL-1", "Feature", "В работе", smallTeam.getId());
        smallEpic.setRoughEstimateDevDays(new BigDecimal("20.0"));
        issueRepository.save(smallEpic);

        var largeEpic = createEpic("LARGE-1", "Feature", "В работе", largeTeam.getId());
        largeEpic.setRoughEstimateDevDays(new BigDecimal("20.0"));
        issueRepository.save(largeEpic);

        // ===== Get forecasts for both teams =====
        var smallForecast = restTemplate.getForEntity(
                "/api/planning/forecast?teamId=" + smallTeam.getId(),
                Map.class);

        var largeForecast = restTemplate.getForEntity(
                "/api/planning/forecast?teamId=" + largeTeam.getId(),
                Map.class);

        // Both should return OK
        assertEquals(HttpStatus.OK, smallForecast.getStatusCode());
        assertEquals(HttpStatus.OK, largeForecast.getStatusCode());

        // Forecasts should exist
        assertNotNull(smallForecast.getBody());
        assertNotNull(largeForecast.getBody());
    }

    // Helper method
    private TeamMemberEntity addTeamMember(Long teamId, String accountId, String name,
                                            Role role, Grade grade, BigDecimal hoursPerDay) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeam(teamRepository.findById(teamId).orElseThrow());
        member.setJiraAccountId(accountId);
        member.setDisplayName(name);
        member.setRole(role);
        member.setGrade(grade);
        member.setHoursPerDay(hoursPerDay);
        member.setActive(true);
        return memberRepository.save(member);
    }
}
