---
name: deploy
description: Deploy the application to production server. Use when user asks to deploy, push to production, or check production status.
argument-hint: "[version or 'check' to verify status]"
allowed-tools: Read, Bash, Agent, AskUserQuestion
---

# Deploy to Production

**Argument:** $ARGUMENTS

Use the **production-deploy** agent to handle the deployment.

If argument is "check" or "status" — verify production health only, don't deploy.

Otherwise, proceed with full deployment:

## Build locally, then deploy

### 1. Run tests
```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/backend
./gradlew test
```

### 2. Build backend JAR locally
```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/backend
./gradlew bootJar
# Result: build/libs/leadboard-*.jar
```

### 3. Build frontend dist locally
```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend
npm run build
# Result: dist/
```

### 4. Build Docker images (from local artifacts, NO build inside Docker)
```bash
# Backend — copies pre-built JAR
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/backend
docker build --platform linux/amd64 -t onelane-backend:latest .

# Frontend — copies pre-built dist
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend
docker build --platform linux/amd64 -t onelane-frontend:latest .
```

### 5. Push images to server
```bash
docker save onelane-backend:latest | gzip > /tmp/onelane-backend.tar.gz
docker save onelane-frontend:latest | gzip > /tmp/onelane-frontend.tar.gz
scp /tmp/onelane-backend.tar.gz root@79.174.94.70:/root/
scp /tmp/onelane-frontend.tar.gz root@79.174.94.70:/root/
ssh root@79.174.94.70 "docker load < /root/onelane-backend.tar.gz"
ssh root@79.174.94.70 "docker load < /root/onelane-frontend.tar.gz"
```

### 6. Restart containers
```bash
ssh root@79.174.94.70 "cd /opt/leadboard && docker compose -f docker-compose.prod.yml up -d"
```

### 7. Verify health
```bash
ssh root@79.174.94.70 "curl -s http://localhost:8080/api/health"
ssh root@79.174.94.70 "curl -s -o /dev/null -w '%{http_code}' http://localhost:3000"
```

## Important rules
- NEVER build inside Docker (no multi-stage builds). Build JAR and dist locally, Docker only packages.
- ALWAYS run tests before deploying.
- ALWAYS confirm with the user before pushing to production.
- `--platform linux/amd64` is required (Mac = arm64, server = amd64).
