package com.leadboard.jira;

import com.leadboard.auth.OAuthService;
import com.leadboard.config.JiraConfigResolver;
import io.netty.channel.ChannelOption;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class JiraClient {

    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);
    private static final String ATLASSIAN_API_BASE = "https://api.atlassian.com";
    private static final int MAX_BUFFER_SIZE = 16 * 1024 * 1024; // 16MB

    // Well-formed Jira issue key, e.g. "ABC-123". Used to guard values that get
    // interpolated into JQL (see #validateIssueKey) — SECURITY_AUDIT.md #4.
    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]+-\\d+$");

    private final WebClient webClient;
    private final JiraConfigResolver configResolver;
    private final OAuthService oauthService;

    public JiraClient(JiraConfigResolver configResolver, OAuthService oauthService,
                      WebClient.Builder webClientBuilder) {
        this.configResolver = configResolver;
        this.oauthService = oauthService;

        // Increase buffer size for large Jira responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE))
                .build();

        // BUG-48: Configure connection and response timeouts.
        // Use the JDK/OS DNS resolver instead of Netty's native UDP resolver, which
        // fails to resolve Atlassian hosts on some networks (UnknownHostException).
        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(30));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Builds the "Bearer &lt;token&gt;" Authorization header value used by every
     * OAuth-backed request in this client.
     */
    private static String bearerAuthHeaderValue(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * Builds the "Basic base64(email:token)" Authorization header value from the
     * currently configured Jira credentials (see {@link JiraConfigResolver}) — the
     * fallback used whenever OAuth is unavailable. Extracted because the "get OAuth
     * access token, else build a Basic Auth header" pattern was duplicated across
     * ~10 call sites in this client.
     */
    private String basicAuthHeaderValue() {
        return basicAuthHeaderValue(configResolver.getEmail(), configResolver.getApiToken());
    }

    /**
     * Same as {@link #basicAuthHeaderValue()} but for credentials that haven't been
     * saved via {@link JiraConfigResolver} yet (e.g. {@link #testConnection}).
     */
    private static String basicAuthHeaderValue(String email, String apiToken) {
        String auth = email + ":" + apiToken;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
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

    /**
     * Lightweight search that fetches only issue keys (for reconciliation of deleted issues).
     */
    public JiraSearchResponse searchKeysOnly(String jql, int maxResults, String nextPageToken) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        if (accessToken != null && cloudId != null) {
            return searchWithOAuth(jql, maxResults, nextPageToken, accessToken, cloudId, "key");
        }
        return searchWithBasicAuth(jql, maxResults, nextPageToken, "key");
    }

    private String buildFieldsList() {
        String baseFields = "summary,description,status,issuetype,parent,project,timetracking,priority,duedate,created,updated,assignee,flagged,customfield_10021,issuelinks,components,labels";
        String teamFieldId = configResolver.getTeamFieldId();
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
                .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                .retrieve()
                .bodyToMono(JiraSearchResponse.class)
                .block();
    }

    private JiraSearchResponse searchWithBasicAuth(String jql, int maxResults, String nextPageToken, String fields) {
        if (configResolver.getBaseUrl() == null || configResolver.getBaseUrl().isEmpty()) {
            throw new IllegalStateException("Jira base URL is not configured and OAuth is not available");
        }

        return webClient.get()
                .uri(configResolver.getBaseUrl() + "/rest/api/3/search/jql", uriBuilder -> {
                        uriBuilder.queryParam("jql", jql)
                                  .queryParam("maxResults", maxResults)
                                  .queryParam("fields", fields);
                        if (nextPageToken != null) {
                            uriBuilder.queryParam("nextPageToken", nextPageToken);
                        }
                        return uriBuilder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                .retrieve()
                .bodyToMono(JiraSearchResponse.class)
                .block();
    }

    /**
     * Count total issues matching a JQL query by paginating through results
     * using GET /search/jql (cursor-based, since POST /search is 410 Gone).
     */
    public int countByJql(String jql) {
        int total = 0;
        String nextPageToken = null;

        while (true) {
            JiraSearchResponse response = searchKeysOnly(jql, 100, nextPageToken);
            List<JiraIssue> issues = response.getIssues();
            if (issues == null || issues.isEmpty()) break;
            total += issues.size();
            if (response.getNextPageToken() == null) break;
            nextPageToken = response.getNextPageToken();
        }

        log.debug("countByJql: total={} for jql={}", total, jql);
        return total;
    }

    /**
     * Create a new issue in Jira (Story, Epic, etc.)
     */
    public String createIssue(String projectKey, String issueType, String summary, String parentKey) {
        return createIssue(projectKey, issueType, summary, parentKey, null, null);
    }

    /**
     * Create a new issue in Jira with an optional description and component list.
     * F23 rework: Planning Poker creates a Story with a description and the
     * component selected in the "Add story" form. {@code componentNames} are sent
     * as {@code components:[{name}]}; {@code description} is wrapped in ADF.
     */
    public String createIssue(String projectKey, String issueType, String summary, String parentKey,
                              String description, List<String> componentNames) {
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

        if (description != null && !description.isBlank()) {
            fields.put("description", toAdf(description));
        }

        if (componentNames != null && !componentNames.isEmpty()) {
            fields.put("components", componentNames.stream()
                    .filter(n -> n != null && !n.isBlank())
                    .map(n -> Map.of("name", n))
                    .toList());
        }

        return createWithRequiredRetry(fields, accessToken, cloudId);
    }

    /**
     * POST the issue to Jira and, if the create fails with a 400 because the project
     * marks some fields required on the create screen (e.g. LB requires {@code timetracking}
     * and {@code labels} on Story/Subtask), fill placeholder values for exactly the fields
     * Jira named and retry once. Jira's own error body is the source of truth, so this
     * adapts per-project without hardcoding a screen config and without touching projects
     * that don't require these fields. Placeholders are neutral (0m estimate, a marker
     * label); the real per-role estimates are written to subtasks later via publish.
     */
    private String createWithRequiredRetry(Map<String, Object> fields, String accessToken, String cloudId) {
        try {
            return postCreate(fields, accessToken, cloudId);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            if (e.getStatusCode().value() == 400) {
                Map<String, Object> extra = requiredFieldPlaceholders(e.getResponseBodyAsString(), fields);
                if (!extra.isEmpty()) {
                    fields.putAll(extra);
                    log.info("Jira create 400 — retrying with placeholders for required field(s): {}",
                            extra.keySet());
                    return postCreate(fields, accessToken, cloudId);
                }
            }
            throw e;
        }
    }

    private String postCreate(Map<String, Object> fields, String accessToken, String cloudId) {
        Map<String, Object> body = Map.of("fields", fields);
        if (accessToken != null && cloudId != null) {
            return createIssueWithOAuth(body, accessToken, cloudId);
        }
        return createIssueWithBasicAuth(body);
    }

    /**
     * Inspect a Jira create 400 body and return placeholder values for the required
     * system fields we know how to satisfy. Parses the structured {@code errors} object
     * ({@code {"errors":{"timetracking":"...","labels":"..."}}}) and only fills a field
     * when Jira reported an error keyed to THAT field whose message marks it required —
     * so an unrelated field's message mentioning "labels"/"timetracking" can't trigger a
     * spurious injection. Fields the caller already set are never overridden.
     */
    private Map<String, Object> requiredFieldPlaceholders(String errorBody, Map<String, Object> current) {
        Map<String, Object> out = new java.util.HashMap<>();
        if (errorBody == null) return out;

        Map<String, String> errors = parseJiraFieldErrors(errorBody);
        if (errors.isEmpty()) return out;

        boolean timetrackingRequired = errors.entrySet().stream()
                .anyMatch(en -> en.getKey().startsWith("timetracking") && isRequiredMessage(en.getValue()));
        boolean labelsRequired = errors.entrySet().stream()
                .anyMatch(en -> en.getKey().equals("labels") && isRequiredMessage(en.getValue()));

        if (timetrackingRequired && !current.containsKey("timetracking")) {
            out.put("timetracking", Map.of("originalEstimate", "0m", "remainingEstimate", "0m"));
        }
        if (labelsRequired && !current.containsKey("labels")) {
            out.put("labels", List.of("planning-poker"));
        }
        return out;
    }

    /** Parse the {@code errors} object (fieldId -> message) from a Jira 400 body; empty on any problem. */
    private Map<String, String> parseJiraFieldErrors(String errorBody) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode errorsNode = mapper.readTree(errorBody).path("errors");
            if (!errorsNode.isObject()) return Map.of();
            Map<String, String> errors = new java.util.HashMap<>();
            errorsNode.fields().forEachRemaining(en -> errors.put(en.getKey(), en.getValue().asText("")));
            return errors;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** True if a Jira field-error message marks the field as required (RU/EN). */
    private boolean isRequiredMessage(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        return m.contains("обязательно") || m.contains("is required") || m.contains("required");
    }

    /**
     * Wrap plain text in a minimal Atlassian Document Format (ADF) document,
     * as required by the {@code description} field on Jira Cloud REST API v3.
     */
    private static Map<String, Object> toAdf(String text) {
        return Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(Map.of(
                        "type", "paragraph",
                        "content", List.of(Map.of("type", "text", "text", text))
                ))
        );
    }

    private String createIssueWithOAuth(Map<String, Object> body, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        try {
            Map<String, Object> response = webClient.post()
                    .uri(baseUrl + "/rest/api/3/issue")
                    .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return (String) response.get("key");
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("Jira API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    private String createIssueWithBasicAuth(Map<String, Object> body) {
        Map<String, Object> response = webClient.post()
                .uri(configResolver.getBaseUrl() + "/rest/api/3/issue")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return (String) response.get("key");
    }

    /**
     * Create a subtask under a parent issue.
     * @param parentKey parent issue key
     * @param summary subtask summary/title
     * @param projectKey Jira project key
     * @param subtaskTypeName Jira issue type name for subtasks (e.g. "Sub-task", "Подзадача")
     */
    public String createSubtask(String parentKey, String summary, String projectKey, String subtaskTypeName) {
        return createSubtask(parentKey, summary, projectKey, subtaskTypeName, null, null);
    }

    public String createSubtask(String parentKey, String summary, String projectKey, String subtaskTypeName,
                                String description, List<String> componentNames) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        String typeName = (subtaskTypeName != null && !subtaskTypeName.isEmpty())
                ? subtaskTypeName : "Sub-task";

        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("summary", summary);
        fields.put("issuetype", Map.of("name", typeName));
        fields.put("parent", Map.of("key", parentKey));

        if (description != null && !description.isBlank()) {
            fields.put("description", toAdf(description));
        }
        if (componentNames != null && !componentNames.isEmpty()) {
            fields.put("components", componentNames.stream()
                    .filter(n -> n != null && !n.isBlank())
                    .map(n -> Map.of("name", n))
                    .toList());
        }

        // Same required-field retry as createIssue (subtask create screen may also
        // require timetracking / labels).
        return createWithRequiredRetry(fields, accessToken, cloudId);
    }

    /**
     * @deprecated Use {@link #createSubtask(String, String, String, String)} with explicit subtask type name.
     */
    @Deprecated
    public String createSubtask(String parentKey, String summary, String projectKey) {
        return createSubtask(parentKey, summary, projectKey, "Sub-task");
    }

    /**
     * Get subtasks of an issue.
     *
     * SECURITY (SECURITY_AUDIT.md #4): {@code parentKey} historically came straight
     * from user input (Planning Poker's {@code existingStoryKey}) and was concatenated
     * directly into JQL, allowing JQL injection (e.g. {@code "X OR project = SECRET"})
     * that would then be used to write estimates onto arbitrary issues. The DTO now
     * also validates this at the edge (AddStoryRequest#existingStoryKey), but this
     * method re-validates independently — it must never trust its caller.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSubtasks(String parentKey) {
        String safeParentKey = validateIssueKey(parentKey);
        String jql = "parent = \"" + escapeJqlLiteral(safeParentKey) + "\"";
        JiraSearchResponse response = search(jql, 50, null);
        return response.getIssues().stream()
                .map(issue -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("key", issue.getKey());
                    Map<String, Object> fieldsMap = new java.util.HashMap<>();
                    fieldsMap.put("summary", issue.getFields().getSummary());
                    if (issue.getFields().getIssuetype() != null) {
                        fieldsMap.put("issuetype", Map.of("name", issue.getFields().getIssuetype().getName()));
                    }
                    map.put("fields", fieldsMap);
                    return map;
                })
                .toList();
    }

    /**
     * Validates that {@code issueKey} is a well-formed Jira issue key (e.g. "ABC-123")
     * before it is allowed anywhere near a hand-built JQL string. Throws
     * {@link IllegalArgumentException} instead of building/executing any query when
     * the value doesn't match — see SECURITY_AUDIT.md #4 (JQL injection).
     */
    private static String validateIssueKey(String issueKey) {
        if (issueKey == null || !ISSUE_KEY_PATTERN.matcher(issueKey).matches()) {
            throw new IllegalArgumentException("Invalid Jira issue key");
        }
        return issueKey;
    }

    /**
     * Escapes a string literal for safe inclusion inside a double-quoted JQL value
     * (defense-in-depth on top of {@link #validateIssueKey}). JQL uses backslash
     * escaping for {@code \} and {@code "} inside quoted literals.
     */
    private static String escapeJqlLiteral(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Update labels on an issue (replaces full labels list).
     */
    public void updateLabels(String issueKey, List<String> labels) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        List<String> safeLabels = labels != null ? labels : List.of();
        Map<String, Object> body = Map.of(
                "fields", Map.of("labels", safeLabels)
        );

        if (accessToken != null && cloudId != null) {
            updateIssueWithOAuth(issueKey, body, accessToken, cloudId);
        } else {
            updateIssueWithBasicAuth(issueKey, body);
        }
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

    /**
     * Update an issue's summary and (optionally) description in Jira. Used when a poker
     * story that already exists in Jira is edited in the room, so the local copy never
     * contradicts Jira (jira-integration rule). Role/component structure is not touched
     * here — that maps to subtasks and is out of scope for an inline edit.
     */
    public void updateStoryFields(String issueKey, String summary, String description) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        Map<String, Object> fields = new java.util.HashMap<>();
        if (summary != null && !summary.isBlank()) {
            fields.put("summary", summary);
        }
        if (description != null && !description.isBlank()) {
            fields.put("description", toAdf(description));
        }
        if (fields.isEmpty()) return;

        Map<String, Object> body = Map.of("fields", fields);
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
                .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp -> jiraErrorMono(resp, issueKey, "4xx"))
                .onStatus(HttpStatusCode::is5xxServerError, resp -> jiraErrorMono(resp, issueKey, "5xx"))
                .toBodilessEntity()
                .block();
    }

    private void updateIssueWithBasicAuth(String issueKey, Map<String, Object> body) {
        webClient.put()
                .uri(configResolver.getBaseUrl() + "/rest/api/3/issue/" + issueKey)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp -> jiraErrorMono(resp, issueKey, "4xx"))
                .onStatus(HttpStatusCode::is5xxServerError, resp -> jiraErrorMono(resp, issueKey, "5xx"))
                .toBodilessEntity()
                .block();
    }

    /**
     * Delete an issue (and its subtasks) from Jira. Used to roll back a partially-created
     * poker Story when a following subtask create fails, so no orphaned Story is left in
     * Jira. Best-effort: callers treat failure here as non-fatal.
     */
    public void deleteIssue(String issueKey) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();
        String base = (accessToken != null && cloudId != null)
                ? ATLASSIAN_API_BASE + "/ex/jira/" + cloudId
                : configResolver.getBaseUrl();
        String auth = (accessToken != null && cloudId != null)
                ? bearerAuthHeaderValue(accessToken)
                : basicAuthHeaderValue();

        webClient.delete()
                .uri(base + "/rest/api/3/issue/" + issueKey + "?deleteSubtasks=true")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp -> jiraErrorMono(resp, issueKey, "4xx"))
                .onStatus(HttpStatusCode::is5xxServerError, resp -> jiraErrorMono(resp, issueKey, "5xx"))
                .toBodilessEntity()
                .block();
    }

    /**
     * Map a Jira non-2xx response into a {@link JiraClientException} carrying
     * the issue key, status code and (truncated) response body.
     * Logged with WARN since this is an upstream failure, not a service bug.
     */
    private Mono<JiraClientException> jiraErrorMono(
            org.springframework.web.reactive.function.client.ClientResponse response,
            String issueKey,
            String category) {
        HttpStatusCode status = response.statusCode();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    String trimmed = body != null && body.length() > 500 ? body.substring(0, 500) + "…" : body;
                    String msg = "Jira " + category + " (" + status.value() + ") for issue "
                            + issueKey + ": " + trimmed;
                    log.warn("{}", msg);
                    return new JiraClientException(msg);
                });
    }

    // ============================================================
    // Simulation methods — explicit accessToken/cloudId per user
    // ============================================================

    /**
     * Get available transitions for an issue using explicit OAuth credentials.
     */
    @SuppressWarnings("unchecked")
    public List<JiraTransition> getTransitions(String issueKey, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        Map<String, Object> response = webClient.get()
                .uri(baseUrl + "/rest/api/3/issue/" + issueKey + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("transitions")) {
            return List.of();
        }

        List<Map<String, Object>> transitions = (List<Map<String, Object>>) response.get("transitions");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return transitions.stream()
                .map(t -> mapper.convertValue(t, JiraTransition.class))
                .toList();
    }

    /**
     * Transition an issue using explicit OAuth credentials.
     */
    public void transitionIssue(String issueKey, String transitionId, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        webClient.post()
                .uri(baseUrl + "/rest/api/3/issue/" + issueKey + "/transitions?notifyUsers=false")
                .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                .bodyValue(Map.of("transition", Map.of("id", transitionId)))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Add a worklog to an issue using explicit OAuth credentials.
     */
    public void addWorklog(String issueKey, int timeSpentSeconds, LocalDate date,
                           String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        String started = date.atTime(9, 0, 0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'+0000'"));

        webClient.post()
                .uri(baseUrl + "/rest/api/3/issue/" + issueKey + "/worklog?notifyUsers=false")
                .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                .bodyValue(Map.of(
                        "timeSpentSeconds", timeSpentSeconds,
                        "started", started
                ))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Add a worklog to an issue using explicit OAuth credentials and return the created
     * worklog's id (F89 — per-user write path for "Log time" from My Work).
     *
     * @param comment optional worklog comment (ADF-wrapped, same structure as {@link #addComment}); may be null/blank
     * @return the created worklog's id, or null if the response didn't contain one
     */
    public String addWorklogReturningId(String issueKey, int timeSpentSeconds, LocalDate date,
                                        String comment, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        String started = date.atTime(9, 0, 0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'+0000'"));

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("timeSpentSeconds", timeSpentSeconds);
        body.put("started", started);
        if (comment != null && !comment.isBlank()) {
            Map<String, Object> adf = Map.of(
                    "type", "doc",
                    "version", 1,
                    "content", List.of(Map.of(
                            "type", "paragraph",
                            "content", List.of(Map.of("type", "text", "text", comment))
                    ))
            );
            body.put("comment", adf);
        }

        Map<String, Object> resp = webClient.post()
                .uri(baseUrl + "/rest/api/3/issue/" + issueKey + "/worklog?notifyUsers=false")
                .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
        return resp != null && resp.get("id") != null ? String.valueOf(resp.get("id")) : null;
    }

    /**
     * Add a comment to an issue using explicit OAuth credentials (F80 write).
     * Body is wrapped in Atlassian Document Format (ADF).
     */
    public void addComment(String issueKey, String text, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        Map<String, Object> adf = Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(Map.of(
                        "type", "paragraph",
                        "content", List.of(Map.of("type", "text", "text", text))
                ))
        );

        webClient.post()
                .uri(baseUrl + "/rest/api/3/issue/" + issueKey + "/comment")
                .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                .bodyValue(Map.of("body", adf))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Assign an issue to a user using explicit OAuth credentials (F80 write).
     * Pass accountId=null to unassign.
     */
    public void assignIssue(String issueKey, String accountId, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("accountId", accountId); // null => unassign

        webClient.put()
                .uri(baseUrl + "/rest/api/3/issue/" + issueKey + "/assignee?notifyUsers=false")
                .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    // ============================================================
    // Simulation methods — Basic Auth fallback (system API token)
    // ============================================================

    /**
     * Get available transitions using Basic Auth (system API token).
     */
    @SuppressWarnings("unchecked")
    public List<JiraTransition> getTransitionsBasicAuth(String issueKey) {
        Map<String, Object> response = webClient.get()
                .uri(configResolver.getBaseUrl() + "/rest/api/3/issue/" + issueKey + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("transitions")) {
            return List.of();
        }

        List<Map<String, Object>> transitions = (List<Map<String, Object>>) response.get("transitions");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return transitions.stream()
                .map(t -> mapper.convertValue(t, JiraTransition.class))
                .toList();
    }

    /**
     * Transition an issue using Basic Auth (system API token).
     */
    public void transitionIssueBasicAuth(String issueKey, String transitionId) {
        webClient.post()
                .uri(configResolver.getBaseUrl() + "/rest/api/3/issue/" + issueKey + "/transitions?notifyUsers=false")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                .bodyValue(Map.of("transition", Map.of("id", transitionId)))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Assign an issue to a user using Basic Auth (system API token).
     */
    public void assignIssueBasicAuth(String issueKey, String accountId) {
        webClient.put()
                .uri(configResolver.getBaseUrl() + "/rest/api/3/issue/" + issueKey + "/assignee?notifyUsers=false")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                .bodyValue(Map.of("accountId", accountId))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Add a worklog using Basic Auth (system API token).
     */
    public void addWorklogBasicAuth(String issueKey, int timeSpentSeconds, LocalDate date) {
        String started = date.atTime(9, 0, 0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'+0000'"));

        webClient.post()
                .uri(configResolver.getBaseUrl() + "/rest/api/3/issue/" + issueKey + "/worklog?notifyUsers=false")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                .bodyValue(Map.of(
                        "timeSpentSeconds", timeSpentSeconds,
                        "started", started
                ))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Fetch full changelog for an issue. Uses expand=changelog first,
     * then paginates via /changelog endpoint if there are more than 100 entries.
     */
    public List<JiraChangelogResponse.ChangelogHistory> fetchIssueChangelog(String issueKey) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        if (accessToken != null && cloudId != null) {
            return fetchChangelogWithOAuth(issueKey, accessToken, cloudId);
        }
        return fetchChangelogWithBasicAuth(issueKey);
    }

    private List<JiraChangelogResponse.ChangelogHistory> fetchChangelogWithOAuth(
            String issueKey, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        JiraChangelogResponse response = webClient.get()
                .uri(baseUrl + "/rest/api/3/issue/" + issueKey + "?expand=changelog&fields=status")
                .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                .retrieve()
                .bodyToMono(JiraChangelogResponse.class)
                .block();

        if (response == null || response.getChangelog() == null) {
            return List.of();
        }

        var changelog = response.getChangelog();
        List<JiraChangelogResponse.ChangelogHistory> allHistories =
                new java.util.ArrayList<>(changelog.getHistories() != null ? changelog.getHistories() : List.of());

        // Paginate if there are more entries
        if (changelog.getTotal() > changelog.getMaxResults()) {
            int startAt = changelog.getMaxResults();
            while (startAt < changelog.getTotal()) {
                var page = fetchChangelogPageOAuth(issueKey, startAt, accessToken, cloudId);
                if (page == null || page.getValues() == null || page.getValues().isEmpty()) break;
                allHistories.addAll(page.getValues());
                if (page.isLast()) break;
                startAt += page.getMaxResults();
            }
        }

        return allHistories;
    }

    private JiraChangelogResponse.PaginatedChangelog fetchChangelogPageOAuth(
            String issueKey, int startAt, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;

        return webClient.get()
                .uri(baseUrl + "/rest/api/3/issue/" + issueKey + "/changelog?startAt=" + startAt)
                .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                .retrieve()
                .bodyToMono(JiraChangelogResponse.PaginatedChangelog.class)
                .block();
    }

    private List<JiraChangelogResponse.ChangelogHistory> fetchChangelogWithBasicAuth(String issueKey) {
        if (configResolver.getBaseUrl() == null || configResolver.getBaseUrl().isEmpty()) {
            throw new IllegalStateException("Jira base URL is not configured and OAuth is not available");
        }

        JiraChangelogResponse response = webClient.get()
                .uri(configResolver.getBaseUrl() + "/rest/api/3/issue/" + issueKey + "?expand=changelog&fields=status")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                .retrieve()
                .bodyToMono(JiraChangelogResponse.class)
                .block();

        if (response == null || response.getChangelog() == null) {
            return List.of();
        }

        var changelog = response.getChangelog();
        List<JiraChangelogResponse.ChangelogHistory> allHistories =
                new java.util.ArrayList<>(changelog.getHistories() != null ? changelog.getHistories() : List.of());

        if (changelog.getTotal() > changelog.getMaxResults()) {
            int startAt = changelog.getMaxResults();
            while (startAt < changelog.getTotal()) {
                var page = fetchChangelogPageBasicAuth(issueKey, startAt);
                if (page == null || page.getValues() == null || page.getValues().isEmpty()) break;
                allHistories.addAll(page.getValues());
                if (page.isLast()) break;
                startAt += page.getMaxResults();
            }
        }

        return allHistories;
    }

    private JiraChangelogResponse.PaginatedChangelog fetchChangelogPageBasicAuth(
            String issueKey, int startAt) {
        return webClient.get()
                .uri(configResolver.getBaseUrl() + "/rest/api/3/issue/" + issueKey + "/changelog?startAt=" + startAt)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                .retrieve()
                .bodyToMono(JiraChangelogResponse.PaginatedChangelog.class)
                .block();
    }

    /**
     * Fetch worklogs for an issue. Uses /rest/api/3/issue/{key}/worklog with pagination.
     */
    public List<JiraWorklogResponse.WorklogEntry> fetchIssueWorklogs(String issueKey) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        if (accessToken != null && cloudId != null) {
            return fetchWorklogsWithOAuth(issueKey, accessToken, cloudId);
        }
        return fetchWorklogsWithBasicAuth(issueKey);
    }

    private List<JiraWorklogResponse.WorklogEntry> fetchWorklogsWithOAuth(
            String issueKey, String accessToken, String cloudId) {
        String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;
        return fetchWorklogsPaginated(issueKey,
                baseUrl + "/rest/api/3/issue/" + issueKey + "/worklog",
                bearerAuthHeaderValue(accessToken));
    }

    private List<JiraWorklogResponse.WorklogEntry> fetchWorklogsWithBasicAuth(String issueKey) {
        if (configResolver.getBaseUrl() == null || configResolver.getBaseUrl().isEmpty()) {
            throw new IllegalStateException("Jira base URL is not configured and OAuth is not available");
        }
        return fetchWorklogsPaginated(issueKey,
                configResolver.getBaseUrl() + "/rest/api/3/issue/" + issueKey + "/worklog",
                basicAuthHeaderValue());
    }

    private List<JiraWorklogResponse.WorklogEntry> fetchWorklogsPaginated(
            String issueKey, String url, String authHeader) {
        List<JiraWorklogResponse.WorklogEntry> allEntries = new java.util.ArrayList<>();
        int startAt = 0;

        while (true) {
            String paginatedUrl = url + "?startAt=" + startAt + "&maxResults=1000";
            JiraWorklogResponse response = webClient.get()
                    .uri(paginatedUrl)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .bodyToMono(JiraWorklogResponse.class)
                    .block();

            if (response == null || response.getWorklogs() == null || response.getWorklogs().isEmpty()) {
                break;
            }

            allEntries.addAll(response.getWorklogs());

            if (startAt + response.getMaxResults() >= response.getTotal()) {
                break;
            }
            startAt += response.getMaxResults();
        }

        return allEntries;
    }

    /**
     * Get project components from Jira.
     */
    @SuppressWarnings("unchecked")
    public List<String> getProjectComponents(String projectKey) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        List<Map<String, Object>> components;
        if (accessToken != null && cloudId != null) {
            String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;
            components = webClient.get()
                    .uri(baseUrl + "/rest/api/3/project/" + projectKey + "/components")
                    .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
        } else {
            components = webClient.get()
                    .uri(configResolver.getBaseUrl() + "/rest/api/3/project/" + projectKey + "/components")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
        }

        if (components == null) return List.of();
        return components.stream()
                .map(c -> (String) c.get("name"))
                .filter(name -> name != null && !name.isEmpty())
                .toList();
    }

    /**
     * Get project components as {id, name} pairs (F23 rework — the "Add story" form
     * needs both the id for selection and the name for display / Jira create).
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getComponents(String projectKey) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        List<Map<String, Object>> components;
        if (accessToken != null && cloudId != null) {
            String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;
            components = webClient.get()
                    .uri(baseUrl + "/rest/api/3/project/" + projectKey + "/components")
                    .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
        } else {
            if (configResolver.getBaseUrl() == null || configResolver.getBaseUrl().isEmpty()) {
                throw new IllegalStateException("Jira base URL is not configured and OAuth is not available");
            }
            components = webClient.get()
                    .uri(configResolver.getBaseUrl() + "/rest/api/3/project/" + projectKey + "/components")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
        }

        if (components == null) return List.of();
        return components.stream()
                .map(c -> {
                    Object id = c.get("id");
                    Object name = c.get("name");
                    Map<String, String> m = new java.util.HashMap<>();
                    m.put("id", id != null ? String.valueOf(id) : null);
                    m.put("name", name != null ? String.valueOf(name) : null);
                    return m;
                })
                .filter(m -> m.get("name") != null && !m.get("name").isEmpty())
                .toList();
    }

    /**
     * Verifies Jira credentials that have not been saved yet (e.g. the tenant Jira
     * config setup wizard), so they can't be resolved via {@link JiraConfigResolver}.
     * Credentials are passed explicitly and used for a single Basic Auth request.
     */
    public String testConnection(String baseUrl, String email, String apiToken) {
        return webClient.get()
                .uri(baseUrl + "/rest/api/3/myself")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue(email, apiToken))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
    }

    /**
     * Generic GET against the Jira REST API, using this client's usual OAuth-first
     * (falling back to Basic Auth) auth logic. Intended for callers that only need
     * a raw response shape (e.g. metadata lookups) rather than a typed DTO.
     */
    public <T> T getRaw(String path, Class<T> responseType) {
        String accessToken = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();

        if (accessToken != null && cloudId != null) {
            String baseUrl = ATLASSIAN_API_BASE + "/ex/jira/" + cloudId;
            return webClient.get()
                    .uri(baseUrl + path)
                    .header(HttpHeaders.AUTHORIZATION, bearerAuthHeaderValue(accessToken))
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        }

        if (configResolver.getBaseUrl() == null || configResolver.getBaseUrl().isEmpty()) {
            throw new IllegalStateException("Jira base URL is not configured and OAuth is not available");
        }

        return webClient.get()
                .uri(configResolver.getBaseUrl() + path)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue())
                .retrieve()
                .bodyToMono(responseType)
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
