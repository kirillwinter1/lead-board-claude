package com.leadboard.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/oauth/atlassian")
public class OAuthController {

    private final OAuthService oauthService;

    public OAuthController(OAuthService oauthService) {
        this.oauthService = oauthService;
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

        OAuthService.AuthResult result = oauthService.handleCallback(code, state);

        // Redirect to frontend (port 5173 in dev)
        String frontendUrl = "http://localhost:5173";

        if (result.success()) {
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
    public ResponseEntity<Void> logout() {
        oauthService.logout();
        return ResponseEntity.ok().build();
    }

    public record LoginUrlResponse(String url) {}
}
