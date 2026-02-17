package com.leadboard.auth;

import java.util.Set;

/**
 * Application roles for RBAC.
 */
public enum AppRole {
    ADMIN,           // Full access, user management, settings
    PROJECT_MANAGER, // Manage projects, RICE, view board/timeline
    TEAM_LEAD,       // Manage own team, change priorities
    MEMBER,          // View, participate in Poker, own metrics
    VIEWER;          // Read-only (PM/Product)

    /**
     * Get permissions associated with this role.
     */
    public Set<String> getPermissions() {
        return switch (this) {
            case ADMIN -> Set.of(
                    "teams:manage",
                    "priorities:edit",
                    "board:view",
                    "poker:participate",
                    "sync:trigger",
                    "admin:access"
            );
            case PROJECT_MANAGER -> Set.of(
                    "projects:manage",
                    "board:view",
                    "poker:participate"
            );
            case TEAM_LEAD -> Set.of(
                    "teams:manage:own",
                    "priorities:edit",
                    "board:view",
                    "poker:participate"
            );
            case MEMBER -> Set.of(
                    "board:view",
                    "poker:participate"
            );
            case VIEWER -> Set.of(
                    "board:view"
            );
        };
    }

    /**
     * Check if this role has a specific permission.
     */
    public boolean hasPermission(String permission) {
        return getPermissions().contains(permission);
    }
}
