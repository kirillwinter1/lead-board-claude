package com.leadboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    private static final List<String> ALLOWED_HEADERS = List.of(
            "Content-Type", "Authorization", "X-Requested-With",
            "X-Tenant-Slug", "Accept", "Origin"
    );

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> exactOrigins = new ArrayList<>();
        List<String> originPatterns = new ArrayList<>();

        // Localhost for development
        exactOrigins.add("http://localhost:5173");
        exactOrigins.add("http://localhost:3000");

        // Production origins from env
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            for (String origin : corsAllowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.contains("*")) {
                        // Wildcards require allowedOriginPatterns (e.g. https://*.onelane.ru)
                        originPatterns.add(trimmed);
                    } else {
                        exactOrigins.add(trimmed);
                    }
                }
            }
        }

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
            String[] patterns = originPatterns.toArray(new String[0]);
            String[] exact = exactOrigins.toArray(new String[0]);
            // When using patterns, add exact origins as patterns too
            List<String> allPatterns = new ArrayList<>(originPatterns);
            exactOrigins.forEach(allPatterns::add);
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
