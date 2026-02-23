package com.leadboard.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("should set and get tenant context")
    void shouldSetAndGetTenantContext() {
        TenantContext.setTenant(1L, "tenant_acme");

        assertEquals(1L, TenantContext.getCurrentTenantId());
        assertEquals("tenant_acme", TenantContext.getCurrentSchema());
        assertTrue(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("should return public schema when no tenant set")
    void shouldReturnPublicSchemaByDefault() {
        assertEquals("public", TenantContext.getCurrentSchema());
        assertNull(TenantContext.getCurrentTenantId());
        assertFalse(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("should clear tenant context")
    void shouldClearTenantContext() {
        TenantContext.setTenant(1L, "tenant_acme");
        TenantContext.clear();

        assertEquals("public", TenantContext.getCurrentSchema());
        assertNull(TenantContext.getCurrentTenantId());
        assertFalse(TenantContext.hasTenant());
    }
}
