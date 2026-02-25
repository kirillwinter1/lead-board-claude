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
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

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
    public void authorize(HttpServletResponse response) throws IOException {
        String authUrl = oauthService.getAuthorizationUrl();
        response.sendRedirect(authUrl);
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
            response.sendRedirect(redirectUrl + "/?auth=error&message=" + result.error());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<OAuthService.AuthStatus> getStatus() {
        return ResponseEntity.ok(oauthService.getAuthStatus());
    }

    @GetMapping("/login-url")
    public ResponseEntity<LoginUrlResponse> getLoginUrl() {
        String url = oauthService.getAuthorizationUrl();
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
     * Build redirect URL for the tenant's subdomain.
     * If tenantId is present, redirects to https://{slug}.{baseDomain}
     * Otherwise falls back to the configured frontendUrl.
     */
    private String buildTenantRedirectUrl(Long tenantId) {
        String frontendUrl = appProperties.getFrontendUrl();

        if (tenantId == null) {
            return frontendUrl;
        }

        Optional<TenantEntity> tenantOpt = tenantService.findById(tenantId);
        if (tenantOpt.isEmpty()) {
            return frontendUrl;
        }

        String slug = tenantOpt.get().getSlug();
        try {
            URI base = URI.create(frontendUrl);
            String host = base.getHost();
            // Construct subdomain URL: https://slug.onelane.ru
            String tenantHost = slug + "." + host;
            String tenantUrl = base.getScheme() + "://" + tenantHost;
            log.info("Tenant redirect URL: {}", tenantUrl);
            return tenantUrl;
        } catch (Exception e) {
            log.warn("Failed to build tenant redirect URL for slug '{}', falling back to frontendUrl", slug, e);
            return frontendUrl;
        }
    }

    private void addSessionCookie(HttpServletResponse response, String sessionId) {
        String cookieName = appProperties.getSession().getCookieName();
        int maxAgeSeconds = appProperties.getSession().getMaxAgeDays() * 24 * 60 * 60;
        String cookieDomain = appProperties.getSession().getCookieDomain();

        Cookie cookie = new Cookie(cookieName, sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
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
