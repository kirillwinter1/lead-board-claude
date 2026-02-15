package com.leadboard.config.service;

import com.leadboard.auth.OAuthService;
import com.leadboard.config.JiraProperties;
import com.leadboard.config.entity.TrackerMetadataCacheEntity;
import com.leadboard.config.repository.TrackerMetadataCacheRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Fetches metadata from Jira API for workflow configuration.
 * Caches results in tracker_metadata_cache table.
 */
@Service
public class JiraMetadataService {

    private static final Logger log = LoggerFactory.getLogger(JiraMetadataService.class);
    private static final String ATLASSIAN_API_BASE = "https://api.atlassian.com";

    private final JiraProperties jiraProperties;
    private final OAuthService oauthService;
    private final WebClient webClient;
    private final TrackerMetadataCacheRepository cacheRepo;
    private final ObjectMapper objectMapper;

    public JiraMetadataService(
            JiraProperties jiraProperties,
            OAuthService oauthService,
            WebClient.Builder webClientBuilder,
            TrackerMetadataCacheRepository cacheRepo,
            ObjectMapper objectMapper
    ) {
        this.jiraProperties = jiraProperties;
        this.oauthService = oauthService;
        this.webClient = webClientBuilder.build();
        this.cacheRepo = cacheRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Gets issue types for the configured project.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getIssueTypes() {
        // Check cache first (TTL: 1 hour)
        String cacheKey = "issue_types_" + jiraProperties.getProjectKey();
        String cached = getCachedValue(cacheKey, 60);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse cached issue types", e);
            }
        }

        try {
            String projectKey = jiraProperties.getProjectKey();
            Map<String, Object> project = callJiraApi("/rest/api/3/project/" + projectKey);

            List<Map<String, Object>> issueTypes = (List<Map<String, Object>>) project.get("issueTypes");
            if (issueTypes == null) issueTypes = List.of();

            // Simplify response
            List<Map<String, Object>> result = issueTypes.stream().map(it -> {
                Map<String, Object> simplified = new LinkedHashMap<>();
                simplified.put("id", it.get("id"));
                simplified.put("name", it.get("name"));
                simplified.put("subtask", it.get("subtask"));
                simplified.put("description", it.get("description"));
                simplified.put("iconUrl", it.get("iconUrl"));
                return simplified;
            }).toList();

            cacheValue(cacheKey, objectMapper.writeValueAsString(result));
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch issue types from Jira", e);
            return List.of();
        }
    }

    /**
     * Gets statuses grouped by issue type for the configured project.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getStatuses() {
        String cacheKey = "statuses_" + jiraProperties.getProjectKey();
        String cached = getCachedValue(cacheKey, 60);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse cached statuses", e);
            }
        }

        try {
            String projectKey = jiraProperties.getProjectKey();
            List<Map<String, Object>> response = callJiraApiList(
                    "/rest/api/3/project/" + projectKey + "/statuses");

            // Simplify: flatten into list of {issueType, statuses[{name, statusCategory}]}
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> typeStatuses : response) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("issueTypeId", typeStatuses.get("id"));
                entry.put("issueType", typeStatuses.get("name"));

                List<Map<String, Object>> statuses = (List<Map<String, Object>>) typeStatuses.get("statuses");
                List<Map<String, Object>> simplifiedStatuses = new ArrayList<>();
                if (statuses != null) {
                    for (Map<String, Object> status : statuses) {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("id", status.get("id"));
                        s.put("name", status.get("name"));
                        s.put("untranslatedName", status.get("untranslatedName"));
                        Map<String, Object> cat = (Map<String, Object>) status.get("statusCategory");
                        if (cat != null) {
                            s.put("statusCategory", cat.get("key"));
                            s.put("statusCategoryName", cat.get("name"));
                        }
                        simplifiedStatuses.add(s);
                    }
                }
                entry.put("statuses", simplifiedStatuses);
                result.add(entry);
            }

            cacheValue(cacheKey, objectMapper.writeValueAsString(result));
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch statuses from Jira", e);
            return List.of();
        }
    }

    /**
     * Gets link types from Jira.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getLinkTypes() {
        String cacheKey = "link_types";
        String cached = getCachedValue(cacheKey, 60);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse cached link types", e);
            }
        }

        try {
            Map<String, Object> response = callJiraApi("/rest/api/3/issueLinkType");
            List<Map<String, Object>> linkTypes = (List<Map<String, Object>>) response.get("issueLinkTypes");
            if (linkTypes == null) linkTypes = List.of();

            List<Map<String, Object>> result = linkTypes.stream().map(lt -> {
                Map<String, Object> simplified = new LinkedHashMap<>();
                simplified.put("id", lt.get("id"));
                simplified.put("name", lt.get("name"));
                simplified.put("inward", lt.get("inward"));
                simplified.put("outward", lt.get("outward"));
                return simplified;
            }).toList();

            cacheValue(cacheKey, objectMapper.writeValueAsString(result));
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch link types from Jira", e);
            return List.of();
        }
    }

    /**
     * Gets workflows with transitions and statuses from Jira.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getWorkflows() {
        String cacheKey = "workflows";
        String cached = getCachedValue(cacheKey, 60);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse cached workflows", e);
            }
        }

        try {
            Map<String, Object> response = callJiraApi(
                    "/rest/api/3/workflow/search?expand=transitions,statuses");

            List<Map<String, Object>> workflows = (List<Map<String, Object>>) response.get("values");
            if (workflows == null) workflows = List.of();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> wf : workflows) {
                Map<String, Object> simplified = new LinkedHashMap<>();
                simplified.put("name", wf.get("name"));

                // Simplify statuses
                List<Map<String, Object>> wfStatuses = (List<Map<String, Object>>) wf.get("statuses");
                List<Map<String, Object>> simplifiedStatuses = new ArrayList<>();
                if (wfStatuses != null) {
                    for (Map<String, Object> st : wfStatuses) {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("id", st.get("id"));
                        s.put("name", st.get("name"));
                        simplifiedStatuses.add(s);
                    }
                }
                simplified.put("statuses", simplifiedStatuses);

                // Simplify transitions
                List<Map<String, Object>> wfTransitions = (List<Map<String, Object>>) wf.get("transitions");
                List<Map<String, Object>> simplifiedTransitions = new ArrayList<>();
                if (wfTransitions != null) {
                    for (Map<String, Object> tr : wfTransitions) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("name", tr.get("name"));
                        t.put("type", tr.get("type"));

                        // "to" is a status ID string
                        Object toObj = tr.get("to");
                        t.put("to", toObj != null ? String.valueOf(toObj) : null);

                        // "from" is a list of status ID strings
                        List<String> fromList = new ArrayList<>();
                        Object fromObj = tr.get("from");
                        if (fromObj instanceof List) {
                            for (Object f : (List<?>) fromObj) {
                                fromList.add(String.valueOf(f));
                            }
                        }
                        t.put("from", fromList);

                        simplifiedTransitions.add(t);
                    }
                }
                simplified.put("transitions", simplifiedTransitions);

                result.add(simplified);
            }

            cacheValue(cacheKey, objectMapper.writeValueAsString(result));
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch workflows from Jira", e);
            return List.of();
        }
    }

    // ==================== Private helpers ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> callJiraApi(String path) {
        String url = buildUrl(path);
        HttpHeaders headers = buildAuthHeaders();

        return webClient.get()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callJiraApiList(String path) {
        String url = buildUrl(path);
        HttpHeaders headers = buildAuthHeaders();

        return webClient.get()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .retrieve()
                .bodyToMono(List.class)
                .block();
    }

    private String buildUrl(String path) {
        try {
            String accessToken = oauthService.getValidAccessToken();
            if (accessToken != null) {
                String cloudId = oauthService.getCloudIdForCurrentUser();
                return ATLASSIAN_API_BASE + "/ex/jira/" + cloudId + path;
            }
        } catch (Exception e) {
            // Fall through to basic auth
        }

        // Basic auth fallback
        return jiraProperties.getBaseUrl() + path;
    }

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            String accessToken = oauthService.getValidAccessToken();
            if (accessToken != null) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                return headers;
            }
        } catch (Exception e) {
            // Fall through to basic auth
        }

        // Basic auth
        String auth = jiraProperties.getEmail() + ":" + jiraProperties.getApiToken();
        String encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        return headers;
    }

    private String getCachedValue(String key, int ttlMinutes) {
        return cacheRepo.findByCacheKey(key)
                .filter(c -> c.getFetchedAt().plusMinutes(ttlMinutes).isAfter(OffsetDateTime.now()))
                .map(TrackerMetadataCacheEntity::getData)
                .orElse(null);
    }

    private void cacheValue(String key, String value) {
        try {
            TrackerMetadataCacheEntity entity = cacheRepo.findByCacheKey(key)
                    .orElseGet(() -> {
                        TrackerMetadataCacheEntity e = new TrackerMetadataCacheEntity();
                        e.setCacheKey(key);
                        return e;
                    });
            entity.setData(value);
            entity.setFetchedAt(OffsetDateTime.now());
            cacheRepo.saveAndFlush(entity);
        } catch (Exception e) {
            log.warn("Failed to cache metadata for key '{}': {}", key, e.getMessage());
        }
    }
}
