---
name: bugfix
description: Find and fix a bug by description or bug number. Use when user reports a bug, error, or broken functionality.
argument-hint: "[bug-description or BUG-number]"
allowed-tools: Read, Edit, Write, Bash, Glob, Grep, Agent, AskUserQuestion
---

# Bugfix Orchestrator

**Bug:** $ARGUMENTS

## Step 1: Understand the Bug

1. If a BUG-number was given, search in `ai-ru/testing/QA_STATUS.md` and `ai-ru/testing/reports/` for details
2. If a description was given, search for related code using Grep/Glob
3. Read the relevant source files to understand the current behavior

## Step 2: Diagnose Root Cause

- Trace the code path that causes the bug
- Identify the exact file(s) and line(s) responsible
- Check if this is a regression (git log/blame)

Present the diagnosis to the user before fixing.

## Step 3: Implement Fix

Determine if the fix is backend, frontend, or both:
- **Backend-only** — use **backend-engineer** agent
- **Frontend-only** — use **frontend-engineer** agent
- **Both** — fix backend first, then frontend

## Step 4: Verify

```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/backend && ./gradlew test
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend && npm run build
```

## Step 5: Summary

Report:
- Root cause
- What was fixed and where
- Tests passing
- Any related issues to watch
