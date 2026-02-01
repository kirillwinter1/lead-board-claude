package com.leadboard.config;

import com.leadboard.auth.LeadBoardAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final LeadBoardAuthenticationFilter authenticationFilter;

    public SecurityConfig(LeadBoardAuthenticationFilter authenticationFilter) {
        this.authenticationFilter = authenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for API endpoints (we use token-based auth)
            .csrf(csrf -> csrf.disable())

            // Disable session management - we use OAuth tokens
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Add our custom auth filter before the default one
            .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // Configure authorization
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - OAuth flow
                .requestMatchers("/oauth/atlassian/**").permitAll()

                // Health check
                .requestMatchers("/api/health").permitAll()

                // Config endpoint
                .requestMatchers("/api/config").permitAll()

                // WebSocket endpoint for Poker
                .requestMatchers("/ws/**").permitAll()

                // Admin endpoints - require ADMIN role
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Sync trigger - require ADMIN role
                .requestMatchers(HttpMethod.POST, "/api/sync/trigger").hasRole("ADMIN")

                // All other API endpoints - allow authenticated users
                .requestMatchers("/api/**").permitAll()

                // Allow all other requests (frontend resources)
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
