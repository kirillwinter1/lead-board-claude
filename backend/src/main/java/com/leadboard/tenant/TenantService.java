package com.leadboard.tenant;

import com.leadboard.auth.AppRole;
import com.leadboard.auth.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,61}[a-z0-9]$");
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "www", "api", "app", "admin", "mail", "ftp", "smtp",
            "pop", "imap", "ns1", "ns2", "test", "dev", "staging",
            "beta", "demo", "help", "support", "status", "docs"
    );
    private static final int TRIAL_DAYS = 14;

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantMigrationService tenantMigrationService;

    public TenantService(TenantRepository tenantRepository,
                         TenantUserRepository tenantUserRepository,
                         TenantMigrationService tenantMigrationService) {
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantMigrationService = tenantMigrationService;
    }

    @Transactional
    public TenantEntity createTenant(String name, String slug) {
        validateSlug(slug);

        if (tenantRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Slug '" + slug + "' is already taken");
        }

        String schemaName = "tenant_" + slug.replace("-", "_");

        TenantEntity tenant = new TenantEntity();
        tenant.setName(name);
        tenant.setSlug(slug);
        tenant.setSchemaName(schemaName);
        tenant.setPlan(TenantPlan.TRIAL);
        tenant.setTrialEndsAt(OffsetDateTime.now().plusDays(TRIAL_DAYS));
        tenant.setActive(true);

        tenant = tenantRepository.save(tenant);

        // Create schema and run tenant migrations
        tenantMigrationService.createTenantSchema(schemaName);

        log.info("Created tenant '{}' with schema '{}'", slug, schemaName);
        return tenant;
    }

    @Transactional
    public TenantUserEntity addUserToTenant(TenantEntity tenant, UserEntity user, AppRole role) {
        Optional<TenantUserEntity> existing = tenantUserRepository.findByTenantIdAndUserId(tenant.getId(), user.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        TenantUserEntity tenantUser = new TenantUserEntity();
        tenantUser.setTenant(tenant);
        tenantUser.setUser(user);
        tenantUser.setAppRole(role);

        return tenantUserRepository.save(tenantUser);
    }

    public Optional<TenantEntity> findBySlug(String slug) {
        return tenantRepository.findBySlug(slug);
    }

    public Optional<TenantEntity> findById(Long id) {
        return tenantRepository.findById(id);
    }

    public List<TenantEntity> findAllActive() {
        return tenantRepository.findAllActive();
    }

    public Optional<TenantUserEntity> findTenantUser(Long tenantId, Long userId) {
        return tenantUserRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    public List<TenantUserEntity> findUserTenants(Long userId) {
        return tenantUserRepository.findByUserId(userId);
    }

    /**
     * Returns true if slug passes format and reserved-word checks.
     * Used by check-slug endpoint for pre-validation (BUG-64).
     */
    public boolean isValidSlug(String slug) {
        if (slug == null || slug.isBlank()) return false;
        if (!SLUG_PATTERN.matcher(slug).matches()) return false;
        return !RESERVED_SLUGS.contains(slug);
    }

    private void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Slug cannot be empty");
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "Slug must be 3-63 characters, lowercase alphanumeric with hyphens, starting with a letter");
        }
        if (RESERVED_SLUGS.contains(slug)) {
            throw new IllegalArgumentException("Slug '" + slug + "' is reserved");
        }
    }
}
