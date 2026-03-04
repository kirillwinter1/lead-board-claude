# Full Project Code Review Report

**Дата:** 2026-03-04
**Scope:** Весь проект (backend + frontend)
**Reviewer:** Claude Code Review Agents

---

## Статистика

| Severity | Backend | Frontend | Total |
|----------|---------|----------|-------|
| Critical | 3 | 2 | **5** |
| High | 9 | 9 | **18** |
| Medium | 14 | 22 | **36** |
| Low | 8 | 8 | **16** |
| **Total** | **34** | **41** | **75** |

**Verdict: REQUEST CHANGES** — 5 Critical + 18 High issues found

---

## Critical (5)

### C1. [Backend] SimulationService TOCTOU race condition
**Файл:** `simulation/SimulationService.java:41-42`
`existsByStatus("RUNNING")` check не атомарен с insert. Два одновременных запроса могут оба пройти проверку.
→ Использовать DB-level partial unique index на `status = 'RUNNING'` или advisory lock.

### C2. [Backend] SimulationScheduler ignores TenantContext
**Файл:** `simulation/SimulationScheduler.java`
Scheduler вызывает `simulationService.runSimulation()` без установки TenantContext. В мультитенантном режиме работает с public schema.
→ Итерировать по всем tenant slugs, устанавливать TenantContext перед каждым вызовом.

### C3. [Backend] Async SecurityContext propagation risk
**Файл:** `auth/AuthorizationService.java`
`@Async` методы, вызывающие `AuthorizationService`, могут не иметь SecurityContext в потоке. `getCurrentAuth()` вернёт null, `getUserTeamIds()` — пустой set.
→ Передавать account ID параметром в async-методы, не читать из SecurityContext.

### C4. [Frontend] isEpic() hardcodes "Epic"/"Эпик"
**Файл:** `frontend/src/components/board/helpers.ts:49-51`
```ts
return issueType === 'Epic' || issueType === 'Эпик'
```
Тенанты с другим названием типа эпика будут работать некорректно.
→ Удалить `isEpic()` из helpers.ts, использовать `useWorkflowConfig().isEpic(type)`.

### C5. [Frontend] triggerSync interval leak
**Файл:** `frontend/src/hooks/useBoardData.ts:41-62`
`setInterval` для polling статуса синка не отменяется при unmount компонента. Утечка памяти + обновление state на unmounted компоненте.
→ Использовать `useRef` + cleanup в `useEffect`, или AbortController.

---

## High (18)

### Backend (9)

| # | Файл | Проблема |
|---|------|----------|
| H1 | `WorkflowConfigService.java:335` | Hardcoded `"DEV"` fallback в `getDefaultRoleCode()` |
| H2 | `WorkflowConfigService.java:453-463` | Hardcoded `"SA"`, `"QA"` fallbacks в `determinePhase()` |
| H3 | `WorkflowConfigService.java:663-664` | Hardcoded `"Done"` / `"In Progress"` в `getFirstStatusNameForCategory()` |
| H4 | `WorkflowConfigService.java:744` | Hardcoded `"Story"` fallback в `getStoryTypeName()` |
| H5 | `TeamMemberEntity.java:26` | `private String role = "DEV"` — hardcoded default |
| H6 | `PlanningConfigDto.java:57-81` | Hardcoded `"SA"`, `"DEV"`, `"QA"` в `defaults()` |
| H7 | `DataQualityService.java:496-560` | N+1 queries (findByParentKey в цикле, recursive findByIssueKey) |
| H8 | `DsrService.java:189-238` | N+1 pattern (findByParentKey + findByParentKeyIn в per-epic loop) |
| H9 | `StoryPriorityService.java:101` | `issueRepository.findAll()` без фильтрации — OOM risk |

### Frontend (9)

| # | Файл | Проблема |
|---|------|----------|
| H10 | `DataQualityPage.tsx:79-103` | Локальный `SeverityBadge` с hardcoded цветами — дубликат StatusBadge |
| H11 | `DataQualityPage.tsx:105-112` | Локальный `SummaryCard` — дубликат MetricCard |
| H12 | `ProjectsPage.tsx:30-53` | Hardcoded progress bar цвета `#36B37E` / `#0065FF` |
| H13 | `DsrBreakdownChart.tsx:101` | `any` type в CustomYTick props |
| H14 | `QuarterlyPlanningPage.tsx:67` | Double `as any` при маппинге проектов |
| H15 | `WorkflowConfigPage.tsx:789-879` | Множество `value: any` параметров в update-функциях |
| H16 | `WorkflowConfigPage.tsx:717-999` | 7× `catch (err: any)` вместо `unknown` |
| H17 | `TeamMetricsPage.tsx:287` | Suppressed `exhaustive-deps` — stale closure risk |
| H18 | `DsrBreakdownChart.tsx:205` | `any` в bar click handler |

---

## Medium (36)

### Backend (14)

| # | Файл | Проблема |
|---|------|----------|
| M1 | `SimulationPlanner.java:340` | Hardcoded `"Epic"` fallback |
| M2 | `JiraClient.java:257` | Deprecated `createSubtask` hardcodes `"Sub-task"` |
| M3 | `PokerController.java:36-41` | Hardcoded `ELIGIBLE_STATUSES` list |
| M4 | `PokerJiraService.java:27-28` | `@Value("${jira.project-key}")` bypasses JiraConfigResolver |
| M5 | `DsrService.java:64` | `new ObjectMapper()` вместо Spring bean |
| M6 | `SimulationService.java` | `new ObjectMapper()` вместо Spring bean |
| M7 | `SimulationDeviation.java:14` | Redundant `@Autowired` |
| M8 | `EmbeddingService.java:26` | Field injection `@Autowired(required = false)` |
| M9 | `RiceAssessmentService.java:101-123` | N+1 в `computeEffortAuto()` |
| M10 | `AutoScoreService.java:50-55` | `save()` per epic в цикле (no batch) |
| M11 | `SyncService.java:334` | `inheritTeamFromParent()` вызван дважды без документации |
| M12 | `MetricsQueryRepository.java` | Dead code: `getMetricsByAssignee()` не используется |
| M13 | `TeamMetricsController.java` | Нет `@PreAuthorize` — любой user видит все метрики |
| M14 | `PokerController.java` | Нет `@PreAuthorize` на мутирующих endpoints |

### Frontend (22)

| # | Файл | Проблема |
|---|------|----------|
| M15 | `helpers.ts:26-42` | Hardcoded issue type names в fallback map |
| M16 | `TeamMetricsPage.tsx:26` | Dead export: `WipHistoryChart` не используется |
| M17 | `TeamMetricsPage.tsx:136-191` | `key={i}` на chart элементах |
| M18 | `DataQualityPage.tsx:153` | `key={i}` на violation rows |
| M19 | `ProjectsPage.tsx:149` | `key={i}` на recommendations |
| M20 | `ChatWidget.tsx:322` | `key={i}` на messages (streaming updates!) |
| M21 | `TimeInStatusChart.tsx:96-199` | Multiple `key={i}` в SVG chart |
| M22 | `WorkflowConfigPage.tsx:1145-1720` | Multiple `key={i}` / `key={idx}` |
| M23 | `ProjectTimelinePage.tsx:683-707` | `key={i}` на timeline headers |
| M24 | `ProjectTimelinePage.tsx:615` | Clickable div без `role="button"` / keyboard |
| M25 | `QuarterlyPlanningPage.tsx:365,604` | Clickable div/span без accessibility |
| M26 | `ProjectsPage.tsx:334-344` | Clickable div без keyboard handler |
| M27 | `Modal.tsx:30` | Backdrop div без role/keyboard |
| M28 | `TeamsPage.tsx + TeamMembersPage.tsx` | 9× `window.alert()`/`confirm()` |
| M29 | `AbsenceTimeline.tsx:92` | Suppressed deps с confusing logic |
| M30 | `TimelinePage.tsx:1530` | Suppressed `exhaustive-deps` |
| M31 | `WorkflowConfigPage.tsx:104-113` | `useEffect` deps missing `ref` |
| M32 | `Layout.tsx:112` | User avatar `alt=""` вместо `alt={displayName}` |
| M33 | `ProjectTimelinePage.tsx:60` + 2 файла | Дублирование `lightenColor()` в 3 файлах |
| M34 | `ProjectTimelinePage.tsx:529,851` | Hardcoded progress colors (duplicate H12) |
| M35 | Множество файлов | Hardcoded `'ru-RU'` locale |
| M36 | `TeamsPage.tsx:189-203` | Color picker span без keyboard access |

---

## Low (16)

### Backend (8)

| # | Файл | Проблема |
|---|------|----------|
| L1 | `WorkCalendarService.java:257` | Unimplemented `loadFromFile()` — TODO |
| L2 | `JiraIssueRepository.java` | `findByProjectKey()` / `findByLabelsIsNotNull()` без пагинации |
| L3 | `ProjectService.java:54-84` | Cold cache: N planning calculations on first request |
| L4 | `VelocityService.java` | Missing `@Transactional(readOnly = true)` |
| L5 | `ChatToolExecutor.java:155-169` | N+1 в `teamList()` member count |
| L6 | `ProjectAlignmentService.java:165` | Full table scan на каждый recalculate |
| L7 | `AutoScoreCalculator.java:283-358` | Thread safety risk — instance fields в singleton |
| L8 | `BoardService.java` | Нет lookback filter для done epics |

### Frontend (8)

| # | Файл | Проблема |
|---|------|----------|
| L9 | `helpers.ts:1-4` | Local icon imports (acceptable fallback) |
| L10 | `landing/DemoBoard.tsx:3-6` | Direct icon imports (OK for demo) |
| L11 | `SettingsPage.tsx:31-33` | `fetchData` not in `useCallback` |
| L12 | `ProjectsPage.tsx:76-94` | Inline local components (OK) |
| L13 | `QuarterlyPlanningPage.tsx` | 614-line monolith |
| L14 | `TeamMembersPage.tsx:12-16` | Hardcoded `SA/DEV/QA` в DEFAULT_PLANNING_CONFIG |
| L15 | `DataQualityPage.tsx` | Mixed languages (RU + EN) |
| L16 | `WorkflowConfigPage.tsx` | 1900+ lines — monolithic |

---

## Good Practices

- **Backend:** Правильная schema-per-tenant архитектура, JiraConfigResolver для большинства Jira-запросов, параметризованные SQL-запросы (нет SQL injection), rate limiting и security headers
- **Frontend:** StatusBadge и TeamBadge переиспользуются в большинстве мест, getIssueIcon() с Jira URL приоритетом, WorkflowConfigContext правильно загружает конфиг из backend
- **Design System:** В целом правила соблюдаются — нарушения точечные, не системные
- **Tests:** JUnit 5 тесты для основных сервисов
