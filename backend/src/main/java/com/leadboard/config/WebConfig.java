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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>();
        List<String> patterns = new ArrayList<>();

        // Localhost для разработки
        origins.add("http://localhost:5173");
        origins.add("http://localhost:3000");

        // Production origins из env (поддержка wildcards: https://*.onelane.ru)
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            for (String origin : corsAllowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.contains("*")) {
                        patterns.add(trimmed);
                    } else {
                        origins.add(trimmed);
                    }
                }
            }
        }

        configureCors(registry.addMapping("/api/**"), origins, patterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);

        configureCors(registry.addMapping("/oauth/**"), origins, patterns)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);

        configureCors(registry.addMapping("/ws/**"), origins, patterns)
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    private org.springframework.web.servlet.config.annotation.CorsRegistration configureCors(
            org.springframework.web.servlet.config.annotation.CorsRegistration registration,
            List<String> origins, List<String> patterns) {
        if (!origins.isEmpty()) {
            registration.allowedOrigins(origins.toArray(new String[0]));
        }
        if (!patterns.isEmpty()) {
            registration.allowedOriginPatterns(patterns.toArray(new String[0]));
        }
        return registration;
    }
}
