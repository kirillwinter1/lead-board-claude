package com.leadboard.tenant;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * Manages Flyway migrations for tenant schemas.
 * - Creates new schemas with all business tables
 * - On startup, runs pending migrations for all existing tenants
 */
@Service
public class TenantMigrationService {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationService.class);
    private static final String TENANT_MIGRATIONS_LOCATION = "classpath:db/tenant";

    private final DataSource dataSource;
    private final TenantRepository tenantRepository;

    public TenantMigrationService(DataSource dataSource, TenantRepository tenantRepository) {
        this.dataSource = dataSource;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Creates a new PostgreSQL schema and runs all tenant migrations in it.
     */
    public void createTenantSchema(String schemaName) {
        log.info("Creating tenant schema: {}", schemaName);

        // 1. Create the schema
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create schema: " + schemaName, e);
        }

        // 2. Run Flyway migrations in the new schema
        runMigrations(schemaName);
    }

    /**
     * On application startup, migrate all existing tenant schemas.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void migrateAllTenants() {
        List<TenantEntity> tenants = tenantRepository.findAllActive();
        if (tenants.isEmpty()) {
            log.info("No active tenants found, skipping tenant migrations");
            return;
        }

        log.info("Running tenant migrations for {} active tenants", tenants.size());
        for (TenantEntity tenant : tenants) {
            try {
                runMigrations(tenant.getSchemaName());
            } catch (Exception e) {
                log.error("Failed to migrate tenant schema: {}", tenant.getSchemaName(), e);
            }
        }
    }

    private void runMigrations(String schemaName) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations(TENANT_MIGRATIONS_LOCATION)
                .table("flyway_schema_history")
                .baselineOnMigrate(false)
                .sqlMigrationPrefix("T")
                .load();

        flyway.migrate();
        log.info("Tenant schema '{}' migrated successfully", schemaName);
    }
}
