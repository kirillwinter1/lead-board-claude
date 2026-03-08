# Full Project Code Review Report

**Дата:** 2026-03-04
**Scope:** Весь проект (backend + frontend)
**Reviewer:** Claude Code Review Agents
**Последнее обновление статусов:** 2026-03-08

---

## Статистика

| Severity | Backend | Frontend | Total | Fixed |
|----------|---------|----------|-------|-------|
| Critical | 3 | 2 | **5** | 3 |
| High | 9 | 9 | **18** | 2 |
| Medium | 14 | 22 | **36** | 0 |
| Low | 8 | 8 | **16** | 0 |
| **Total** | **34** | **41** | **75** | **5** |

**Verdict: REQUEST CHANGES** — открыто 2 Critical + 16 High issues

---

## Critical (5)

### ~~C1. [Backend] SimulationService TOCTOU race condition~~ ✅ FIXED
**Файл:** `simulation/SimulationService.java`
~~`existsByStatus("RUNNING")` check не атомарен с insert.~~
**Исправлено:** добавлен partial unique index `V50__simulation_running_unique_index.sql` на `status = 'RUNNING'` + catch `DataIntegrityViolationException` при `saveAndFlush()`.

### ~~C2. [Backend] SimulationScheduler ignores TenantContext~~ ✅ FIXED
**Файл:** `simulation/SimulationScheduler.java`
~~Scheduler вызывает `simulationService.runSimulation()` без установки TenantContext.~~
**Исправлено:** итерирует tenants через `TenantRepository.findAllActive()`, ставит `TenantContext.setTenant()` перед каждым вызовом, очищает в finally.

### ~~C3. [Backend] Async SecurityContext propagation risk~~ ✅ NOT AN ISSUE
**Файл:** `auth/AuthorizationService.java`
~~`@Async` методы могут не иметь SecurityContext.~~
**Верифицировано:** все методы AuthorizationService синхронные, @Async не используется. Проблема не воспроизводится.

### C4. [Frontend] isEpic() hardcodes "Epic" ⚠️ OPEN
**Файл:** `frontend/src/components/board/helpers.ts:42-44`
```ts
export function isEpic(issueType: string): boolean {
  return issueType === 'Epic'
}
```
Тенанты с другим названием типа эпика будут работать некорректно.
→ Удалить `isEpic()` из helpers.ts, использовать `useWorkflowConfig().isEpic(type)`.

### ~~C5. [Frontend] triggerSync interval leak~~ ✅ FIXED
**Файл:** `frontend/src/hooks/useBoardData.ts`
~~`setInterval` для polling не отменяется при unmount.~~
**Исправлено:** `clearInterval(pollInterval)` корректно вызывается при завершении sync.

---

## High (18)

### Backend (9)

| # | Файл | Проблема | Статус |
|---|------|----------|--------|
| H1 | `WorkflowConfigService.java:335` | Hardcoded `"DEV"` fallback в `getDefaultRoleCode()` | ⚠️ OPEN (fallback) |
| H2 | `WorkflowConfigService.java:453-463` | Hardcoded `"SA"`, `"QA"` fallbacks в `determinePhase()` | ⚠️ OPEN (fallback) |
| H3 | `WorkflowConfigService.java:663-664` | Hardcoded `"Done"` / `"In Progress"` в `getFirstStatusNameForCategory()` | ⚠️ OPEN (fallback) |
| H4 | `WorkflowConfigService.java:744` | Hardcoded `"Story"` fallback в `getStoryTypeName()` | ⚠️ OPEN (fallback) |
| H5 | `TeamMemberEntity.java:26` | `private String role = "DEV"` — hardcoded default | ⚠️ OPEN |
| H6 | `PlanningConfigDto.java:57-81` | Hardcoded `"SA"`, `"DEV"`, `"QA"` в `defaults()` | ⚠️ OPEN |
| H7 | `DataQualityService.java:496-560` | N+1 queries (findByParentKey в цикле, recursive findByIssueKey) | ⚠️ OPEN |
| H8 | `DsrService.java:189-238` | N+1 pattern (findByParentKey + findByParentKeyIn в per-epic loop) | ⚠️ OPEN |
| H9 | `StoryPriorityService.java:101` | `issueRepository.findAll()` без фильтрации — OOM risk | ⚠️ OPEN |

### Frontend (9)

| # | Файл | Проблема | Статус |
|---|------|----------|--------|
| H10 | `DataQualityPage.tsx` | ~~Локальный `SeverityBadge` с hardcoded цветами~~ | ✅ FIXED — вынесен в отдельный компонент `SeverityBadge.tsx` |
| H11 | `DataQualityPage.tsx` | ~~Локальный `SummaryCard` — дубликат MetricCard~~ | ✅ FIXED — используется переиспользуемый компонент |
| H12 | `ProjectsPage.tsx:30-53` | Hardcoded progress bar цвета `#36B37E` / `#0065FF` | ⚠️ OPEN |
| H13 | `DsrBreakdownChart.tsx:101` | `any` type в CustomYTick props | ⚠️ OPEN |
| H14 | `QuarterlyPlanningPage.tsx:67` | Double `as any` при маппинге проектов | ⚠️ OPEN |
| H15 | `WorkflowConfigPage.tsx:789-879` | Множество `value: any` параметров в update-функциях | ⚠️ OPEN |
| H16 | `WorkflowConfigPage.tsx:717-999` | 7× `catch (err: any)` вместо `unknown` | ⚠️ OPEN |
| H17 | `TeamMetricsPage.tsx:287` | Suppressed `exhaustive-deps` — stale closure risk | ⚠️ OPEN |
| H18 | `DsrBreakdownChart.tsx:205` | `any` в bar click handler | ⚠️ OPEN |

---

## Medium (36)

### Backend (14)

| # | Файл | Проблема | Статус |
|---|------|----------|--------|
| M1 | `SimulationPlanner.java:340` | Hardcoded `"Epic"` fallback | ⚠️ OPEN |
| M2 | `JiraClient.java:257` | Deprecated `createSubtask` hardcodes `"Sub-task"` | ⚠️ OPEN |
| M3 | `PokerController.java:36-41` | Hardcoded `ELIGIBLE_STATUSES` list | ⚠️ OPEN |
| M4 | `PokerJiraService.java:27-28` | `@Value("${jira.project-key}")` bypasses JiraConfigResolver | ⚠️ OPEN |
| M5 | `DsrService.java:64` | `new ObjectMapper()` вместо Spring bean | ⚠️ OPEN |
| M6 | `SimulationService.java` | `new ObjectMapper()` вместо Spring bean | ⚠️ OPEN |
| M7 | `SimulationDeviation.java:14` | Redundant `@Autowired` | ⚠️ OPEN |
| M8 | `EmbeddingService.java:26` | Field injection `@Autowired(required = false)` | ⚠️ OPEN |
| M9 | `RiceAssessmentService.java:101-123` | N+1 в `computeEffortAuto()` | ⚠️ OPEN |
| M10 | `AutoScoreService.java:50-55` | `save()` per epic в цикле (no batch) | ⚠️ OPEN |
| M11 | `SyncService.java:334` | `inheritTeamFromParent()` вызван дважды без документации | ⚠️ OPEN |
| M12 | `MetricsQueryRepository.java` | Dead code: `getMetricsByAssignee()` не используется | ⚠️ OPEN |
| M13 | `TeamMetricsController.java` | Нет `@PreAuthorize` — любой user видит все метрики | ⚠️ OPEN |
| M14 | `PokerController.java` | Нет `@PreAuthorize` на мутирующих endpoints | ⚠️ OPEN |

### Frontend (22)

| # | Файл | Проблема | Статус |
|---|------|----------|--------|
| M15 | `helpers.ts:26-42` | Hardcoded issue type names в fallback map | ⚠️ OPEN |
| M16 | `TeamMetricsPage.tsx:26` | Dead export: `WipHistoryChart` не используется | ⚠️ OPEN |
| M17 | `TeamMetricsPage.tsx:136-191` | `key={i}` на chart элементах | ⚠️ OPEN |
| M18 | `DataQualityPage.tsx:153` | `key={i}` на violation rows | ⚠️ OPEN |
| M19 | `ProjectsPage.tsx:149` | `key={i}` на recommendations | ⚠️ OPEN |
| M20 | `ChatWidget.tsx:322` | `key={i}` на messages (streaming updates!) | ⚠️ OPEN |
| M21 | `TimeInStatusChart.tsx:96-199` | Multiple `key={i}` в SVG chart | ⚠️ OPEN |
| M22 | `WorkflowConfigPage.tsx:1145-1720` | Multiple `key={i}` / `key={idx}` | ⚠️ OPEN |
| M23 | `ProjectTimelinePage.tsx:683-707` | `key={i}` на timeline headers | ⚠️ OPEN |
| M24 | `ProjectTimelinePage.tsx:615` | Clickable div без `role="button"` / keyboard | ⚠️ OPEN |
| M25 | `QuarterlyPlanningPage.tsx:365,604` | Clickable div/span без accessibility | ⚠️ OPEN |
| M26 | `ProjectsPage.tsx:334-344` | Clickable div без keyboard handler | ⚠️ OPEN |
| M27 | `Modal.tsx:30` | Backdrop div без role/keyboard | ⚠️ OPEN |
| M28 | `TeamsPage.tsx + TeamMembersPage.tsx` | 9× `window.alert()`/`confirm()` | ⚠️ OPEN |
| M29 | `AbsenceTimeline.tsx:92` | Suppressed deps с confusing logic | ⚠️ OPEN |
| M30 | `TimelinePage.tsx:1530` | Suppressed `exhaustive-deps` | ⚠️ OPEN |
| M31 | `WorkflowConfigPage.tsx:104-113` | `useEffect` deps missing `ref` | ⚠️ OPEN |
| M32 | `Layout.tsx:112` | User avatar `alt=""` вместо `alt={displayName}` | ⚠️ OPEN |
| M33 | `ProjectTimelinePage.tsx:60` + 2 файла | Дублирование `lightenColor()` в 3 файлах | ⚠️ OPEN |
| M34 | `ProjectTimelinePage.tsx:529,851` | Hardcoded progress colors (duplicate H12) | ⚠️ OPEN |
| M35 | Множество файлов | Hardcoded `'ru-RU'` locale | ⚠️ OPEN |
| M36 | `TeamsPage.tsx:189-203` | Color picker span без keyboard access | ⚠️ OPEN |

---

## Low (16)

### Backend (8)

| # | Файл | Проблема | Статус |
|---|------|----------|--------|
| L1 | `WorkCalendarService.java:257` | Unimplemented `loadFromFile()` — TODO | ⚠️ OPEN |
| L2 | `JiraIssueRepository.java` | `findByProjectKey()` / `findByLabelsIsNotNull()` без пагинации | ⚠️ OPEN |
| L3 | `ProjectService.java:54-84` | Cold cache: N planning calculations on first request | ⚠️ OPEN |
| L4 | `VelocityService.java` | Missing `@Transactional(readOnly = true)` | ⚠️ OPEN |
| L5 | `ChatToolExecutor.java:155-169` | N+1 в `teamList()` member count | ⚠️ OPEN |
| L6 | `ProjectAlignmentService.java:165` | Full table scan на каждый recalculate | ⚠️ OPEN |
| L7 | `AutoScoreCalculator.java:283-358` | Thread safety risk — instance fields в singleton | ⚠️ OPEN |
| L8 | `BoardService.java` | Нет lookback filter для done epics | ⚠️ OPEN |

### Frontend (8)

| # | Файл | Проблема | Статус |
|---|------|----------|--------|
| L9 | `helpers.ts:1-4` | Local icon imports (acceptable fallback) | ⚠️ OPEN |
| L10 | `landing/DemoBoard.tsx:3-6` | Direct icon imports (OK for demo) | ⚠️ OPEN |
| L11 | `SettingsPage.tsx:31-33` | `fetchData` not in `useCallback` | ⚠️ OPEN |
| L12 | `ProjectsPage.tsx:76-94` | Inline local components (OK) | ⚠️ OPEN |
| L13 | `QuarterlyPlanningPage.tsx` | 614-line monolith | ⚠️ OPEN |
| L14 | `TeamMembersPage.tsx:12-16` | Hardcoded `SA/DEV/QA` в DEFAULT_PLANNING_CONFIG | ⚠️ OPEN |
| L15 | `DataQualityPage.tsx` | Mixed languages (RU + EN) | ⚠️ OPEN |
| L16 | `WorkflowConfigPage.tsx` | 1900+ lines — monolithic | ⚠️ OPEN |

---

## Good Practices

- **Backend:** Правильная schema-per-tenant архитектура, JiraConfigResolver для большинства Jira-запросов, параметризованные SQL-запросы (нет SQL injection), rate limiting и security headers
- **Frontend:** StatusBadge и TeamBadge переиспользуются в большинстве мест, getIssueIcon() с Jira URL приоритетом, WorkflowConfigContext правильно загружает конфиг из backend
- **Design System:** В целом правила соблюдаются — нарушения точечные, не системные
- **Tests:** JUnit 5 тесты для основных сервисов
