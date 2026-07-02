package com.leadboard.tenant;

import com.leadboard.auth.AppRole;
import com.leadboard.auth.UserEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tenant_users", schema = "public")
public class TenantUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private TenantEntity tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "app_role", nullable = false, length = 20)
    private AppRole appRole = AppRole.MEMBER;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * F82: membership lifecycle tied to Jira access. A user may only belong to a tenant
     * while their Atlassian account has access to the tenant's Jira site. When access is
     * lost (e.g. offboarded from Jira), the row is deactivated — never deleted — and
     * reactivated automatically if access is restored.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deactivated_at")
    private OffsetDateTime deactivatedAt;

    @Column(name = "deactivated_reason", length = 255)
    private String deactivatedReason;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TenantEntity getTenant() { return tenant; }
    public void setTenant(TenantEntity tenant) { this.tenant = tenant; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

    public AppRole getAppRole() { return appRole; }
    public void setAppRole(AppRole appRole) { this.appRole = appRole; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getDeactivatedAt() { return deactivatedAt; }
    public void setDeactivatedAt(OffsetDateTime deactivatedAt) { this.deactivatedAt = deactivatedAt; }

    public String getDeactivatedReason() { return deactivatedReason; }
    public void setDeactivatedReason(String deactivatedReason) { this.deactivatedReason = deactivatedReason; }

    /** Deactivates this membership (access lost). Does not delete the row. */
    public void deactivate(String reason) {
        this.active = false;
        this.deactivatedAt = OffsetDateTime.now();
        this.deactivatedReason = reason;
    }

    /** Restores this membership after access was regained. */
    public void reactivate() {
        this.active = true;
        this.deactivatedAt = null;
        this.deactivatedReason = null;
    }
}
