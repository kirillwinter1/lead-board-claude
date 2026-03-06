package com.leadboard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting filter using token bucket algorithm.
 * Different limits for different endpoint groups:
 * - OAuth/auth: 20 req/min per IP
 * - Sync trigger: 5 req/min per IP
 * - Public registration: 10 req/min per IP
 * - General API: 200 req/min per IP
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5) // Before TenantFilter
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ObservabilityMetrics observabilityMetrics;

    public RateLimitFilter(ObservabilityMetrics observabilityMetrics) {
        this.observabilityMetrics = observabilityMetrics;
    }

    // Bucket configs: requests per window
    private static final int OAUTH_LIMIT = 20;
    private static final int SYNC_LIMIT = 5;
    private static final int REGISTRATION_LIMIT = 10;
    private static final int CHAT_LIMIT = 30;
    private static final long WINDOW_MS = 60_000; // 1 minute

    @Value("${app.rate-limit.general:200}")
    private int generalApiLimit;

    // Cleanup interval: remove stale entries every 5 minutes
    private static final long CLEANUP_INTERVAL_MS = 300_000;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip health check — no rate limiting needed
        if ("/api/health".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip non-API paths (frontend static resources)
        if (!path.startsWith("/api/") && !path.startsWith("/oauth/") && !path.startsWith("/ws/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        int limit = resolveLimit(path);
        String bucketKey = clientIp + ":" + resolveBucketGroup(path);

        cleanupIfNeeded();

        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new TokenBucket(limit));

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for IP {} on path {}", clientIp, path);
            observabilityMetrics.recordRateLimitHit();
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
            response.setHeader("Retry-After", "60");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int resolveLimit(String path) {
        if (path.startsWith("/oauth/")) {
            return OAUTH_LIMIT;
        }
        if (path.startsWith("/api/sync/trigger")) {
            return SYNC_LIMIT;
        }
        if (path.startsWith("/api/public/tenants/register")) {
            return REGISTRATION_LIMIT;
        }
        if (path.startsWith("/api/chat/message")) {
            return CHAT_LIMIT;
        }
        return generalApiLimit;
    }

    private String resolveBucketGroup(String path) {
        if (path.startsWith("/oauth/")) {
            return "oauth";
        }
        if (path.startsWith("/api/sync/trigger")) {
            return "sync";
        }
        if (path.startsWith("/api/public/tenants/register")) {
            return "register";
        }
        if (path.startsWith("/api/chat/message")) {
            return "chat";
        }
        return "api";
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take the first IP (original client)
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        long last = lastCleanup.get();
        if (now - last > CLEANUP_INTERVAL_MS && lastCleanup.compareAndSet(last, now)) {
            buckets.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        }
    }

    /**
     * Simple token bucket with a fixed window.
     */
    public static class TokenBucket {
        private final int maxTokens;
        private final AtomicInteger tokens;
        private final AtomicLong windowStart;

        public TokenBucket(int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = new AtomicInteger(maxTokens);
            this.windowStart = new AtomicLong(System.currentTimeMillis());
        }

        public boolean tryConsume() {
            long now = System.currentTimeMillis();
            long start = windowStart.get();

            // Reset window if expired
            if (now - start >= WINDOW_MS) {
                windowStart.set(now);
                tokens.set(maxTokens);
            }

            // Try to consume a token
            while (true) {
                int current = tokens.get();
                if (current <= 0) {
                    return false;
                }
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        public boolean isExpired(long now) {
            return now - windowStart.get() > WINDOW_MS * 5;
        }
    }
}
