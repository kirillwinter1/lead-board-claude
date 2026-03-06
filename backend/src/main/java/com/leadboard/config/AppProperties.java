package com.leadboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String frontendUrl = "http://localhost:5173";
    private Session session = new Session();
    private Encryption encryption = new Encryption();

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
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

    public static class Session {
        private String cookieName = "LEAD_SESSION";
        private int maxAgeDays = 30;
        private String cookieDomain;
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
}
