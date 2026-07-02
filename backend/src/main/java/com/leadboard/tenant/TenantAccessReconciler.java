package com.leadboard.tenant;

import com.leadboard.auth.OAuthService;
import com.leadboard.auth.OAuthTokenEntity;
import com.leadboard.auth.OAuthTokenRepository;
import com.leadboard.auth.UserEntity;
import com.leadboard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * F82: periodically re-verifies that every active {@code tenant_users} membership still has
 * Jira access to its tenant's Jira site (cloudId). Membership is tied to Jira access (see
 * ai-ru/features/F82_JIRA_ACCESS_MEMBERSHIP.md, ai-ru/SECURITY_AUDIT.md §2): when an
 * Atlassian account loses access to the site (offboarded employee), the corresponding
 * {@code tenant_users} row is deactivated — never deleted — here.
 *
 * <p>{@link com.leadboard.auth.OAuthService#applyMembershipGate} already reconciles a single
 * user on every login. This job closes the gap for users who simply stop logging in after
 * being offboarded in Jira — their session/role would otherwise remain valid indefinitely.</p>
 *
 * <p>Modeled after {@link TenantSyncScheduler}: a cheap fixed-delay tick checks whether a
 * full pass is due (per {@code app.access-reconcile.interval-seconds}), and sets/clears
 * {@link TenantContext} around each tenant's lookup exactly like the sync scheduler does.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.access-reconcile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TenantAccessReconciler {

    private static final Logger log = LoggerFactory.getLogger(TenantAccessReconciler.class);
    private static final int DEFAULT_INTERVAL_SECONDS = 4 * 60 * 60; // 4 hours
    private static final long POLL_DELAY_MS = 60_000;

    private final TenantUserRepository tenantUserRepository;
    private final TenantJiraConfigRepository jiraConfigRepository;
    private final TenantService tenantService;
    private final OAuthService oauthService;
    private final OAuthTokenRepository oauthTokenRepository;
    private final AppProperties appProperties;

    private volatile Instant nextRunAt = Instant.EPOCH;

    public TenantAccessReconciler(TenantUserRepository tenantUserRepository,
                                   TenantJiraConfigRepository jiraConfigRepository,
                                   TenantService tenantService,
                                   OAuthService oauthService,
                                   OAuthTokenRepository oauthTokenRepository,
                                   AppProperties appProperties) {
        this.tenantUserRepository = tenantUserRepository;
        this.jiraConfigRepository = jiraConfigRepository;
        this.tenantService = tenantService;
        this.oauthService = oauthService;
        this.oauthTokenRepository = oauthTokenRepository;
        this.appProperties = appProperties;
    }

    /**
     * Cheap tick (every 60s) that only triggers a full {@link #reconcile()} pass once the
     * configured interval has elapsed — mirrors {@code TenantSyncScheduler.isSyncDue}.
     */
    @Scheduled(fixedDelay = POLL_DELAY_MS)
    public void scheduledReconcile() {
        Instant now = Instant.now();
        if (now.isBefore(nextRunAt)) {
            return;
        }
        int intervalSeconds = appProperties.getAccessReconcile().getIntervalSeconds();
        nextRunAt = now.plusSeconds(intervalSeconds > 0 ? intervalSeconds : DEFAULT_INTERVAL_SECONDS);
        reconcile();
    }

    /**
     * Runs one full reconciliation pass over every active {@code tenant_users} row. Public so
     * it can be triggered directly (tests, ops) without waiting for the scheduler tick.
     */
    public void reconcile() {
        List<TenantUserEntity> activeMemberships = tenantUserRepository.findAllByActiveTrue();
        if (activeMemberships.isEmpty()) {
            log.debug("Access reconcile: no active tenant_users to check");
            return;
        }

        int checked = 0;
        int deactivated = 0;
        int skippedNoToken = 0;
        int skippedNoCloudId = 0;

        for (TenantUserEntity membership : activeMemberships) {
            TenantEntity tenant = membership.getTenant();
            UserEntity user = membership.getUser();
            try {
                TenantContext.setTenant(tenant.getId(), tenant.getSchemaName());

                String tenantCloudId = jiraConfigRepository.findActive()
                        .map(TenantJiraConfigEntity::getJiraCloudId)
                        .orElse(null);
                if (tenantCloudId == null || tenantCloudId.isBlank()) {
                    // Can't verify without a configured Jira site — do not touch membership.
                    skippedNoCloudId++;
                    continue;
                }

                Optional<OAuthTokenEntity> tokenEntity =
                        oauthTokenRepository.findByAtlassianAccountId(user.getAtlassianAccountId());
                if (tokenEntity.isEmpty()) {
                    // Never logged in via OAuth (or no stored token) — cannot verify, skip.
                    skippedNoToken++;
                    continue;
                }

                checked++;
                OAuthService.TokenInfo tokenInfo = oauthService.getValidAccessTokenForUser(user.getAtlassianAccountId());
                if (tokenInfo == null || tokenInfo.accessToken() == null) {
                    // Token existed but refresh/validation failed — Atlassian revoked it
                    // (e.g. offboarded employee) or the refresh token is no longer valid.
                    tenantService.deactivateMembership(membership, "jira_access_lost");
                    deactivated++;
                    log.info("Deactivated membership: user {} — token refresh/validation failed for tenant '{}'",
                            user.getId(), tenant.getSlug());
                    continue;
                }

                boolean hasAccess = oauthService.userHasJiraAccess(tokenInfo.accessToken(), tenantCloudId);
                if (!hasAccess) {
                    tenantService.deactivateMembership(membership, "jira_access_lost");
                    deactivated++;
                    log.info("Deactivated membership: user {} lost Jira access to tenant '{}' (cloudId {})",
                            user.getId(), tenant.getSlug(), tenantCloudId);
                }
            } catch (Exception e) {
                log.error("Access reconcile failed for user {} in tenant '{}': {}",
                        user.getId(), tenant.getSlug(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }

        log.info("Tenant access reconcile complete: total={}, checked={}, deactivated={}, skippedNoToken={}, skippedNoCloudId={}",
                activeMemberships.size(), checked, deactivated, skippedNoToken, skippedNoCloudId);
    }
}
