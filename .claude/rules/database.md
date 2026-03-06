# Database Rules

## Production DB — FORBIDDEN Without Permission

**ANY production DB changes (UPDATE/INSERT/DELETE) are FORBIDDEN without EXPLICIT user permission.** This rule has NO exceptions. Even if the change seems "safe" or "obvious" — ALWAYS ask BEFORE executing. Violation of this rule has already caused an incident.

## Migrations

- Flyway migrations in `src/main/resources/db/migration/`
- Public schema: `V{N}__description.sql` (sequential numbering)
- Tenant schema: `T{N}__description.sql`
- Always check existing migration numbers before creating new ones
- Use conditional migrations when needed (e.g., checking `pg_available_extensions`)
- Flyway + PostgreSQL requires `org.flywaydb:flyway-database-postgresql:10.10.0`

## Multi-Tenancy

- Schema-per-tenant isolation using `TenantContext` (ThreadLocal) + `TenantFilter` (subdomain/header)
- Hibernate sets `SET search_path` per request
- **ALWAYS use `JiraConfigResolver`** for Jira config — NEVER use `JiraProperties` directly
- Tenant migrations are separate from public schema (T-prefixed)
