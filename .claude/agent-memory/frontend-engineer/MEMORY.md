# Frontend Engineer Memory

## Multi-Tenancy Architecture

### Tenant Resolution Flow
1. `getTenantSlug()` in `src/utils/tenant.ts` resolves tenant from: subdomain first, then localStorage fallback
2. `main.tsx` has axios request interceptor that adds `X-Tenant-Slug` header to all requests
3. Backend `TenantFilter` resolves tenant from subdomain (priority) or header (localhost only)
4. **Unknown tenant slug returns 404** for all non-public routes (`/api/public/`, `/oauth/`, `/api/health`, `/actuator/`)
5. Auth check via `/oauth/atlassian/status` is a public route and succeeds even with wrong tenant slug

### Database Tenants (local dev)
- `test2` (tenant_id=6, schema: `tenant_test2`) - main development tenant with LB project (kirillwinter.atlassian.net)
- `perf-alpha/beta/gamma` - performance test tenants with ~600K issues each

### Common Pitfall: Wrong Tenant Slug
If localStorage has wrong tenant slug (e.g., "kirillwinter" instead of "test2"), ALL API calls except auth/health return 404. The user appears authenticated but no data loads. Fixed by adding tenant-not-found detection in Layout.tsx.

## Layout Rendering Logic
`Layout.tsx` has a 3-phase content rendering:
1. `tenantError` -> shows tenant-not-found error with "Go to Landing" button
2. `showWizard` (setupRequired === true) -> shows SetupWizardPage
3. `setupRequired === false` -> renders `<Outlet />` (child routes)
4. `setupRequired === null` (initial state) -> renders **nothing** (brief blank flash)

## Key File Paths
- Tenant utils: `src/utils/tenant.ts`
- Axios interceptors: `src/main.tsx`
- Auth context: `src/contexts/AuthContext.tsx`
- Layout with setup check: `src/components/Layout.tsx`
- Workflow config API: `src/api/workflowConfig.ts` (endpoints under `/api/admin/workflow-config`)
- Workflow config page: `src/pages/WorkflowConfigPage.tsx`

## Backend StatusCategory Enum
The `StatusCategory` enum in backend (`com.leadboard.status.StatusCategory`) must contain all values present in the database. If a tenant's DB has a status like 'BACKLOG' that doesn't exist in the enum, Hibernate throws `IllegalArgumentException` when loading configs. This was observed with the `perf-alpha` tenant.
