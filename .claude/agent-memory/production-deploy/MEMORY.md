# Production Deploy — Agent Memory

## Docker Socket on macOS

Docker Desktop does NOT auto-start. If `docker build` fails with "Cannot connect to Docker daemon":
- Check: `ls /Users/kirillreshetov/.docker/run/docker.sock`
- Fix: `open -a "Docker Desktop"` then wait ~10s for socket to appear
- The symlink `/var/run/docker.sock` → `~/.docker/run/docker.sock` exists but target only appears when Docker Desktop is running

## Frontend Container Healthcheck — Known IPv6 Issue

The `onelane-frontend` container shows "unhealthy" in `docker ps` because its healthcheck uses `wget localhost:80` which resolves to IPv6 `[::1]` while nginx listens on IPv4. This is a **pre-existing false alarm** — the service works correctly. Verified by:
- `curl http://localhost:3000` → 200 on server
- `curl https://onelane.ru` → 200 externally

## Integration Tests Require Docker (Testcontainers)

73 tests fail locally without Docker — all are Testcontainers-based integration/component/E2E tests. These are **pre-existing failures when Docker Desktop is not running** during `./gradlew test`. Unit tests pass. Pattern: `DockerClientProviderStrategy` in error = Testcontainers failure, not a code bug.

## Deployment Sequence

1. Run backend unit tests: `./gradlew test --tests "com.leadboard.metrics.*" ...` (skip integration tests if Docker not available locally)
2. Build frontend: `cd frontend && npm run build`
3. Build both Docker images with `--platform linux/amd64` (Mac = arm64, server = amd64)
4. Save as gzip tarballs → scp to server → docker load on server
5. Restart via `docker compose -f docker-compose.prod.yml down && up -d`
6. Wait ~30s for Spring Boot startup, then check `/api/health`

## Server Notes

- Server: 79.174.94.70, project at /opt/leadboard/
- Compose file: `docker-compose.prod.yml`
- Backend health: `curl http://localhost:8080/api/health`
- External: `curl https://onelane.ru/api/health`
- Backend container logs on startup: WARN about `project_configurations` and `jira_sync_state` not existing are **expected** in multi-tenant mode (tables live in tenant schemas, not public schema)
- Sync error "null value in column id" is a **runtime sync issue**, not a startup failure

## Disk Space

CRITICAL: Server has a 20 GB disk that fills up quickly with old Docker images.
After each deploy, the uploaded tar.gz archives stay in /root/ and Docker keeps old image layers.
Run this on server after loading images: `rm -f /root/*.tar.gz && docker image prune -f && docker builder prune -f`
Symptom when disk is full: backend crashes with "No space left on device" for /tmp/tomcat.*
Recovery: prune frees ~4-7 GB. Disk was at 100% (0 free), after prune: 40% used (12 GB free).

## Pre-existing Failing Integration Tests (known, ignore)

BoardIntegrationTest (4 tests) and FullSyncE2ETest (2 tests) — these fail in the main test run.
Confirmed pre-existing BEFORE F56. Root cause: tenant schema isolation in test setup.
Do not block deploys. 837+ unit/service tests all pass.

## ObservabilityMetrics Fix (F56)

refreshGauges() called issueRepository.count() without TenantContext → ERROR every 60s.
Fix: iterate tenantRepository.findAllActive(), set TenantContext per tenant, clear in finally.
After deploy: verify NO "jira_issues does not exist" lines from `scheduling-1` thread in logs.
Post-deploy check: after 60+ seconds, scheduler should run silently without any errors.

## Version Numbering

Version = feature number: F56 → v0.56.0. Update both:
- `backend/build.gradle.kts`: `version = "0.56.0"`
- `frontend/package.json`: `"version": "0.56.0"`

## Bug-fix deploys stay at same version

BUG-149/150/151/152/153/154/156 deployed without version bump (stayed at 0.56.0).
Version only bumps for new features (Fxx → v0.xx.0), not for bug-fix batches.

## Startup ERRORs — which are safe to ignore

These ERRORs appear in every startup and are safe (pre-existing, handled with catch):
1. `relation "project_configurations" does not exist` — WorkflowConfigService + MappingAutoDetectService
   read before tenant migrations run; caught, app continues
2. `relation "jira_sync_state" does not exist` — SyncService startup recovery; caught as WARN
Both resolve after TenantMigrationService runs (~14:18:31 in logs). App starts healthy.
