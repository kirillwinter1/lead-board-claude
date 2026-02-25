package com.leadboard.tenant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public (no auth required) endpoints for tenant registration.
 */
@RestController
@RequestMapping("/api/public/tenants")
public class TenantRegistrationController {

    private final TenantService tenantService;

    public TenantRegistrationController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            TenantEntity tenant = tenantService.createTenant(request.name(), request.slug());
            return ResponseEntity.ok(Map.of(
                    "tenantId", tenant.getId(),
                    "slug", tenant.getSlug(),
                    "schemaName", tenant.getSchemaName(),
                    "redirectUrl", "https://" + tenant.getSlug() + ".leadboard.app"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/check-slug")
    public ResponseEntity<?> checkSlug(@RequestParam String slug) {
        String normalized = slug.toLowerCase().trim();
        // Validate slug format and reserved words before DB lookup (BUG-64)
        if (!tenantService.isValidSlug(normalized)) {
            return ResponseEntity.ok(Map.of("available", false, "reason", "invalid"));
        }
        boolean available = !tenantService.findBySlug(normalized).isPresent();
        return ResponseEntity.ok(Map.of("available", available));
    }

    public record RegisterRequest(String name, String slug) {}
}
