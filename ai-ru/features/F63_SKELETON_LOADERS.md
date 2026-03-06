# F63 Skeleton Loaders + Stale-While-Revalidate Cache

**Version:** 0.63.0
**Type:** Frontend-only UX improvement

## Problem

When navigating between tabs (Board, Timeline, Projects), users see:
1. Plain text loading messages ("Loading board...", etc.) on white background — causes visual flicker
2. When returning to a previously visited page, data loads again from scratch — content "jumps"

## Solution

Two-layer approach:
- **Skeleton loaders** — instead of text, show pulsating page skeleton on first load
- **Stale-while-revalidate cache** — on revisit, show cached data instantly, refresh in background (no loading state)

## New Components

### Skeleton (base)
- `frontend/src/components/Skeleton.tsx` + `Skeleton.css`
- Simple `<span>` with CSS pulse animation. Props: `width`, `height`, `borderRadius`, `style`

### BoardSkeleton
- `frontend/src/components/skeletons/BoardSkeleton.tsx`
- Uses real board CSS classes (`board-table-container`, `board-grid`, `board-header`, `board-row`)
- Grid matches actual board: `40px minmax(280px, 1fr) 170px 80px 130px 110px 260px 200px 70px`
- 6 rows with stagger animation

### GanttSkeleton
- `frontend/src/components/skeletons/GanttSkeleton.tsx`
- Two-panel layout: labels (380px) + chart area
- 6 rows with varying bar widths/offsets

### ProjectListSkeleton
- `frontend/src/components/skeletons/ProjectListSkeleton.tsx`
- 5 card placeholders mimicking project cards

## Cache Layer

### useApiCache hook
- `frontend/src/hooks/useApiCache.ts`
- Module-level `Map<string, unknown>` cache surviving component unmounts
- Exports: `useApiCache<T>(key, fetcher)`, `getApiCache(key)`, `setApiCache(key, value)`

### Integration
- **Board:** `useBoardData.ts` — initializes from cache, fetches silently on revisit
- **Timeline:** `TimelinePage.tsx` — cache per team ID, skeleton on first load
- **Projects:** `ProjectsPage.tsx` — cache list + timeline data, shows skeleton or cached data

## UX Behavior

| Scenario | Before | After |
|----------|--------|-------|
| First visit to Board | "Loading board..." text | Pulsating board skeleton |
| First visit to Timeline | Text loading | Pulsating Gantt skeleton |
| First visit to Projects | "Loading projects..." text | Pulsating card skeletons |
| Return to Board | Loading text again | Instant cached data, silent background refresh |
| Return to Timeline | Loading text again | Instant cached data, silent background refresh |
| Return to Projects | Loading text again | Instant cached data, silent background refresh |

## Files

**New (7):**
- `frontend/src/components/Skeleton.tsx` + `Skeleton.css`
- `frontend/src/components/skeletons/BoardSkeleton.tsx`
- `frontend/src/components/skeletons/GanttSkeleton.tsx`
- `frontend/src/components/skeletons/ProjectListSkeleton.tsx`
- `frontend/src/components/skeletons/index.ts`
- `frontend/src/hooks/useApiCache.ts`

**Modified (5):**
- `frontend/src/hooks/useBoardData.ts` — cache layer
- `frontend/src/pages/BoardPage.tsx` — BoardSkeleton
- `frontend/src/pages/TimelinePage.tsx` — GanttSkeleton + cache
- `frontend/src/pages/ProjectsPage.tsx` — ProjectListSkeleton/GanttSkeleton + cache
- `frontend/package.json` + `backend/build.gradle.kts` — version 0.63.0
