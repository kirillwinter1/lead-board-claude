package com.leadboard.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom Authentication principal for Lead Board.
 */
public class LeadBoardAuthentication implements Authentication {

    private final UserEntity user;
    private final Set<GrantedAuthority> authorities;
    private boolean authenticated = true;
    private final Long tenantId;
    private final AppRole tenantRole;

    public LeadBoardAuthentication(UserEntity user) {
        this(user, null, user.getAppRole());
    }

    public LeadBoardAuthentication(UserEntity user, Long tenantId, AppRole tenantRole) {
        this.user = user;
        this.tenantId = tenantId;
        this.tenantRole = tenantRole != null ? tenantRole : user.getAppRole();
        this.authorities = this.tenantRole.getPermissions().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        // Add role as authority
        this.authorities.add(new SimpleGrantedAuthority("ROLE_" + this.tenantRole.name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return user;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return user.getDisplayName();
    }

    public UserEntity getUser() {
        return user;
    }

    public AppRole getRole() {
        return tenantRole;
    }

    public Long getUserId() {
        return user.getId();
    }

    public String getAtlassianAccountId() {
        return user.getAtlassianAccountId();
    }

    public boolean hasPermission(String permission) {
        return tenantRole.hasPermission(permission);
    }

    public Long getTenantId() {
        return tenantId;
    }

    public AppRole getTenantRole() {
        return tenantRole;
    }
}
