# F65 Worklog Timeline

**Version:** 0.65.0
**Date:** 2026-03-08
**Type:** Full-stack feature (Backend + Frontend)

## Problem

Team leads need to see daily time logging patterns for each team member — who's logging consistently, who has gaps, how actual logged hours compare to capacity. Without this, workload imbalance and under-logging go unnoticed until sprint reviews.

## Solution

Heatmap-style timeline on TeamMembersPage showing logged hours per employee per day over the last 30 days, with weekend/holiday/absence highlighting and capacity summary.

## API

### `GET /api/teams/{teamId}/worklog-timeline?from=YYYY-MM-DD&to=YYYY-MM-DD`

**Response:**
```json
{
  "from": "2026-02-06",
  "to": "2026-03-08",
  "days": [
    { "date": "2026-02-06", "dayType": "WORKDAY" },
    { "date": "2026-02-07", "dayType": "WEEKEND" },
    { "date": "2026-02-08", "dayType": "HOLIDAY" }
  ],
  "members": [
    {
      "memberId": 1,
      "displayName": "Alice",
      "role": "DEV",
      "avatarUrl": "https://...",
      "hoursPerDay": 8.0,
      "entries": [
        { "date": "2026-02-06", "hoursLogged": 6.5, "absenceType": null },
        { "date": "2026-02-07", "hoursLogged": null, "absenceType": null },
        { "date": "2026-02-10", "hoursLogged": null, "absenceType": "VACATION" }
      ],
      "summary": {
        "totalLogged": 120.0,
        "workdaysInPeriod": 18,
        "capacityHours": 144.0,
        "ratio": 83.3
      }
    }
  ]
}
```

**Day types:** `WORKDAY`, `WEEKEND`, `HOLIDAY`
**Absence types:** `VACATION`, `SICK_LEAVE`, `DAY_OFF`, `OTHER`

## Backend

### WorklogTimelineService

Core service composing data from 4 sources:

1. **Team members** — `TeamMemberRepository.findByTeamIdAndActiveTrue()`
2. **Calendar** — `WorkCalendarService.getWorkdaysInfo()` for holidays/weekends
3. **Worklogs** — `IssueWorklogRepository.findDailyWorklogsByAuthors()` (new aggregation query)
4. **Absences** — `AbsenceService.getAbsencesForTeam()`

**Logic:**
- Build day infos array (WORKDAY/WEEKEND/HOLIDAY) using calendar API
- Aggregate worklogs by author + date via native SQL `GROUP BY`
- Overlay absences on member entries (memberId -> date -> absenceType)
- Sort members by role pipeline order from `WorkflowConfigService.getRoleCodesInPipelineOrder()`, then alphabetically
- Calculate summary: totalLogged, workdaysAvailable (workdays minus absence days), capacity (workdays x hoursPerDay), ratio (%)

**Hours rounding:** `Math.round(totalSeconds / 360.0) / 10.0` — 1 decimal place

### New Repository Query

```sql
SELECT w.author_account_id, w.started_date, SUM(w.time_spent_seconds) as total_seconds
FROM issue_worklogs w
WHERE w.author_account_id IN :accountIds
  AND w.started_date BETWEEN :fromDate AND :toDate
GROUP BY w.author_account_id, w.started_date
ORDER BY w.author_account_id, w.started_date
```

### Database Migration

`T11__worklog_author_date_index.sql` — performance index:
```sql
CREATE INDEX IF NOT EXISTS idx_worklogs_author_date
  ON issue_worklogs(author_account_id, started_date);
```

## Frontend

### WorklogTimeline Component

Two-panel layout (same pattern as AbsenceTimeline):
- **Left panel** (360px fixed): member avatar, name, role badge, summary (logged/capacity + ratio%)
- **Right panel** (scrollable): day grid with cells

**Cell rendering:**
| Cell State | Display |
|------------|---------|
| Hours logged | Plain number (integer if whole, 1 decimal otherwise) |
| No hours | Empty cell |
| Weekend | Gray background (`#f9fafb`) |
| Holiday | Orange background (`#fff3e6`) |
| Absence | Colored cell with short label (VAC/SICK/OFF/OTH) |
| Low hours (<50% capacity on workday) | Red text |
| Today | Blue left border |

**Summary indicators:**
| Ratio | Color | Class |
|-------|-------|-------|
| >= 90% | Green (`#36B37E`) | `good` |
| >= 70% | Yellow (`#FF991F`) | `warning` |
| < 70% | Red (`#DE350B`) | `danger` |

**Role groups:** Members grouped by role with colored separator rows using `getRoleColor()` from WorkflowConfigContext.

**Period:** Last 30 days (computed client-side via `useMemo`).

### Integration

- Added as collapsible section on `TeamMembersPage` (alongside existing AbsenceTimeline — both kept)
- Toggle state: `showWorklogTimeline` (default expanded)
- Imports `ABSENCE_COLORS` from `AbsenceModal` for consistent absence colors

## Files

**New (5):**
- `backend/src/main/java/com/leadboard/team/WorklogTimelineService.java`
- `backend/src/main/java/com/leadboard/team/dto/WorklogTimelineResponse.java`
- `backend/src/main/resources/db/tenant/T11__worklog_author_date_index.sql`
- `frontend/src/components/WorklogTimeline.tsx`
- `frontend/src/components/WorklogTimeline.css`

**Modified (4):**
- `backend/src/main/java/com/leadboard/team/TeamController.java` — new endpoint + DI
- `backend/src/main/java/com/leadboard/metrics/repository/IssueWorklogRepository.java` — new query
- `frontend/src/api/teams.ts` — types + API method
- `frontend/src/pages/TeamMembersPage.tsx` — WorklogTimeline section

**Tests (1):**
- `backend/src/test/java/com/leadboard/team/WorklogTimelineServiceTest.java` — 7 tests

## Tests

| Test | What it covers |
|------|---------------|
| `emptyTeam_returnsEmptyResponse` | Empty team returns valid response with day infos |
| `noWorklogs_allEntriesNull` | Members without worklogs have null hoursLogged |
| `worklogsAggregatedCorrectly` | Seconds-to-hours conversion, entry mapping |
| `absencesMarkedCorrectly` | Absence type overlay, capacity reduction |
| `dayTypesDetectedCorrectly` | Weekend/holiday/workday detection from calendar |
| `membersSortedByRolePipelineOrder` | Pipeline-order sorting (DEV before QA) |
| `capacityAndRatioCalculation` | Full capacity and ratio math (40h/32h = 125%) |
