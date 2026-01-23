package com.leadboard.jira;

import com.leadboard.config.JiraProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class JiraClient {

    private final WebClient webClient;
    private final JiraProperties jiraProperties;

    public JiraClient(JiraProperties jiraProperties, WebClient.Builder webClientBuilder) {
        this.jiraProperties = jiraProperties;
        this.webClient = webClientBuilder
                .baseUrl(jiraProperties.getBaseUrl() != null ? jiraProperties.getBaseUrl() : "")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public JiraSearchResponse search(String jql, int startAt, int maxResults) {
        if (jiraProperties.getBaseUrl() == null || jiraProperties.getBaseUrl().isEmpty()) {
            throw new IllegalStateException("Jira base URL is not configured");
        }

        String auth = jiraProperties.getEmail() + ":" + jiraProperties.getApiToken();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/api/3/search/jql")
                        .queryParam("jql", jql)
                        .queryParam("startAt", startAt)
                        .queryParam("maxResults", maxResults)
                        .queryParam("fields", "summary,status,issuetype,parent,project")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .retrieve()
                .bodyToMono(JiraSearchResponse.class)
                .block();
    }
}
