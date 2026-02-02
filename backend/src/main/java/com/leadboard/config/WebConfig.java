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
        // Localhost для разработки
        origins.add("http://localhost:5173");
        origins.add("http://localhost:3000");

        // Production origins из env
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            for (String origin : corsAllowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    origins.add(trimmed);
                }
            }
        }

        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);

        // WebSocket для Planning Poker
        registry.addMapping("/ws/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
