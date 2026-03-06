---
description: Deploy the application to production server
argument-hint: "[version or 'check' to verify status]"
allowed-tools: Read, Bash, Agent, AskUserQuestion
---

# Deploy to Production

**Argument:** $ARGUMENTS

Use the **production-deploy** agent to handle the deployment.

If argument is "check" or "status" — verify production health only, don't deploy.

Otherwise, proceed with full deployment:
1. Run tests before deploying
2. Build Docker images
3. Push to production server (79.174.94.70)
4. Run migrations
5. Verify health checks

Always confirm with the user before pushing to production.
