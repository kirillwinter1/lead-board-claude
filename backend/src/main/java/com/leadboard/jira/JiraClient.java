package com.leadboard.jira;

import com.leadboard.auth.OAuthService;
import com.leadboard.config.JiraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class JiraClient {

    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);
    private static final String ATLASSIAN_API_BASE = "https://api.atlassian.com";
    private static final int MAX_BUFFER_SIZE = 16 * 1024 * 1024; // 16MB

    private final WebClient webClient;
    private final JiraProperties jiraProperties;
    private final OAuthService oauthService;

    public JiraClient(JiraProperties jiraProperties, OAuthService oauthService, WebClient.Builder webClientBuilder) {
        this.jiraProperties = jiraProperties;
        this.oauthService = oauthService;

        // Increase buffer size for large Jira responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE))
                .build();

        this.webClient = webClientBuilder
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public JiraSearchResponse search(String jql, int startAt, int maxResults) {
        return search(jql, maxResults, null);
    }

    public JiraSearchResponse search(String jql, int maxResults, String nextPageToken) {
        // Try OAuth first
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        // Build fields list including team field if configured
        String fields = buildFieldsList();

        if (accessToken != null && cloudId != null) {
            log.debug("Using OAuth for Jira API");
            return searchWithOAuth(jql, maxResults, nextPageToken, accessToken, cloudId, fields);
        }

        // Fall back to Basic Auth
        log.debug("Using Basic Auth for Jira API");
        return searchWithBasicAuth(jql, maxResults, nextPageToken, fields);
    }

    private String buildFieldsList() {
        String baseFields = "summary,status,issuetype,parent,project,timetracking,priority,duedate,created,assignee,flagged,issuelinks";
        String teamFieldId = jiraProperties.getTeamFieldId();
        if (teamFieldId != null && !teamFieldId.isEmpty()) {
            return baseFields + "," + teamFieldId;
        }
        return baseFields;
    }

    private JiraSearchResponse searchWithOAuth(String jql, int maxResults, String nextPageToken, String accessToken, String cloudId, String fields) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        return webClient.get()
                .uri(baseUrl + "/rest/api/3/search/jql", uriBuilder -> {
                        uriBuilder.queryParam("jql", jql)
                                  .queryParam("maxResults", maxResults)
                                  .queryParam("fields", fields);
                        if (nextPageToken != null) {
                            uriBuilder.queryParam("nextPageToken", nextPageToken);
                        }
                        return uriBuilder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JiraSearchResponse.class)
                .block();
    }

    private JiraSearchResponse searchWithBasicAuth(String jql, int maxResults, String nextPageToken, String fields) {
        if (jiraProperties.getBaseUrl() == null || jiraProperties.getBaseUrl().isEmpty()) {
            throw new IllegalStateException("Jira base URL is not configured and OAuth is not available");
        }

        String auth = jiraProperties.getEmail() + ":" + jiraProperties.getApiToken();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        return webClient.get()
                .uri(jiraProperties.getBaseUrl() + "/rest/api/3/search/jql", uriBuilder -> {
                        uriBuilder.queryParam("jql", jql)
                                  .queryParam("maxResults", maxResults)
                                  .queryParam("fields", fields);
                        if (nextPageToken != null) {
                            uriBuilder.queryParam("nextPageToken", nextPageToken);
                        }
                        return uriBuilder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .retrieve()
                .bodyToMono(JiraSearchResponse.class)
                .block();
    }

    /**
     * Create a new issue in Jira (Story, Epic, etc.)
     */
    public String createIssue(String projectKey, String issueType, String summary, String parentKey) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("summary", summary);
        fields.put("issuetype", Map.of("name", issueType));

        if (parentKey != null && !parentKey.isEmpty()) {
            // For Story linked to Epic
            fields.put("parent", Map.of("key", parentKey));
        }

        Map<String, Object> body = Map.of("fields", fields);

        if (accessToken != null && cloudId != null) {
            return createIssueWithOAuth(body, accessToken, cloudId);
        }
        return createIssueWithBasicAuth(body);
    }

    private String createIssueWithOAuth(Map<String, Object> body, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        Map<String, Object> response = webClient.post()
                .uri(baseUrl + "/rest/api/3/issue")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return (String) response.get("key");
    }

    private String createIssueWithBasicAuth(Map<String, Object> body) {
        String auth = jiraProperties.getEmail() + ":" + jiraProperties.getApiToken();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> response = webClient.post()
                .uri(jiraProperties.getBaseUrl() + "/rest/api/3/issue")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return (String) response.get("key");
    }

    /**
     * Create a subtask under a parent issue
     */
    public String createSubtask(String parentKey, String summary, String projectKey) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("summary", summary);
        fields.put("issuetype", Map.of("name", "Sub-task"));
        fields.put("parent", Map.of("key", parentKey));

        Map<String, Object> body = Map.of("fields", fields);

        if (accessToken != null && cloudId != null) {
            return createIssueWithOAuth(body, accessToken, cloudId);
        }
        return createIssueWithBasicAuth(body);
    }

    /**
     * Get subtasks of an issue
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSubtasks(String parentKey) {
        String jql = "parent = " + parentKey;
        JiraSearchResponse response = search(jql, 50, null);
        return response.getIssues().stream()
                .map(issue -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("key", issue.getKey());
                    map.put("fields", Map.of("summary", issue.getFields().getSummary()));
                    return map;
                })
                .toList();
    }

    /**
     * Update time estimate on an issue (in seconds)
     */
    public void updateEstimate(String issueKey, int estimateSeconds) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        Map<String, Object> body = Map.of(
                "fields", Map.of(
                        "timetracking", Map.of(
                                "originalEstimate", formatTimeEstimate(estimateSeconds)
                        )
                )
        );

        if (accessToken != null && cloudId != null) {
            updateIssueWithOAuth(issueKey, body, accessToken, cloudId);
        } else {
            updateIssueWithBasicAuth(issueKey, body);
        }
    }

    private void updateIssueWithOAuth(String issueKey, Map<String, Object> body, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        webClient.put()
                .uri(baseUrl + "/rest/api/3/issue/" + issueKey)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private void updateIssueWithBasicAuth(String issueKey, Map<String, Object> body) {
        String auth = jiraProperties.getEmail() + ":" + jiraProperties.getApiToken();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        webClient.put()
                .uri(jiraProperties.getBaseUrl() + "/rest/api/3/issue/" + issueKey)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private String formatTimeEstimate(int seconds) {
        int hours = seconds / 3600;
        if (hours >= 8) {
            int days = hours / 8;
            int remainingHours = hours % 8;
            if (remainingHours > 0) {
                return days + "d " + remainingHours + "h";
            }
            return days + "d";
        }
        return hours + "h";
    }
}
