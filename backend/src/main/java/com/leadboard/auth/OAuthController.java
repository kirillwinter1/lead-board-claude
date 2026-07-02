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

    // SECURITY_AUDIT.md §7: binds the OAuth `state` to the browser that started the flow, on
    // top of the existing server-side (exists/not-expired) check in OAuthService. Local
    // constant — not in AppProperties, this isn't operator-configurable.
    private static final String STATE_COOKIE_NAME = "oauth_state";
    private static final String STATE_COOKIE_PATH = "/oauth/atlassian";

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
        OAuthService.AuthorizationRequest authRequest = oauthService.createAuthorizationRequest(tenantId);
        addStateCookie(response, authRequest.state());
        response.sendRedirect(authRequest.url());
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
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // SECURITY_AUDIT.md §7 — login CSRF / session fixation: `state` alone (checked
        // server-side in OAuthService for existence/expiry) doesn't prove this callback is
        // being followed by the browser that started the flow. An attacker could start the
        // flow themselves and hand the victim their own callback URL, logging the victim into
        // the attacker's account. Bind `state` to the browser via a short-lived HttpOnly
        // cookie set on /authorize (and /login-url) and require it to match here. One-time use
        // — always clear it, whether or not it matches.
        String cookieState = extractStateCookie(request);
        clearStateCookie(response);
        if (cookieState == null || !cookieState.equals(state)) {
            log.warn("OAuth callback rejected: state cookie missing or mismatched (possible login CSRF)");
            response.sendRedirect(buildTenantRedirectUrl(null) + "/?auth=error&reason=state_mismatch");
            return;
        }

        OAuthService.CallbackResult result = oauthService.handleCallback(code, state);

        String redirectUrl = buildTenantRedirectUrl(result.tenantId());

        if (result.success()) {
            addSessionCookie(response, result.sessionId());
            response.sendRedirect(redirectUrl + "/?auth=success");
        } else {
            log.warn("OAuth callback failed: {}", result.error());
            // Don't expose internal error details in URL — use a generic error code, plus an
            // optional machine-readable `reason` (e.g. F82 "jira_access_denied") the frontend
            // can use to show a more specific message. Existing failure paths pass no
            // errorCode, so their redirect is unchanged (?auth=error).
            String redirect = redirectUrl + "/?auth=error";
            if (result.errorCode() != null) {
                redirect += "&reason=" + result.errorCode();
            }
            response.sendRedirect(redirect);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<OAuthService.AuthStatus> getStatus() {
        return ResponseEntity.ok(oauthService.getAuthStatus());
    }

    @GetMapping("/login-url")
    public ResponseEntity<LoginUrlResponse> getLoginUrl(HttpServletResponse response) {
        // Also starts a flow (frontend then navigates the browser to the returned URL), so it
        // needs the same state-cookie binding as /authorize.
        OAuthService.AuthorizationRequest authRequest = oauthService.createAuthorizationRequest(null);
        addStateCookie(response, authRequest.state());
        return ResponseEntity.ok(new LoginUrlResponse(authRequest.url()));
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

    private void addStateCookie(HttpServletResponse response, String state) {
        Cookie cookie = new Cookie(STATE_COOKIE_NAME, state);
        cookie.setHttpOnly(true);
        cookie.setSecure(appProperties.getSession().isCookieSecure());
        cookie.setPath(STATE_COOKIE_PATH);
        cookie.setMaxAge(OAuthService.STATE_TTL_SECONDS);
        cookie.setAttribute("SameSite", "Lax");
        String cookieDomain = appProperties.getSession().getCookieDomain();
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }
        response.addCookie(cookie);
    }

    private void clearStateCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(STATE_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(appProperties.getSession().isCookieSecure());
        cookie.setPath(STATE_COOKIE_PATH);
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        String cookieDomain = appProperties.getSession().getCookieDomain();
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }
        response.addCookie(cookie);
    }

    private String extractStateCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> STATE_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
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
