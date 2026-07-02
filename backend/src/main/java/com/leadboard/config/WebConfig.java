package com.leadboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    private static final List<String> ALLOWED_HEADERS = List.of(
            "Content-Type", "Authorization", "X-Requested-With",
            "X-Tenant-Slug", "Accept", "Origin"
    );

    private static final List<String> DEFAULT_DEV_ORIGINS = List.of(
            "http://localhost:5173",
            "http://localhost:3000"
    );

    /**
     * Result of parsing {@code cors.allowed-origins} (env {@code CORS_ALLOWED_ORIGINS}).
     *
     * <p>{@code exactOrigins} are passed to {@code allowedOrigins(...)}, {@code originPatterns}
     * to {@code allowedOriginPatterns(...)}. {@code rejectedBareWildcard} is set when the raw
     * config contained an unconditional wildcard entry (e.g. {@code "*"} or {@code "**"}) — such
     * an entry is dropped rather than forwarded to {@code allowedOriginPatterns}, because combined
     * with {@code allowCredentials(true)} (used on /api/** and /oauth/**) Spring would reflect
     * back whatever {@code Origin} header the caller sent alongside
     * {@code Access-Control-Allow-Credentials: true} — effectively "allow anyone with
     * credentials".
     */
    record ParsedOrigins(List<String> exactOrigins, List<String> originPatterns, boolean rejectedBareWildcard) {
    }

    /**
     * Parses a comma-separated {@code cors.allowed-origins} value into exact origins and scoped
     * origin patterns, on top of {@code baseExactOrigins} (e.g. localhost dev origins).
     *
     * <p>An entry is treated as an "unconditional wildcard" — and rejected — only when, once
     * trimmed, it consists solely of {@code *} characters (e.g. {@code "*"}, {@code "**"}).
     * Scoped patterns such as {@code https://*.onelane.ru} contain non-wildcard characters and
     * are still accepted as origin patterns, since they don't cause Spring to match every
     * possible Origin.
     */
    static ParsedOrigins parseOrigins(String rawAllowedOrigins, List<String> baseExactOrigins) {
        List<String> exactOrigins = new ArrayList<>(baseExactOrigins);
        List<String> originPatterns = new ArrayList<>();
        boolean rejectedBareWildcard = false;

        if (rawAllowedOrigins != null && !rawAllowedOrigins.isBlank()) {
            for (String origin : rawAllowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (isUnconditionalWildcard(trimmed)) {
                    rejectedBareWildcard = true;
                    continue;
                }
                if (trimmed.contains("*")) {
                    // Scoped wildcards require allowedOriginPatterns (e.g. https://*.onelane.ru)
                    originPatterns.add(trimmed);
                } else {
                    exactOrigins.add(trimmed);
                }
            }
        }

        return new ParsedOrigins(exactOrigins, originPatterns, rejectedBareWildcard);
    }

    private static boolean isUnconditionalWildcard(String trimmed) {
        return trimmed.chars().allMatch(c -> c == '*');
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        ParsedOrigins parsed = parseOrigins(corsAllowedOrigins, DEFAULT_DEV_ORIGINS);

        if (parsed.rejectedBareWildcard()) {
            log.error("CORS_ALLOWED_ORIGINS contains a bare wildcard entry (\"*\") which was " +
                    "IGNORED. /api/** and /oauth/** use allowCredentials(true), so forwarding a " +
                    "bare '*' to allowedOriginPatterns(...) would make Spring reflect back " +
                    "whatever Origin header the caller sends together with " +
                    "Access-Control-Allow-Credentials: true — effectively allowing any origin " +
                    "with credentials. Configure explicit origins or scoped patterns instead, " +
                    "e.g. https://*.onelane.ru");
        }

        List<String> exactOrigins = parsed.exactOrigins();
        List<String> originPatterns = parsed.originPatterns();

        String[] headersArray = ALLOWED_HEADERS.toArray(new String[0]);

        var apiMapping = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders(headersArray)
                .allowCredentials(true)
                .maxAge(3600);

        var oauthMapping = registry.addMapping("/oauth/**")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders(headersArray)
                .allowCredentials(true)
                .maxAge(3600);

        if (!originPatterns.isEmpty()) {
            // When using patterns, add exact origins as patterns too
            List<String> allPatterns = new ArrayList<>(originPatterns);
            allPatterns.addAll(exactOrigins);
            String[] all = allPatterns.toArray(new String[0]);
            apiMapping.allowedOriginPatterns(all);
            oauthMapping.allowedOriginPatterns(all);
        } else {
            String[] exact = exactOrigins.toArray(new String[0]);
            apiMapping.allowedOrigins(exact);
            oauthMapping.allowedOrigins(exact);
        }
    }
}
