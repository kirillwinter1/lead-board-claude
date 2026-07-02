package com.leadboard.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the CORS origin parsing/validation logic in {@link WebConfig#parseOrigins}.
 *
 * <p>Regression test for the "bare `*` + credentials -> Origin reflection" MEDIUM finding
 * (SECURITY_AUDIT.md #8): /api/** and /oauth/** are registered with allowCredentials(true), so a
 * bare "*" must never reach allowedOriginPatterns(...) — Spring would otherwise reflect back
 * whatever Origin header the caller sends, alongside Access-Control-Allow-Credentials: true.
 */
class WebConfigOriginParsingTest {

    private static final List<String> DEV_ORIGINS = List.of(
            "http://localhost:5173",
            "http://localhost:3000"
    );

    @Test
    void bareWildcardIsRejectedAndNotForwardedAsPattern() {
        WebConfig.ParsedOrigins parsed = WebConfig.parseOrigins("*", DEV_ORIGINS);

        assertTrue(parsed.rejectedBareWildcard(), "bare '*' must be flagged as rejected");
        assertTrue(parsed.originPatterns().isEmpty(), "bare '*' must not become an origin pattern");
        assertEquals(DEV_ORIGINS, parsed.exactOrigins(), "only the base dev origins should remain");
    }

    @Test
    void wildcardOnlyEntryIsRejectedEvenWithMultipleAsterisks() {
        WebConfig.ParsedOrigins parsed = WebConfig.parseOrigins("**", DEV_ORIGINS);

        assertTrue(parsed.rejectedBareWildcard());
        assertTrue(parsed.originPatterns().isEmpty());
    }

    @Test
    void bareWildcardMixedWithLegitimateOriginsIsDroppedButOthersSurvive() {
        WebConfig.ParsedOrigins parsed = WebConfig.parseOrigins(
                "https://app.onelane.ru, *, https://*.onelane.ru", DEV_ORIGINS);

        assertTrue(parsed.rejectedBareWildcard());
        assertTrue(parsed.exactOrigins().contains("https://app.onelane.ru"));
        assertEquals(List.of("https://*.onelane.ru"), parsed.originPatterns());
        // the bare "*" itself must not leak into either list
        assertFalse(parsed.exactOrigins().contains("*"));
        assertFalse(parsed.originPatterns().contains("*"));
    }

    @Test
    void scopedSubdomainPatternIsAcceptedAsPattern() {
        WebConfig.ParsedOrigins parsed = WebConfig.parseOrigins("https://*.onelane.ru", DEV_ORIGINS);

        assertFalse(parsed.rejectedBareWildcard());
        assertEquals(List.of("https://*.onelane.ru"), parsed.originPatterns());
        assertEquals(DEV_ORIGINS, parsed.exactOrigins());
    }

    @Test
    void specificOriginsAreAcceptedAsExactOrigins() {
        WebConfig.ParsedOrigins parsed = WebConfig.parseOrigins(
                "https://app.onelane.ru,https://admin.onelane.ru", DEV_ORIGINS);

        assertFalse(parsed.rejectedBareWildcard());
        assertTrue(parsed.originPatterns().isEmpty());
        assertTrue(parsed.exactOrigins().containsAll(List.of(
                "https://app.onelane.ru", "https://admin.onelane.ru")));
        // base dev origins are preserved alongside the configured ones
        assertTrue(parsed.exactOrigins().containsAll(DEV_ORIGINS));
    }

    @Test
    void blankOrEmptyConfigOnlyYieldsBaseOrigins() {
        WebConfig.ParsedOrigins parsedNull = WebConfig.parseOrigins(null, DEV_ORIGINS);
        WebConfig.ParsedOrigins parsedBlank = WebConfig.parseOrigins("   ", DEV_ORIGINS);
        WebConfig.ParsedOrigins parsedEmptyEntries = WebConfig.parseOrigins(" , ,", DEV_ORIGINS);

        for (WebConfig.ParsedOrigins parsed : List.of(parsedNull, parsedBlank, parsedEmptyEntries)) {
            assertFalse(parsed.rejectedBareWildcard());
            assertTrue(parsed.originPatterns().isEmpty());
            assertEquals(DEV_ORIGINS, parsed.exactOrigins());
        }
    }

    @Test
    void whitespaceAroundBareWildcardIsStillRejected() {
        WebConfig.ParsedOrigins parsed = WebConfig.parseOrigins("  *  ", DEV_ORIGINS);

        assertTrue(parsed.rejectedBareWildcard());
        assertTrue(parsed.originPatterns().isEmpty());
    }
}
