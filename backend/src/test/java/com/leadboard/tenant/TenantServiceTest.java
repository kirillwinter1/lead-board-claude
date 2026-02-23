package com.leadboard.tenant;

import com.leadboard.auth.AppRole;
import com.leadboard.auth.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @Mock
    private TenantMigrationService tenantMigrationService;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, tenantUserRepository, tenantMigrationService);
    }

    @Test
    @DisplayName("should create tenant with valid slug")
    void shouldCreateTenant() {
        when(tenantRepository.existsBySlug("acme")).thenReturn(false);
        when(tenantRepository.save(any(TenantEntity.class))).thenAnswer(inv -> {
            TenantEntity t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        TenantEntity tenant = tenantService.createTenant("Acme Corp", "acme");

        assertEquals("acme", tenant.getSlug());
        assertEquals("Acme Corp", tenant.getName());
        assertEquals("tenant_acme", tenant.getSchemaName());
        assertEquals(TenantPlan.TRIAL, tenant.getPlan());
        assertNotNull(tenant.getTrialEndsAt());
        assertTrue(tenant.isActive());

        verify(tenantMigrationService).createTenantSchema("tenant_acme");
    }

    @Test
    @DisplayName("should reject duplicate slug")
    void shouldRejectDuplicateSlug() {
        when(tenantRepository.existsBySlug("acme")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> tenantService.createTenant("Acme Corp", "acme"));
    }

    @Test
    @DisplayName("should reject reserved slugs")
    void shouldRejectReservedSlug() {
        assertThrows(IllegalArgumentException.class,
                () -> tenantService.createTenant("Test", "www"));
        assertThrows(IllegalArgumentException.class,
                () -> tenantService.createTenant("Test", "api"));
        assertThrows(IllegalArgumentException.class,
                () -> tenantService.createTenant("Test", "admin"));
    }

    @Test
    @DisplayName("should reject invalid slug format")
    void shouldRejectInvalidSlugFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> tenantService.createTenant("Test", "ab")); // too short
        assertThrows(IllegalArgumentException.class,
                () -> tenantService.createTenant("Test", "AB")); // uppercase
        assertThrows(IllegalArgumentException.class,
                () -> tenantService.createTenant("Test", "1abc")); // starts with number
    }

    @Test
    @DisplayName("should convert slug with hyphens to schema name with underscores")
    void shouldConvertHyphensToUnderscores() {
        when(tenantRepository.existsBySlug("my-company")).thenReturn(false);
        when(tenantRepository.save(any(TenantEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantEntity tenant = tenantService.createTenant("My Company", "my-company");
        assertEquals("tenant_my_company", tenant.getSchemaName());
    }

    @Test
    @DisplayName("should add user to tenant")
    void shouldAddUserToTenant() {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(1L);
        UserEntity user = new UserEntity();
        user.setId(1L);

        when(tenantUserRepository.findByTenantIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
        when(tenantUserRepository.save(any(TenantUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantUserEntity result = tenantService.addUserToTenant(tenant, user, AppRole.ADMIN);

        assertEquals(AppRole.ADMIN, result.getAppRole());
        verify(tenantUserRepository).save(any(TenantUserEntity.class));
    }

    @Test
    @DisplayName("should not duplicate tenant user")
    void shouldNotDuplicateTenantUser() {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(1L);
        UserEntity user = new UserEntity();
        user.setId(1L);

        TenantUserEntity existing = new TenantUserEntity();
        existing.setAppRole(AppRole.MEMBER);
        when(tenantUserRepository.findByTenantIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));

        TenantUserEntity result = tenantService.addUserToTenant(tenant, user, AppRole.ADMIN);

        assertEquals(AppRole.MEMBER, result.getAppRole()); // existing not overwritten
        verify(tenantUserRepository, never()).save(any());
    }
}
