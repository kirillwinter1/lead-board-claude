---
name: deploy-checklist
description: Production deployment checklist for Lead Board. Auto-loaded during deploy workflows.
user-invocable: false
disable-model-invocation: false
---

# Deploy Checklist

Server: 79.174.94.70, project at /opt/leadboard/

## Pre-Deploy

1. All tests pass: `cd backend && ./gradlew test`
2. Frontend builds: `cd frontend && npm run build`
3. No uncommitted changes: `git status`
4. Version bumped in build.gradle.kts and package.json
5. FEATURES.md and documentation updated

## Deploy Steps

1. Build Docker images locally
2. Push images to server
3. SSH to server, pull images
4. Run `docker compose up -d`
5. Wait for containers to start
6. Run health checks

## Post-Deploy Verification

1. Health check: `curl https://leadboard.ru/api/health`
2. Check logs: `docker compose logs --tail=50 backend`
3. Verify Flyway migrations applied
4. Test key pages load correctly
5. Check sync is running

## Rollback

If issues found:
1. `docker compose down`
2. Switch to previous image tag
3. `docker compose up -d`
4. Verify rollback successful

See DEPLOY.md for detailed instructions.
