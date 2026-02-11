package com.leadboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "atlassian.oauth")
public class AtlassianOAuthProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scopes = "read:me read:jira-user read:jira-work write:jira-work offline_access";
    private String siteBaseUrl;

    // OAuth endpoints
    private String authorizationUri = "https://auth.atlassian.com/authorize";
    private String tokenUri = "https://auth.atlassian.com/oauth/token";
    private String userInfoUri = "https://api.atlassian.com/me";
    private String accessibleResourcesUri = "https://api.atlassian.com/oauth/token/accessible-resources";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getSiteBaseUrl() {
        return siteBaseUrl;
    }

    public void setSiteBaseUrl(String siteBaseUrl) {
        this.siteBaseUrl = siteBaseUrl;
    }

    public String getAuthorizationUri() {
        return authorizationUri;
    }

    public void setAuthorizationUri(String authorizationUri) {
        this.authorizationUri = authorizationUri;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public String getUserInfoUri() {
        return userInfoUri;
    }

    public void setUserInfoUri(String userInfoUri) {
        this.userInfoUri = userInfoUri;
    }

    public String getAccessibleResourcesUri() {
        return accessibleResourcesUri;
    }

    public void setAccessibleResourcesUri(String accessibleResourcesUri) {
        this.accessibleResourcesUri = accessibleResourcesUri;
    }
}
