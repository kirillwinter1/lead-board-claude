---
description: Orchestrate full feature implementation — spec, plan, backend, frontend, tests, docs
argument-hint: [feature-description or spec-path]
allowed-tools: Read, Edit, Write, Bash, Glob, Grep, Agent, AskUserQuestion, Skill
---

# Feature Implementation Orchestrator

You are orchestrating the implementation of a new feature for Lead Board.

**Feature:** $ARGUMENTS

**CRITICAL RULE: You MUST execute ALL 10 steps below in order. Do NOT skip any step. Do NOT stop early. After completing each step, explicitly state which step you just finished and which step is next. If you reach the context limit, tell the user which step to resume from.**

**ANTI-SKIP RULE: Steps 7 (review-and-fix agent) and 9 (qa-and-fix agent) are MANDATORY and must use the Agent tool. If you find yourself writing a summary without having run these agents — STOP and go back. The summary checklist in Step 10 will catch this.**

## Step 1: Understand the Feature

1. If a spec file path was given, read it fully
2. If a feature description was given, search for related specs in `ai-ru/features/` and `ai-ru/backlog/`
3. Read `ai-ru/ARCHITECTURE.md` for relevant architecture context
4. Read `ai-ru/RULES.md` for business rules

If requirements are unclear, use AskUserQuestion to clarify with the user. Prefer presenting options (A/B/C) over open-ended questions.

## Step 2: Create a Plan

**Use the `EnterPlanMode` tool NOW** to enter plan mode. Then create a detailed implementation plan:
- Database changes (migrations)
- Backend: entities, repositories, services, controllers, tests
- Frontend: pages, components, API calls, routing
- Documentation updates

Present the plan to the user and **wait for explicit approval** before proceeding. Use `ExitPlanMode` after approval to switch to implementation.

## Step 3: Determine the Feature Number

Check `ai-ru/FEATURES.md` for the last implemented F-number. The new feature gets next sequential number.

## Step 4: Implement Backend

**Skip if feature is frontend-only.** State "Step 4: skipped (frontend-only)" and move on.

Use the **backend-engineer** agent for:
- Database migrations (V{N} or T{N})
- Entity classes
- Repository interfaces
- Service layer with business logic
- REST controller with endpoints
- JUnit5 tests

**CHECKPOINT after Step 4:** State "Step 4 DONE. Remaining: 5-Frontend, 6-Tests, 7-Review agent, 8-Docs, 9-QA agent, 10-Summary"

## Step 5: Implement Frontend

**Skip if feature is backend-only (no UI changes).** State "Step 5: skipped (backend-only)" and move on.

Use the **frontend-engineer** agent for:
- TypeScript interfaces for API responses
- API client functions
- React page/components (following Design System rules)
- CSS styles
- Navigation/routing updates
- Build verification (`npm run build`)

**CHECKPOINT after Step 5:** State "Step 5 DONE. Remaining: 6-Tests, 7-Review agent, 8-Docs, 9-QA agent, 10-Summary"

## Step 6: Run Tests

```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/backend && ./gradlew test
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend && npm run build
```

If tests fail, fix the issues before proceeding.

**CHECKPOINT after Step 6:** State "Step 6 DONE. NEXT IS MANDATORY: Step 7 — Code Review agent. Then Step 9 — QA agent. Do NOT skip these."

## Step 7: Code Review + Fix (Agent)

**MANDATORY — DO NOT SKIP.** Launch the review-and-fix agent NOW:
```
Agent(agent="review-and-fix", prompt="Review all changes for feature F{N} <feature-name>. Check backend and frontend. Fix Critical/High issues by spawning backend-engineer and frontend-engineer sub-agents.")
```

This agent runs in its own context:
- Reviews all changed files (backend + frontend)
- Fixes Critical/High issues via backend-engineer / frontend-engineer sub-agents
- Re-runs tests after fixes
- Returns a structured review report

Wait for the agent to finish. Note the review results for the summary.

## Step 8: Update Documentation (Agent)

**MANDATORY — DO NOT SKIP.** Launch the docs-writer agent NOW:
```
Agent(agent="docs-writer", prompt="Feature F{N} <feature-name>. Description: <brief description of what was implemented>. Update FEATURES.md, create feature spec, bump version to 0.{N}.0.")
```

This agent runs in its own context:
- Updates `ai-ru/FEATURES.md` with new feature entry
- Creates `ai-ru/features/F{N}_NAME.md` spec file
- Bumps version in `backend/build.gradle.kts` and `frontend/package.json`
- Updates `ai-ru/ARCHITECTURE.md` if significant changes were made

## Step 9: QA Testing + Fix (Agent)

**MANDATORY — DO NOT SKIP.** Launch the qa-and-fix agent NOW:
```
Agent(agent="qa-and-fix", prompt="QA test feature F{N} <feature-name>. Test API endpoints, run unit tests, take UI screenshots. Fix Critical/High bugs by spawning backend-engineer and frontend-engineer sub-agents.")
```

This agent runs in its own context:
- Tests API endpoints and runs unit tests
- Takes UI screenshots via Playwright
- Fixes Critical/High bugs via backend-engineer / frontend-engineer sub-agents
- Re-tests after fixes
- Returns a QA report

Wait for the agent to finish. Note the QA results for the summary.

## Step 10: Summary

Present a summary to the user:
- What was implemented
- Files changed
- Tests status
- Code Review results (from review-and-fix agent report)
- QA results (from qa-and-fix agent report)
- Next steps (if any)

**CHECKLIST — verify before presenting summary:**
- [ ] Step 1: Feature understood
- [ ] Step 2: Plan approved by user
- [ ] Step 3: F-number assigned
- [ ] Step 4: Backend implemented (or skipped if frontend-only)
- [ ] Step 5: Frontend implemented (or skipped if backend-only)
- [ ] Step 6: Tests pass
- [ ] Step 7: `review-and-fix` agent executed — report received
- [ ] Step 8: `docs-writer` agent executed — FEATURES.md, spec, version bumped
- [ ] Step 9: `qa-and-fix` agent executed — report received
- [ ] Step 10: Summary presented

If any checkbox is unchecked — GO BACK and complete that step before finishing.
