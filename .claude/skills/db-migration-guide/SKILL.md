---
name: db-migration-guide
description: Database migration patterns and conventions for Lead Board. Auto-loaded when creating Flyway migrations or modifying DB schema.
user-invocable: false
disable-model-invocation: false
---

# Database Migration Guide

## File Naming

- Public schema: `V{N}__description.sql` (e.g., `V47__add_labels_to_jira_issues.sql`)
- Tenant schema: `T{N}__description.sql` (e.g., `T7__add_labels_to_tenant.sql`)
- Location: `backend/src/main/resources/db/migration/`

## Before Creating a Migration

1. Check the latest migration number: `ls backend/src/main/resources/db/migration/ | sort | tail -5`
2. Use next sequential number — never skip or reuse numbers
3. For tenant migrations, check T-prefixed files separately

## Common Patterns

### Add Column
```sql
ALTER TABLE table_name ADD COLUMN column_name VARCHAR(255);
```

### Add Column with Default
```sql
ALTER TABLE table_name ADD COLUMN column_name BOOLEAN DEFAULT false NOT NULL;
```

### Conditional Migration (pgvector example)
```sql
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
    CREATE EXTENSION IF NOT EXISTS vector;
  END IF;
END $$;
```

### Add GIN Index (for arrays/jsonb)
```sql
CREATE INDEX idx_table_column ON table_name USING GIN (column_name);
```

## Important

- Flyway requires `org.flywaydb:flyway-database-postgresql:10.10.0`
- Migrations are immutable once deployed — never edit existing migrations
- Test migration locally before committing
- For tenant-specific tables, create T-prefixed migration that runs in each tenant schema
