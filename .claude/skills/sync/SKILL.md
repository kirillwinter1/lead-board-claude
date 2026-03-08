---
name: sync
description: Trigger Jira sync and verify results. Use when user asks to sync data from Jira, refresh issues, or check sync status.
argument-hint: "[optional: team-name or 'full']"
allowed-tools: Bash, Read
---

# Jira Sync

Trigger a manual Jira sync and verify the results.

**Argument:** $ARGUMENTS

## Step 1: Check Backend is Running

```bash
curl -s http://localhost:8080/api/health | head -20
```

If not running, inform the user.

## Step 2: Trigger Sync

```bash
curl -s -X POST http://localhost:8080/api/sync/trigger
```

## Step 3: Wait and Verify

Wait 5 seconds, then check sync status:

```bash
sleep 5 && curl -s http://localhost:8080/api/sync/status
```

## Step 4: Report

Show the sync result — number of issues synced, any errors, last sync timestamp.
