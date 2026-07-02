package com.leadboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String frontendUrl = "http://localhost:5173";
    private String baseDomain = "leadboard.app";
    private Session session = new Session();
    private Encryption encryption = new Encryption();
    private AccessReconcile accessReconcile = new AccessReconcile();

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String getBaseDomain() {
        return baseDomain;
    }

    public void setBaseDomain(String baseDomain) {
        this.baseDomain = baseDomain;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Encryption getEncryption() {
        return encryption;
    }

    public void setEncryption(Encryption encryption) {
        this.encryption = encryption;
    }

    public AccessReconcile getAccessReconcile() {
        return accessReconcile;
    }

    public void setAccessReconcile(AccessReconcile accessReconcile) {
        this.accessReconcile = accessReconcile;
    }

    public static class Session {
        private String cookieName = "LEAD_SESSION";
        private int maxAgeDays = 30;
        private String cookieDomain;
        // SECURITY_AUDIT.md §9: fail-closed default. application.yml's
        // ${APP_SESSION_COOKIE_SECURE:true} placeholder must not override this to false.
        private boolean cookieSecure = true;

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public int getMaxAgeDays() {
            return maxAgeDays;
        }

        public void setMaxAgeDays(int maxAgeDays) {
            this.maxAgeDays = maxAgeDays;
        }

        public String getCookieDomain() {
            return cookieDomain;
        }

        public void setCookieDomain(String cookieDomain) {
            this.cookieDomain = cookieDomain;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }
    }

    public static class Encryption {
        private String tokenKey = "";

        public String getTokenKey() {
            return tokenKey;
        }

        public void setTokenKey(String tokenKey) {
            this.tokenKey = tokenKey;
        }
    }

    /**
     * F82: background reconciliation of tenant membership vs. Jira access
     * (see {@code com.leadboard.tenant.TenantAccessReconciler}).
     */
    public static class AccessReconcile {
        private boolean enabled = true;
        private int intervalSeconds = 14400; // 4 hours

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getIntervalSeconds() {
            return intervalSeconds;
        }

        public void setIntervalSeconds(int intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }
    }
}
