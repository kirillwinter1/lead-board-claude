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

    public LeadBoardAuthentication(UserEntity user) {
        this.user = user;
        this.authorities = user.getAppRole().getPermissions().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        // Add role as authority
        this.authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getAppRole().name()));
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
        return user.getAppRole();
    }

    public Long getUserId() {
        return user.getId();
    }

    public String getAtlassianAccountId() {
        return user.getAtlassianAccountId();
    }

    public boolean hasPermission(String permission) {
        return user.getAppRole().hasPermission(permission);
    }
}
