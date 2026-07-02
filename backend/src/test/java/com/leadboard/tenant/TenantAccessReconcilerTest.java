package com.leadboard.tenant;

import com.leadboard.auth.OAuthService;
import com.leadboard.auth.OAuthTokenEntity;
import com.leadboard.auth.OAuthTokenRepository;
import com.leadboard.auth.UserEntity;
import com.leadboard.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantAccessReconcilerTest {

    private static final String TENANT_CLOUD_ID = "cloud-tenant-1";

    @Mock
    private TenantUserRepository tenantUserRepository;

    @Mock
    private TenantJiraConfigRepository jiraConfigRepository;

    @Mock
    private TenantService tenantService;

    @Mock
    private OAuthService oauthService;

    @Mock
    private OAuthTokenRepository oauthTokenRepository;

    private TenantAccessReconciler reconciler;

    private TenantEntity tenant;
    private UserEntity user;
    private TenantUserEntity membership;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        reconciler = new TenantAccessReconciler(tenantUserRepository, jiraConfigRepository, tenantService,
                oauthService, oauthTokenRepository, appProperties);

        tenant = new TenantEntity();
        tenant.setId(1L);
        tenant.setSlug("acme");
        tenant.setSchemaName("tenant_acme");

        user = new UserEntity();
        user.setId(42L);
        user.setAtlassianAccountId("acc-42");

        membership = new TenantUserEntity();
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setActive(true);

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void mockTenantCloudId(String cloudId) {
        TenantJiraConfigEntity config = new TenantJiraConfigEntity();
        config.setJiraCloudId(cloudId);
        when(jiraConfigRepository.findActive()).thenReturn(Optional.of(config));
    }

    @Test
    @DisplayName("no active memberships — no-op, nothing checked")
    void shouldDoNothingWhenNoActiveMemberships() {
        when(tenantUserRepository.findAllByActiveTrue()).thenReturn(List.of());

        reconciler.reconcile();

        verifyNoInteractions(jiraConfigRepository, oauthService, oauthTokenRepository, tenantService);
    }

    @Test
    @DisplayName("tenant without configured cloudId — skipped, membership untouched")
    void shouldSkipWhenTenantHasNoCloudId() {
        when(tenantUserRepository.findAllByActiveTrue()).thenReturn(List.of(membership));
        when(jiraConfigRepository.findActive()).thenReturn(Optional.empty());

        reconciler.reconcile();

        verify(tenantService, never()).deactivateMembership(any(), any());
        verifyNoInteractions(oauthTokenRepository, oauthService);
    }

    @Test
    @DisplayName("no stored OAuth token for user — cannot verify, skipped")
    void shouldSkipWhenNoTokenStored() {
        when(tenantUserRepository.findAllByActiveTrue()).thenReturn(List.of(membership));
        mockTenantCloudId(TENANT_CLOUD_ID);
        when(oauthTokenRepository.findByAtlassianAccountId("acc-42")).thenReturn(Optional.empty());

        reconciler.reconcile();

        verify(tenantService, never()).deactivateMembership(any(), any());
        verifyNoInteractions(oauthService);
    }

    @Test
    @DisplayName("token refresh/validation fails — deactivates membership (revoked by Atlassian)")
    void shouldDeactivateWhenTokenRefreshFails() {
        when(tenantUserRepository.findAllByActiveTrue()).thenReturn(List.of(membership));
        mockTenantCloudId(TENANT_CLOUD_ID);
        when(oauthTokenRepository.findByAtlassianAccountId("acc-42")).thenReturn(Optional.of(new OAuthTokenEntity()));
        when(oauthService.getValidAccessTokenForUser("acc-42")).thenReturn(null);

        reconciler.reconcile();

        verify(tenantService).deactivateMembership(membership, "jira_access_lost");
    }

    @Test
    @DisplayName("cloudId no longer in accessible resources — deactivates membership")
    void shouldDeactivateWhenAccessLost() {
        when(tenantUserRepository.findAllByActiveTrue()).thenReturn(List.of(membership));
        mockTenantCloudId(TENANT_CLOUD_ID);
        when(oauthTokenRepository.findByAtlassianAccountId("acc-42")).thenReturn(Optional.of(new OAuthTokenEntity()));
        when(oauthService.getValidAccessTokenForUser("acc-42"))
                .thenReturn(new OAuthService.TokenInfo("access-token-x", TENANT_CLOUD_ID));
        when(oauthService.userHasJiraAccess("access-token-x", TENANT_CLOUD_ID)).thenReturn(false);

        reconciler.reconcile();

        verify(tenantService).deactivateMembership(membership, "jira_access_lost");
    }

    @Test
    @DisplayName("still has access — membership left untouched")
    void shouldNotDeactivateWhenAccessStillValid() {
        when(tenantUserRepository.findAllByActiveTrue()).thenReturn(List.of(membership));
        mockTenantCloudId(TENANT_CLOUD_ID);
        when(oauthTokenRepository.findByAtlassianAccountId("acc-42")).thenReturn(Optional.of(new OAuthTokenEntity()));
        when(oauthService.getValidAccessTokenForUser("acc-42"))
                .thenReturn(new OAuthService.TokenInfo("access-token-x", TENANT_CLOUD_ID));
        when(oauthService.userHasJiraAccess("access-token-x", TENANT_CLOUD_ID)).thenReturn(true);

        reconciler.reconcile();

        verify(tenantService, never()).deactivateMembership(any(), any());
    }

    @Test
    @DisplayName("clears TenantContext after processing each membership")
    void shouldClearTenantContextAfterProcessing() {
        when(tenantUserRepository.findAllByActiveTrue()).thenReturn(List.of(membership));
        mockTenantCloudId(TENANT_CLOUD_ID);
        when(oauthTokenRepository.findByAtlassianAccountId("acc-42")).thenReturn(Optional.of(new OAuthTokenEntity()));
        when(oauthService.getValidAccessTokenForUser("acc-42"))
                .thenReturn(new OAuthService.TokenInfo("access-token-x", TENANT_CLOUD_ID));
        when(oauthService.userHasJiraAccess("access-token-x", TENANT_CLOUD_ID)).thenReturn(true);

        reconciler.reconcile();

        assertFalse(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("one membership failing does not stop the rest of the batch")
    void shouldContinueBatchWhenOneMembershipFails() {
        TenantEntity tenant2 = new TenantEntity();
        tenant2.setId(2L);
        tenant2.setSlug("beta");
        tenant2.setSchemaName("tenant_beta");
        UserEntity user2 = new UserEntity();
        user2.setId(43L);
        user2.setAtlassianAccountId("acc-43");
        TenantUserEntity membership2 = new TenantUserEntity();
        membership2.setTenant(tenant2);
        membership2.setUser(user2);
        membership2.setActive(true);

        when(tenantUserRepository.findAllByActiveTrue()).thenReturn(List.of(membership, membership2));
        when(jiraConfigRepository.findActive()).thenThrow(new RuntimeException("boom")).thenReturn(Optional.of(cloudConfig(TENANT_CLOUD_ID)));
        when(oauthTokenRepository.findByAtlassianAccountId("acc-43")).thenReturn(Optional.of(new OAuthTokenEntity()));
        when(oauthService.getValidAccessTokenForUser("acc-43"))
                .thenReturn(new OAuthService.TokenInfo("access-token-y", TENANT_CLOUD_ID));
        when(oauthService.userHasJiraAccess("access-token-y", TENANT_CLOUD_ID)).thenReturn(false);

        reconciler.reconcile();

        verify(tenantService, never()).deactivateMembership(eq(membership), any());
        verify(tenantService).deactivateMembership(membership2, "jira_access_lost");
    }

    private TenantJiraConfigEntity cloudConfig(String cloudId) {
        TenantJiraConfigEntity config = new TenantJiraConfigEntity();
        config.setJiraCloudId(cloudId);
        return config;
    }
}
