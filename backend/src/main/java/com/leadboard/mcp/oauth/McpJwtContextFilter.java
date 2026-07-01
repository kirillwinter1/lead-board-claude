package com.leadboard.mcp.oauth;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * После валидации JWT (OAuth resource server) восстанавливает {@link TenantContext} и
 * {@link LeadBoardAuthentication} из claims (tenant_id, user_account_id), чтобы RBAC в
 * ChatToolExecutor и tenant-изоляция работали как при обычном входе (F80 Plan 2).
 *
 * <p>contextExtractor транспорта MCP затем перенесёт их в поток выполнения инструмента.</p>
 */
@Component
@ConditionalOnProperty(prefix = "mcp", name = "oauth-enabled", havingValue = "true")
public class McpJwtContextFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;

    public McpJwtContextFilter(UserRepository userRepository,
                               TenantRepository tenantRepository,
                               TenantUserRepository tenantUserRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            // Гейт: /mcp разрешён permitAll на уровне AuthorizationFilter, поэтому отсутствие
            // валидного JWT отклоняем здесь (нет токена → нет JwtAuthenticationToken).
            response.setHeader("WWW-Authenticate", "Bearer realm=\"mcp\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token required");
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String accountId = jwt.getClaimAsString("user_account_id");
        String tenantIdStr = jwt.getClaimAsString("tenant_id");
        if (accountId == null) {
            chain.doFilter(request, response);
            return;
        }

        Optional<UserEntity> user = userRepository.findByAtlassianAccountId(accountId);
        if (user.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown user");
            return;
        }

        Long tenantId = null;
        String schema = null;
        AppRole role = user.get().getAppRole();
        if (tenantIdStr != null) {
            try {
                tenantId = Long.valueOf(tenantIdStr);
            } catch (NumberFormatException ignored) {
                // leave null
            }
        }
        if (tenantId != null) {
            Optional<TenantEntity> tenant = tenantRepository.findById(tenantId);
            if (tenant.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown tenant");
                return;
            }
            schema = tenant.get().getSchemaName();
            // F80 §4.3: отзыв доступа — если пользователь больше не в tenant_users, не аутентифицируем
            Optional<TenantUserEntity> tu = tenantUserRepository.findByTenantIdAndUserId(tenantId, user.get().getId());
            if (tu.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access revoked for tenant");
                return;
            }
            role = tu.get().getAppRole();
        }

        try {
            if (tenantId != null) {
                TenantContext.setTenant(tenantId, schema);
            }
            LeadBoardAuthentication appAuth = new LeadBoardAuthentication(user.get(), tenantId, role);
            SecurityContextHolder.getContext().setAuthentication(appAuth);
            // НЕ очищаем SecurityContext вручную: MCP servlet работает асинхронно (streamable),
            // ручной clearContext после старта async ломает пропагацию auth → 401 на re-dispatch.
            // SecurityContextHolderFilter очистит контекст сам по завершении диспатча.
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
