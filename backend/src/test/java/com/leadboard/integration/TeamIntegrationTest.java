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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Team API.
 * Tests CRUD operations, team members, and planning config with real PostgreSQL.
 */
@DisplayName("Team Integration Tests")
class TeamIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TeamMemberRepository memberRepository;

    // ========== Team CRUD ==========

    @Test
    @DisplayName("Should create and retrieve team")
    void shouldCreateAndRetrieveTeam() {
        // When - create team
        var createRequest = new CreateTeamRequest("Integration Team", "integration-jira-value");
        var createResponse = restTemplate.postForEntity(
                "/api/teams",
                createRequest,
                TeamDto.class);

        // Then
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody());
        assertNotNull(createResponse.getBody().id());
        assertEquals("Integration Team", createResponse.getBody().name());

        // When - retrieve team
        var getResponse = restTemplate.getForEntity(
                "/api/teams/" + createResponse.getBody().id(),
                TeamDto.class);

        // Then
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals("Integration Team", getResponse.getBody().name());
    }

    @Test
    @DisplayName("Should list all active teams")
    void shouldListAllActiveTeams() {
        // Given
        createTeam("Team Alpha");
        createTeam("Team Beta");
        createTeam("Team Gamma");

        // When
        var response = restTemplate.getForEntity("/api/teams", TeamDto[].class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().length >= 3);
    }

    @Test
    @DisplayName("Should update team")
    void shouldUpdateTeam() {
        // Given
        var team = createTeam("Original Name");

        // When
        var updateRequest = new UpdateTeamRequest("Updated Name", "updated-jira-value");
        var response = restTemplate.exchange(
                "/api/teams/" + team.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                TeamDto.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Updated Name", response.getBody().name());
        assertEquals("updated-jira-value", response.getBody().jiraTeamValue());
    }

    @Test
    @DisplayName("Should soft delete team")
    void shouldSoftDeleteTeam() {
        // Given
        var team = createTeam("Team to Delete");

        // When
        var deleteResponse = restTemplate.exchange(
                "/api/teams/" + team.getId(),
                HttpMethod.DELETE,
                null,
                Void.class);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        // Team should not be in active list
        var listResponse = restTemplate.getForEntity("/api/teams", TeamDto[].class);
        boolean found = false;
        for (TeamDto t : listResponse.getBody()) {
            if (t.id().equals(team.getId())) {
                found = true;
                break;
            }
        }
        assertFalse(found, "Deleted team should not appear in active list");
    }

    // ========== Team Members ==========

    @Test
    @DisplayName("Should add and list team members")
    void shouldAddAndListTeamMembers() {
        // Given
        var team = createTeam("Dev Team");

        // When - add member
        var memberRequest = new CreateTeamMemberRequest(
                "jira-account-123",
                "John Developer",
                "DEV",
                Grade.SENIOR,
                new BigDecimal("7.0"));

        var addResponse = restTemplate.postForEntity(
                "/api/teams/" + team.getId() + "/members",
                memberRequest,
                TeamMemberDto.class);

        // Then
        assertEquals(HttpStatus.CREATED, addResponse.getStatusCode());
        assertEquals("John Developer", addResponse.getBody().displayName());
        assertEquals("DEV", addResponse.getBody().role());
        assertEquals(Grade.SENIOR, addResponse.getBody().grade());

        // When - list members
        var listResponse = restTemplate.getForEntity(
                "/api/teams/" + team.getId() + "/members",
                TeamMemberDto[].class);

        // Then
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertEquals(1, listResponse.getBody().length);
    }

    @Test
    @DisplayName("Should update team member")
    void shouldUpdateTeamMember() {
        // Given
        var team = createTeam("QA Team");
        var member = createTeamMember(team.getId(), "account-456", "Jane Tester", "QA", Grade.MIDDLE);

        // When
        var updateRequest = new UpdateTeamMemberRequest(
                "Jane Senior Tester",
                "QA",
                Grade.SENIOR,
                new BigDecimal("8.0"));

        var response = restTemplate.exchange(
                "/api/teams/" + team.getId() + "/members/" + member.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                TeamMemberDto.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Jane Senior Tester", response.getBody().displayName());
        assertEquals(Grade.SENIOR, response.getBody().grade());
    }

    @Test
    @DisplayName("Should deactivate team member")
    void shouldDeactivateTeamMember() {
        // Given
        var team = createTeam("Analytics Team");
        var member = createTeamMember(team.getId(), "account-789", "Alex Analyst", "SA", Grade.MIDDLE);

        // When
        var response = restTemplate.postForEntity(
                "/api/teams/" + team.getId() + "/members/" + member.getId() + "/deactivate",
                null,
                Void.class);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Member should not be in active list
        var listResponse = restTemplate.getForEntity(
                "/api/teams/" + team.getId() + "/members",
                TeamMemberDto[].class);
        assertEquals(0, listResponse.getBody().length);
    }

    // ========== Planning Config ==========

    @Test
    @DisplayName("Should get and update planning config")
    void shouldGetAndUpdatePlanningConfig() {
        // Given
        var team = createTeam("Planning Team");

        // When - get default config
        var getResponse = restTemplate.getForEntity(
                "/api/teams/" + team.getId() + "/planning-config",
                PlanningConfigDto.class);

        // Then - should have defaults
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());

        // When - update config
        var config = getResponse.getBody();
        var updatedConfig = new PlanningConfigDto(
                config.gradeCoefficients(),
                new BigDecimal("0.3"), // increased risk buffer
                config.wipLimits(),
                config.storyDuration());

        var updateResponse = restTemplate.exchange(
                "/api/teams/" + team.getId() + "/planning-config",
                HttpMethod.PUT,
                new HttpEntity<>(updatedConfig),
                PlanningConfigDto.class);

        // Then
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
        assertEquals(0, new BigDecimal("0.3").compareTo(updateResponse.getBody().riskBuffer()));
    }

    // ========== Helper Methods ==========

    private TeamMemberEntity createTeamMember(Long teamId, String accountId, String name, String role, Grade grade) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeam(teamRepository.findById(teamId).orElseThrow());
        member.setJiraAccountId(accountId);
        member.setDisplayName(name);
        member.setRole(role);
        member.setGrade(grade);
        member.setHoursPerDay(new BigDecimal("6.0"));
        member.setActive(true);
        return memberRepository.save(member);
    }
}
