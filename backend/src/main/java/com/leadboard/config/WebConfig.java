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
        List<String> origins = new ArrayList<>();

        // Localhost for development
        origins.add("http://localhost:5173");
        origins.add("http://localhost:3000");

        // Production origins from env — explicit origins only, no wildcards
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            for (String origin : corsAllowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    origins.add(trimmed);
                }
            }
        }

        String[] originsArray = origins.toArray(new String[0]);
        String[] headersArray = ALLOWED_HEADERS.toArray(new String[0]);

        registry.addMapping("/api/**")
                .allowedOrigins(originsArray)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders(headersArray)
                .allowCredentials(true)
                .maxAge(3600);

        registry.addMapping("/oauth/**")
                .allowedOrigins(originsArray)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders(headersArray)
                .allowCredentials(true)
                .maxAge(3600);
    }
}
