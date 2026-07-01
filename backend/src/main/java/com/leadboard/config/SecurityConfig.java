package com.leadboard.config;

import com.leadboard.auth.LeadBoardAuthenticationFilter;
import com.leadboard.mcp.McpDebugAuthFilter;
import com.leadboard.tenant.TenantFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final LeadBoardAuthenticationFilter authenticationFilter;
    private final TenantFilter tenantFilter;
    private final ObjectProvider<McpDebugAuthFilter> mcpDebugAuthFilter;

    public SecurityConfig(LeadBoardAuthenticationFilter authenticationFilter, TenantFilter tenantFilter,
                          ObjectProvider<McpDebugAuthFilter> mcpDebugAuthFilter) {
        this.authenticationFilter = authenticationFilter;
        this.tenantFilter = tenantFilter;
        this.mcpDebugAuthFilter = mcpDebugAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for API endpoints (we use token-based auth)
            .csrf(csrf -> csrf.disable())

            // Disable session management - we use OAuth tokens
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Add tenant filter first, then auth filter
            .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(authenticationFilter, TenantFilter.class);

        // MCP bearer auth (F80) — present only when mcp.enabled=true; runs after
        // session auth, only touches /mcp, sets tenant + LeadBoardAuthentication.
        McpDebugAuthFilter mcpFilter = mcpDebugAuthFilter.getIfAvailable();
        if (mcpFilter != null) {
            http.addFilterAfter(mcpFilter, LeadBoardAuthenticationFilter.class);
        }

        http

            // Configure authorization
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - OAuth flow
                .requestMatchers("/oauth/atlassian/**").permitAll()

                // Public tenant registration
                .requestMatchers("/api/public/**").permitAll()

                // Health check
                .requestMatchers("/api/health").permitAll()

                // Config endpoints
                .requestMatchers("/api/config").permitAll()
                .requestMatchers("/api/config/workflow/**").permitAll()

                // Chat status (public - widget needs to know if chat is enabled)
                .requestMatchers("/api/chat/status").permitAll()

                // WebSocket endpoint for Poker
                .requestMatchers("/ws/**").permitAll()

                // MCP endpoint (F80) — access controlled by McpDebugAuthFilter (bearer), not session
                .requestMatchers("/mcp", "/mcp/**").permitAll()

                // Issue types are needed by every authenticated user — board,
                // planning and metrics pages all resolve icons via this endpoint.
                // Other jira-metadata endpoints (statuses, link-types, priorities,
                // custom-fields) are config/admin tooling and fall through to the
                // ADMIN-only rule below. Must come BEFORE the generic /api/admin/**
                // rule so the ADMIN-only guard doesn't shadow this carve-out.
                .requestMatchers(HttpMethod.GET, "/api/admin/jira-metadata/issue-types").authenticated()

                // Admin endpoints - require ADMIN role
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Sync trigger - require ADMIN role
                .requestMatchers(HttpMethod.POST, "/api/sync/trigger").hasRole("ADMIN")

                // Simulation - require ADMIN role
                .requestMatchers("/api/simulation/**").hasRole("ADMIN")

                // All other API endpoints - require authentication
                .requestMatchers("/api/**").authenticated()

                // Allow all other requests (frontend resources)
                .anyRequest().permitAll()
            )

            // Return 401 JSON instead of redirect to login form
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            );

        return http.build();
    }
}
