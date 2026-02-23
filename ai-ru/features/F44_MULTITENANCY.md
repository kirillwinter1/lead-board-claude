# F44: Multi-Tenancy & SaaS Packaging

**Дата:** 2026-02-23
**Версия:** 0.44.0
**Бэклог:** BF19

## Обзор

Schema-per-tenant мультитенантность для Lead Board. Каждая компания (tenant) получает изолированное пространство данных в отдельной PostgreSQL-схеме с собственным подключением к Jira.

## Архитектура

### Изоляция данных
- **Schema per tenant** — одна PostgreSQL БД, отдельная схема на компанию
- **Идентификация** — по поддомену (`company.leadboard.app`) или header `X-Tenant-Slug` (dev)
- **Public schema** — глобальные данные: `tenants`, `users`, `tenant_users`, `user_sessions`, `oauth_tokens`
- **Tenant schema** — все бизнес-данные: `jira_issues`, `teams`, `project_configurations`, workflow config, planning poker, RICE, absences, bug SLA и т.д.

### Hibernate Multi-tenancy
- `SET search_path TO {tenant_schema}, public` — прозрачное переключение схем
- `@Table(schema = "public")` — глобальные entities всегда в public
- `CurrentTenantIdentifierResolver` → читает из `TenantContext`
- `MultiTenantConnectionProvider` → устанавливает search_path

### Фильтры (порядок)
1. **TenantFilter** — резолвит tenant из subdomain/header → `TenantContext`
2. **LeadBoardAuthenticationFilter** — резолвит user из session (tenant-aware)

## Миграции

### V44: Public schema
- Таблица `tenants` (slug, name, schema_name, plan, trial_ends_at, is_active)
- Таблица `tenant_users` (tenant_id, user_id, app_role) — роль per-tenant
- `user_sessions.tenant_id` — привязка сессии к тенанту

### T1: Tenant schema (initial)
- Все 27 бизнес-таблиц из V1-V43 (консолидированные)
- Seed data для RICE шаблонов и bug SLA defaults

### T2: Tenant Jira config
- `tenant_jira_config` — Jira подключение per tenant

## Новые файлы (backend)

| Файл | Описание |
|------|----------|
| `tenant/TenantEntity.java` | Entity для `public.tenants` |
| `tenant/TenantUserEntity.java` | Entity для `public.tenant_users` |
| `tenant/TenantPlan.java` | Enum: TRIAL, FREE, PRO, ENTERPRISE |
| `tenant/TenantContext.java` | ThreadLocal tenant state |
| `tenant/TenantFilter.java` | HTTP filter: subdomain/header → tenant |
| `tenant/TenantService.java` | CRUD тенантов, валидация slug |
| `tenant/TenantRepository.java` | findBySlug, findBySchemaName, existsBySlug |
| `tenant/TenantUserRepository.java` | findByTenantIdAndUserId, findByUserId |
| `tenant/TenantMigrationService.java` | Flyway per tenant schema (T-prefix) |
| `tenant/TenantRegistrationController.java` | POST /api/public/tenants/register |
| `tenant/TenantSchemaResolver.java` | Hibernate tenant resolver |
| `tenant/SchemaBasedConnectionProvider.java` | Hibernate connection provider |
| `tenant/TenantJiraConfigEntity.java` | Jira connection per tenant |
| `tenant/TenantJiraConfigRepository.java` | findActive() |
| `tenant/TenantSyncScheduler.java` | Scheduled sync per tenant |

## Новые файлы (frontend)

| Файл | Описание |
|------|----------|
| `utils/tenant.ts` | getTenantSlug(), isMainDomain(), setDevTenantSlug() |
| `pages/RegistrationPage.tsx` | Форма регистрации компании |

## Изменённые файлы

| Файл | Что изменилось |
|------|---------------|
| `auth/SessionEntity.java` | + `@Table(schema="public")`, + tenantId |
| `auth/UserEntity.java` | + `@Table(schema="public")` |
| `auth/OAuthTokenEntity.java` | + `@Table(schema="public")` |
| `auth/LeadBoardAuthentication.java` | + tenantId, tenantRole, per-tenant authorities |
| `auth/LeadBoardAuthenticationFilter.java` | Tenant-aware: loads per-tenant role |
| `auth/OAuthService.java` | Tenant-aware OAuth flow, tenant_users creation |
| `config/SecurityConfig.java` | TenantFilter before AuthFilter, /api/public/** permitAll |
| `config/service/WorkflowConfigService.java` | Per-tenant cache (ensureLoaded) |
| `jira/JiraClient.java` | Team field from TenantJiraConfig fallback |
| `sync/SyncService.java` | + syncProjectForTenant() |
| `application.yml` | Hibernate multi-tenancy: SCHEMA |
| `App.tsx` | + /register route |
| `main.tsx` | Axios interceptor for X-Tenant-Slug header |

## API

### Public (без авторизации)
- `POST /api/public/tenants/register` — регистрация тенанта (name, slug)
- `GET /api/public/tenants/check-slug?slug=acme` — проверка доступности slug

## Роли per-tenant

Один пользователь может иметь разные роли в разных тенантах:
- `tenant_users.app_role` — ADMIN, PROJECT_MANAGER, MEMBER, VIEWER
- Первый OAuth-пользователь тенанта автоматически получает ADMIN

## Sync per Tenant

- `TenantSyncScheduler` — каждую минуту проверяет активных тенантов
- Для каждого тенанта: устанавливает TenantContext → синкает все project keys
- Каждый тенант синкается независимо, ошибка одного не блокирует других

## Тесты

- `TenantServiceTest` — 8 тестов (CRUD, валидация slug, users)
- `TenantContextTest` — 3 теста (ThreadLocal lifecycle)
- `OAuthServiceTest` — обновлён (tenant-aware mock)
- `LeadBoardAuthenticationFilterTest` — обновлён (tenant-aware mock)
