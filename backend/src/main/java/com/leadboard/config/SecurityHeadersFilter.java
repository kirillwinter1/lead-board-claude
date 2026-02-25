package com.leadboard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds security headers to all API responses.
 * These headers protect against clickjacking, MIME sniffing, and other attacks.
 * Works both behind nginx (redundant but safe) and directly (dev mode).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Prevent clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // Prevent MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Referrer policy — don't leak full URL to external sites
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Disable unnecessary browser features
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");

        // Prevent caching of API responses with sensitive data
        String path = request.getRequestURI();
        if (path.startsWith("/api/") || path.startsWith("/oauth/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }
}
