package com.leadboard.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter that extracts user from OAuth token and sets up SecurityContext.
 */
@Component
public class LeadBoardAuthenticationFilter extends OncePerRequestFilter {

    private final OAuthTokenRepository tokenRepository;

    public LeadBoardAuthenticationFilter(OAuthTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Get the latest OAuth token (single-user mode for now)
        Optional<OAuthTokenEntity> tokenOpt = tokenRepository.findLatestToken();

        if (tokenOpt.isPresent()) {
            OAuthTokenEntity token = tokenOpt.get();
            UserEntity user = token.getUser();

            if (user != null) {
                LeadBoardAuthentication auth = new LeadBoardAuthentication(user);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
