package com.leadboard.tenant;

/**
 * Thread-local holder for current tenant context.
 * Set by TenantFilter at the beginning of each request,
 * cleared at the end.
 */
public final class TenantContext {

    public static final String DEFAULT_SCHEMA = "public";

    private static final ThreadLocal<String> currentSchema = new ThreadLocal<>();
    private static final ThreadLocal<Long> currentTenantId = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenant(Long tenantId, String schemaName) {
        currentTenantId.set(tenantId);
        currentSchema.set(schemaName);
    }

    public static String getCurrentSchema() {
        String schema = currentSchema.get();
        return schema != null ? schema : DEFAULT_SCHEMA;
    }

    public static Long getCurrentTenantId() {
        return currentTenantId.get();
    }

    public static boolean hasTenant() {
        return currentTenantId.get() != null;
    }

    public static void clear() {
        currentSchema.remove();
        currentTenantId.remove();
    }
}
