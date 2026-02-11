package com.leadboard.auth;

import com.leadboard.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;

@RestController
@RequestMapping("/oauth/atlassian")
public class OAuthController {

    private final OAuthService oauthService;
    private final AppProperties appProperties;

    public OAuthController(OAuthService oauthService, AppProperties appProperties) {
        this.oauthService = oauthService;
        this.appProperties = appProperties;
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

        String frontendUrl = appProperties.getFrontendUrl();

        if (result.success()) {
            addSessionCookie(response, result.sessionId());
            response.sendRedirect(frontendUrl + "/?auth=success");
        } else {
            response.sendRedirect(frontendUrl + "/?auth=error&message=" + result.error());
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

    private void addSessionCookie(HttpServletResponse response, String sessionId) {
        String cookieName = appProperties.getSession().getCookieName();
        int maxAgeSeconds = appProperties.getSession().getMaxAgeDays() * 24 * 60 * 60;

        Cookie cookie = new Cookie(cookieName, sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private void clearSessionCookie(HttpServletResponse response) {
        String cookieName = appProperties.getSession().getCookieName();

        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
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
