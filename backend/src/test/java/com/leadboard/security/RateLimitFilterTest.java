package com.leadboard.security;

import com.leadboard.config.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    @Test
    @DisplayName("Token bucket allows requests within limit")
    void tokenBucket_withinLimit() {
        var bucket = new RateLimitFilter.TokenBucket(5);
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Token bucket rejects requests exceeding limit")
    void tokenBucket_exceedsLimit() {
        var bucket = new RateLimitFilter.TokenBucket(3);
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume(), "4th request should be rejected");
        assertFalse(bucket.tryConsume(), "5th request should be rejected");
    }

    @Test
    @DisplayName("Token bucket with limit 1 allows only one request")
    void tokenBucket_limitOne() {
        var bucket = new RateLimitFilter.TokenBucket(1);
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume());
    }

    @Test
    @DisplayName("Bucket not expired within 5 windows")
    void tokenBucket_notExpired() {
        var bucket = new RateLimitFilter.TokenBucket(10);
        assertFalse(bucket.isExpired(System.currentTimeMillis()));
    }

    @Test
    @DisplayName("Bucket expired after 5 windows")
    void tokenBucket_expired() {
        var bucket = new RateLimitFilter.TokenBucket(10);
        // 5 * 60_000 = 300_000ms + 1
        assertTrue(bucket.isExpired(System.currentTimeMillis() + 300_001));
    }
}
