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
        String baseFields = "summary,status,issuetype,parent,project,timetracking";
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
}
