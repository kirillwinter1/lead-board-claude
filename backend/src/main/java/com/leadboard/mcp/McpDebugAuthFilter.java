package com.leadboard.mcp;

import com.leadboard.auth.AppRole;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.auth.UserEntity;
import com.leadboard.auth.UserRepository;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserEntity;
import com.leadboard.tenant.TenantUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Фаза 1 (отладка / личное использование): аутентификация MCP по статическому
 * bearer-токену из конфига. Резолвит фиксированного пользователя + tenant и ставит
 * {@link LeadBoardAuthentication} + {@link TenantContext}, чтобы RBAC в
 * {@code ChatToolExecutor} и tenant-изоляция работали.
 *
 * <p>ВНИМАНИЕ: только для локального ПК через Claude Code CLI / MCP Inspector.
 * Для claude.ai / телефона нужен OAuth 2.1 (План 2).</p>
 */
@Component
@ConditionalOnProperty(prefix = "mcp", name = "enabled", havingValue = "true")
public class McpDebugAuthFilter extends OncePerRequestFilter {

    private final McpProperties props;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;

    public McpDebugAuthFilter(McpProperties props,
                              UserRepository userRepository,
                              TenantRepository tenantRepository,
                              TenantUserRepository tenantUserRepository) {
        this.props = props;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // В OAuth-режиме /mcp защищает mcpResourceChain (JWT); debug-фильтр не вмешивается
        // (иначе перехватывает async re-dispatch и отвечает 401).
        return props.isOauthEnabled() || !request.getRequestURI().startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!props.isEnabled() || props.getDebugToken().isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MCP disabled");
            return;
        }

        String token = bearer(request);
        if (token == null || !constantTimeEquals(token, props.getDebugToken())) {
            response.setHeader("WWW-Authenticate", "Bearer realm=\"mcp\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }

        Optional<TenantEntity> tenant = tenantRepository.findBySlug(props.getDebugTenantSlug());
        Optional<UserEntity> user = userRepository.findByAtlassianAccountId(props.getDebugAccountId());
        if (tenant.isEmpty() || user.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Debug principal not found");
            return;
        }

        Long tenantId = tenant.get().getId();
        Optional<TenantUserEntity> tu = tenantUserRepository.findByTenantIdAndUserId(tenantId, user.get().getId());
        AppRole role = tu.map(TenantUserEntity::getAppRole).orElse(AppRole.MEMBER);

        try {
            TenantContext.setTenant(tenantId, tenant.get().getSchemaName());
            LeadBoardAuthentication auth = new LeadBoardAuthentication(user.get(), tenantId, role);
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private String bearer(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            return h.substring(7).trim();
        }
        return null;
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
