package com.leadboard.auth;

import com.leadboard.config.AppProperties;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;

@RestController
@RequestMapping("/oauth/atlassian")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final OAuthService oauthService;
    private final AppProperties appProperties;
    private final TenantService tenantService;

    public OAuthController(OAuthService oauthService, AppProperties appProperties, TenantService tenantService) {
        this.oauthService = oauthService;
        this.appProperties = appProperties;
        this.tenantService = tenantService;
    }

    @GetMapping("/authorize")
    public void authorize(
            @RequestParam(value = "tenant", required = false) String tenantSlug,
            HttpServletResponse response) throws IOException {
        // Browser navigation doesn't send X-Tenant-Slug header,
        // so tenant slug is passed as query param
        Long tenantId = resolveTenantId(tenantSlug);
        String authUrl = oauthService.getAuthorizationUrl(tenantId);
        response.sendRedirect(authUrl);
    }

    private Long resolveTenantId(String slug) {
        // 1. Try query param
        if (slug != null && !slug.isBlank()) {
            return tenantService.findBySlug(slug.trim()).map(TenantEntity::getId).orElse(null);
        }
        // 2. Fall back to TenantContext (set by TenantFilter from subdomain/header)
        return com.leadboard.tenant.TenantContext.getCurrentTenantId();
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpServletResponse response) throws IOException {

        OAuthService.CallbackResult result = oauthService.handleCallback(code, state);

        String redirectUrl = buildTenantRedirectUrl(result.tenantId());

        if (result.success()) {
            addSessionCookie(response, result.sessionId());
            response.sendRedirect(redirectUrl + "/?auth=success");
        } else {
            log.warn("OAuth callback failed: {}", result.error());
            // Don't expose internal error details in URL — use generic error code
            response.sendRedirect(redirectUrl + "/?auth=error");
        }
    }

    @GetMapping("/status")
    public ResponseEntity<OAuthService.AuthStatus> getStatus() {
        return ResponseEntity.ok(oauthService.getAuthStatus());
    }

    @GetMapping("/login-url")
    public ResponseEntity<LoginUrlResponse> getLoginUrl() {
        String url = oauthService.getAuthorizationUrl(null);
        return ResponseEntity.ok(new LoginUrlResponse(url));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = extractSessionId(request);
        oauthService.logout(sessionId);
        clearSessionCookie(response);
        return ResponseEntity.ok().build();
    }

    /**
     * Returns the configured frontend URL for redirect after OAuth callback.
     * Tenant context is handled by the frontend (localStorage / subdomain).
     */
    private String buildTenantRedirectUrl(Long tenantId) {
        return appProperties.getFrontendUrl();
    }

    private void addSessionCookie(HttpServletResponse response, String sessionId) {
        String cookieName = appProperties.getSession().getCookieName();
        int maxAgeSeconds = appProperties.getSession().getMaxAgeDays() * 24 * 60 * 60;
        String cookieDomain = appProperties.getSession().getCookieDomain();

        Cookie cookie = new Cookie(cookieName, sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(appProperties.getSession().isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }
        response.addCookie(cookie);
    }

    private void clearSessionCookie(HttpServletResponse response) {
        String cookieName = appProperties.getSession().getCookieName();
        String cookieDomain = appProperties.getSession().getCookieDomain();

        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }
        response.addCookie(cookie);
    }

    private String extractSessionId(HttpServletRequest request) {
        String cookieName = appProperties.getSession().getCookieName();
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public record LoginUrlResponse(String url) {}
}
