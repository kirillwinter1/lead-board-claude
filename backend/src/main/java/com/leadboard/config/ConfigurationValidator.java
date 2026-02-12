package com.leadboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationValidator.class);

    private final JiraProperties jiraProperties;
    private final AtlassianOAuthProperties oauthProperties;

    public ConfigurationValidator(JiraProperties jiraProperties, AtlassianOAuthProperties oauthProperties) {
        this.jiraProperties = jiraProperties;
        this.oauthProperties = oauthProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Jira integration (required for core functionality)
        validateRequired(jiraProperties.getBaseUrl(), "JIRA_BASE_URL (jira.base-url)", errors);
        validateRequired(jiraProperties.getEmail(), "JIRA_EMAIL (jira.email)", errors);
        validateRequired(jiraProperties.getApiToken(), "JIRA_API_TOKEN (jira.api-token)", errors);
        validateRequired(jiraProperties.getProjectKey(), "JIRA_PROJECT_KEY (jira.project-key)", errors);
        validateRequired(jiraProperties.getTeamFieldId(), "JIRA_TEAM_FIELD_ID (jira.team-field-id)", errors);

        // Atlassian OAuth (required for user authentication)
        validateRequired(oauthProperties.getClientId(), "ATLASSIAN_CLIENT_ID (atlassian.oauth.client-id)", errors);
        validateRequired(oauthProperties.getClientSecret(), "ATLASSIAN_CLIENT_SECRET (atlassian.oauth.client-secret)", errors);
        validateRequired(oauthProperties.getRedirectUri(), "ATLASSIAN_REDIRECT_URI (atlassian.oauth.redirect-uri)", warnings);
        validateRequired(oauthProperties.getSiteBaseUrl(), "ATLASSIAN_SITE_BASE_URL (atlassian.oauth.site-base-url)", errors);

        // Log warnings
        for (String warning : warnings) {
            log.warn("Configuration warning: {}", warning);
        }

        // Fail on errors
        if (!errors.isEmpty()) {
            String message = String.format(
                    "Application configuration is incomplete. Missing %d required properties:\n  - %s",
                    errors.size(),
                    String.join("\n  - ", errors)
            );
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Configuration validation passed");
    }

    private void validateRequired(String value, String propertyName, List<String> issues) {
        if (value == null || value.isBlank()) {
            issues.add(propertyName + " is not set");
        }
    }
}
