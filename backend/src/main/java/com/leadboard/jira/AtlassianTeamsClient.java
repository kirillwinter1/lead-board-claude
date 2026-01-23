package com.leadboard.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leadboard.config.JiraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
public class AtlassianTeamsClient {

    private static final Logger log = LoggerFactory.getLogger(AtlassianTeamsClient.class);

    private final WebClient webClient;
    private final JiraProperties jiraProperties;

    public AtlassianTeamsClient(JiraProperties jiraProperties, WebClient.Builder webClientBuilder) {
        this.jiraProperties = jiraProperties;
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public TeamsResponse getTeams() {
        String orgId = jiraProperties.getOrganizationId();
        if (orgId == null || orgId.isEmpty()) {
            throw new IllegalStateException("Organization ID is not configured");
        }

        String url = jiraProperties.getBaseUrl() + "/gateway/api/public/teams/v1/org/" + orgId + "/teams";
        log.debug("Fetching teams from: {}", url);

        return webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + getBasicAuth())
                .retrieve()
                .bodyToMono(TeamsResponse.class)
                .block();
    }

    public TeamMembersResponse getTeamMembers(String teamId) {
        String orgId = jiraProperties.getOrganizationId();
        if (orgId == null || orgId.isEmpty()) {
            throw new IllegalStateException("Organization ID is not configured");
        }

        String url = jiraProperties.getBaseUrl() + "/gateway/api/public/teams/v1/org/" + orgId + "/teams/" + teamId + "/members";
        log.debug("Fetching team members from: {}", url);

        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + getBasicAuth())
                .retrieve()
                .bodyToMono(TeamMembersResponse.class)
                .block();
    }

    public AtlassianUser getUser(String accountId) {
        String url = jiraProperties.getBaseUrl() + "/rest/api/3/user";
        log.debug("Fetching user info for: {}", accountId);

        return webClient.get()
                .uri(url, uriBuilder -> uriBuilder
                        .queryParam("accountId", accountId)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Basic " + getBasicAuth())
                .retrieve()
                .bodyToMono(AtlassianUser.class)
                .block();
    }

    private String getBasicAuth() {
        String auth = jiraProperties.getEmail() + ":" + jiraProperties.getApiToken();
        return Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    // Response DTOs

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamsResponse {
        @JsonProperty("entities")
        private List<AtlassianTeam> entities;

        @JsonProperty("cursor")
        private String cursor;

        public List<AtlassianTeam> getEntities() {
            return entities;
        }

        public void setEntities(List<AtlassianTeam> entities) {
            this.entities = entities;
        }

        public String getCursor() {
            return cursor;
        }

        public void setCursor(String cursor) {
            this.cursor = cursor;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtlassianTeam {
        @JsonProperty("teamId")
        private String teamId;

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("description")
        private String description;

        @JsonProperty("state")
        private String state;

        public String getTeamId() {
            return teamId;
        }

        public void setTeamId(String teamId) {
            this.teamId = teamId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamMembersResponse {
        @JsonProperty("results")
        private List<TeamMember> results;

        @JsonProperty("pageInfo")
        private PageInfo pageInfo;

        public List<TeamMember> getResults() {
            return results;
        }

        public void setResults(List<TeamMember> results) {
            this.results = results;
        }

        public PageInfo getPageInfo() {
            return pageInfo;
        }

        public void setPageInfo(PageInfo pageInfo) {
            this.pageInfo = pageInfo;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamMember {
        @JsonProperty("accountId")
        private String accountId;

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        @JsonProperty("hasNextPage")
        private boolean hasNextPage;

        @JsonProperty("endCursor")
        private String endCursor;

        public boolean isHasNextPage() {
            return hasNextPage;
        }

        public void setHasNextPage(boolean hasNextPage) {
            this.hasNextPage = hasNextPage;
        }

        public String getEndCursor() {
            return endCursor;
        }

        public void setEndCursor(String endCursor) {
            this.endCursor = endCursor;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtlassianUser {
        @JsonProperty("accountId")
        private String accountId;

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("emailAddress")
        private String emailAddress;

        @JsonProperty("active")
        private boolean active;

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getEmailAddress() {
            return emailAddress;
        }

        public void setEmailAddress(String emailAddress) {
            this.emailAddress = emailAddress;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
