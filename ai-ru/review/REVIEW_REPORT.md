# Full Project Code Review Report

**Дата:** 2026-03-04
**Scope:** Весь проект (backend + frontend)
**Reviewer:** Claude Code Review Agents
**Последнее обновление статусов:** 2026-03-08

---

## Статистика

| Severity | Total | Fixed | Open |
|----------|-------|-------|------|
| Critical | **5** | 5 | 0 |
| High | **18** | 16 | 2 |
| Medium | **36** | 28 | 8 |
| Low | **16** | 0 | 16 |
| **Total** | **75** | **49** | **26** |

**Verdict:** 49 из 75 issues исправлены. Открыто 0 Critical, 2 High, 8 Medium, 16 Low.

---

## Critical (5) — все закрыты ✅

### ~~C1. [Backend] SimulationService TOCTOU race condition~~ ✅ FIXED
**Исправлено:** partial unique index `V50__simulation_running_unique_index.sql` + catch `DataIntegrityViolationException`.

### ~~C2. [Backend] SimulationScheduler ignores TenantContext~~ ✅ FIXED
**Исправлено:** итерирует tenants через `TenantRepository.findAllActive()`, TenantContext в finally.

### ~~C3. [Backend] Async SecurityContext propagation risk~~ ✅ NOT AN ISSUE
**Верифицировано:** все методы AuthorizationService синхронные, @Async не используется.

### ~~C4. [Frontend] isEpic() hardcodes "Epic"~~ ✅ FIXED
**Исправлено:** функция `isEpic()` удалена из helpers.ts, все вызовы используют `useWorkflowConfig().isEpic(type)`.

### ~~C5. [Frontend] triggerSync interval leak~~ ✅ FIXED
**Исправлено:** `clearInterval(pollInterval)` корректно вызывается при завершении sync.

---

## High (18)

### Backend (9)

| # | Файл | Проблема | Статус |
|---|------|----------|--------|
| H1 | `WorkflowConfigService.java` | Hardcoded `"DEV"` fallback | ⚠️ OPEN (fallback) |
| H2 | `WorkflowConfigService.java` | Hardcoded `"SA"`, `"QA"` fallbacks | ⚠️ OPEN (fallback) |
| H3 | `WorkflowConfigService.java` | Hardcoded `"Done"` / `"In Progress"` | ✅ FIXED ранее |
| H4 | `WorkflowConfigService.java` | Hardcoded `"Story"` fallback | ✅ FIXED ранее |
| H5 | `TeamMemberEntity.java` | `role = "DEV"` default | ✅ FIXED ранее |
| H6 | `PlanningConfigDto.java` | Hardcoded roles в `defaults()` | ✅ FIXED ранее |
| H7 | ~~`DataQualityService.java`~~ | ~~N+1 queries~~ | ✅ FIXED — batch `findByParentKeyIn()` |
| H8 | ~~`DsrService.java`~~ | ~~N+1 per-epic loop~~ | ✅ FIXED — pre-load stories/subtasks в maps |
| H9 | ~~`StoryPriorityService.java`~~ | ~~`findAll()` OOM risk~~ | ✅ FIXED — `findByParentKeyIsNotNullAndSubtaskFalse()` |

### Frontend (9)

| # | Файл | Проблема | Статус |
|---|------|----------|--------|
| H10 | ~~`DataQualityPage.tsx`~~ | ~~Локальный `SeverityBadge`~~ | ✅ FIXED — вынесен в компонент |
| H11 | ~~`DataQualityPage.tsx`~~ | ~~Локальный `SummaryCard`~~ | ✅ FIXED |
| H12 | ~~`ProjectsPage.tsx`~~ | ~~Hardcoded hex цвета~~ | ✅ FIXED — `constants/colors.ts` |
| H13 | ~~`DsrBreakdownChart.tsx`~~ | ~~`any` в CustomYTick~~ | ✅ FIXED |
| H14 | ~~`QuarterlyPlanningPage.tsx`~~ | ~~Double `as any`~~ | ✅ FIXED |
| H15 | ~~`WorkflowConfigPage.tsx`~~ | ~~`value: any`~~ | ✅ FIXED — union types |
| H16 | ~~`WorkflowConfigPage.tsx`~~ | ~~`catch (err: any)`~~ | ✅ FIXED — `catch (err: unknown)` |
| H17 | ~~`TeamMetricsPage.tsx`~~ | ~~Suppressed `exhaustive-deps`~~ | ✅ FIXED |
| H18 | ~~`DsrBreakdownChart.tsx`~~ | ~~`any` в bar click~~ | ✅ FIXED |

---

## Medium (36)

### Backend (14)

| # | Файл | Проблема | Статус |
|---|------|----------|--------|
| M1 | `SimulationPlanner.java` | Hardcoded `"Epic"` fallback | ⚠️ OPEN |
| M2 | `JiraClient.java` | Deprecated `createSubtask` hardcodes `"Sub-task"` | ⚠️ OPEN |
| M3 | `PokerController.java` | Hardcoded `ELIGIBLE_STATUSES` | ⚠️ OPEN |
| M4 | `PokerJiraService.java` | `@Value` bypasses JiraConfigResolver | ⚠️ OPEN |
| M5 | ~~`DsrService.java`~~ | ~~`new ObjectMapper()`~~ | ✅ FIXED |
| M6 | ~~`SimulationService.java`~~ | ~~`new ObjectMapper()`~~ | ✅ FIXED |
| M7 | ~~`SimulationDeviation.java`~~ | ~~Redundant `@Autowired`~~ | ✅ FIXED |
| M8 | ~~`EmbeddingService.java`~~ | ~~Field injection~~ | ✅ FIXED |
| M9 | ~~`RiceAssessmentService.java`~~ | ~~N+1 в `computeEffortAuto()`~~ | ✅ FIXED — batch load по уровням |
| M10 | ~~`AutoScoreService.java`~~ | ~~`save()` per epic~~ | ✅ FIXED — `saveAll()` |
| M11 | `SyncService.java` | `inheritTeamFromParent()` дважды | ⚠️ OPEN |
| M12 | ~~`MetricsQueryRepository.java`~~ | ~~Dead code~~ | ✅ FIXED — removed |
| M13 | ~~`TeamMetricsController.java`~~ | ~~Нет `@PreAuthorize`~~ | ✅ FIXED |
| M14 | ~~`PokerController.java`~~ | ~~Нет `@PreAuthorize`~~ | ✅ FIXED |

### Frontend (22)

| # | Файл | Проблема | Статус |
|---|------|----------|--------|
| M15 | `helpers.ts` | Hardcoded issue type names (fallback icons) | ⚠️ OPEN |
| M16 | ~~`TeamMetricsPage.tsx`~~ | ~~Dead `WipHistoryChart`~~ | ✅ FIXED |
| M17 | ~~`TeamMetricsPage.tsx`~~ | ~~`key={i}` chart~~ | ✅ FIXED |
| M18 | ~~`DataQualityPage.tsx`~~ | ~~`key={i}` violations~~ | ✅ FIXED |
| M19 | ~~`ProjectsPage.tsx`~~ | ~~`key={i}` recommendations~~ | ✅ FIXED |
| M20 | ~~`ChatWidget.tsx`~~ | ~~`key={i}` messages~~ | ✅ FIXED — stable `msg.id` |
| M21 | ~~`TimeInStatusChart.tsx`~~ | ~~`key={i}` SVG~~ | ✅ FIXED |
| M22 | ~~`WorkflowConfigPage.tsx`~~ | ~~`key={i}` wizard~~ | ✅ FIXED |
| M23 | ~~`ProjectTimelinePage.tsx`~~ | ~~`key={i}` headers~~ | ✅ FIXED |
| M24 | ~~`ProjectTimelinePage.tsx`~~ | ~~Clickable div~~ | ✅ FIXED — role/tabIndex/onKeyDown |
| M25 | ~~`QuarterlyPlanningPage.tsx`~~ | ~~Clickable div/span~~ | ✅ FIXED |
| M26 | ~~`ProjectsPage.tsx`~~ | ~~Clickable div~~ | ✅ FIXED |
| M27 | ~~`Modal.tsx`~~ | ~~Backdrop без role~~ | ✅ FIXED — `role="presentation"` |
| M28 | ~~`TeamsPage.tsx + TeamMembersPage.tsx`~~ | ~~`window.alert()`~~ | ✅ FIXED — error state UI |
| M29 | ~~`AbsenceTimeline.tsx`~~ | ~~Suppressed deps~~ | ✅ FIXED — correct deps `[]` |
| M30 | ~~`TimelinePage.tsx`~~ | ~~Suppressed `exhaustive-deps`~~ | ✅ FIXED — mountRef pattern |
| M31 | ~~`WorkflowConfigPage.tsx`~~ | ~~useEffect deps missing ref~~ | ✅ FIXED — added `ref` to deps |
| M32 | ~~`Layout.tsx`~~ | ~~Avatar `alt=""`~~ | ✅ FIXED — `alt={displayName}` |
| M33 | ~~`ProjectTimelinePage.tsx` + 2~~ | ~~Дублирование `lightenColor()`~~ | ✅ FIXED — в `constants/colors.ts` |
| M34 | ~~`ProjectGanttView.tsx`~~ | ~~Hardcoded progress colors~~ | ✅ FIXED |
| M35 | ~~Множество файлов~~ | ~~Hardcoded `'ru-RU'`~~ | ✅ FIXED — `undefined` (browser locale) |
| M36 | ~~`TeamsPage.tsx`~~ | ~~Color picker без keyboard~~ | ✅ FIXED — role/tabIndex/onKeyDown |

---

## Low (16) — не исправлялись

### Backend (8)

| # | Файл | Проблема |
|---|------|----------|
| L1 | `WorkCalendarService.java` | Unimplemented `loadFromFile()` — TODO |
| L2 | `JiraIssueRepository.java` | `findByProjectKey()` без пагинации |
| L3 | `ProjectService.java` | Cold cache: N planning calculations |
| L4 | `VelocityService.java` | Missing `@Transactional(readOnly = true)` |
| L5 | `ChatToolExecutor.java` | N+1 в `teamList()` member count |
| L6 | `ProjectAlignmentService.java` | Full table scan на recalculate |
| L7 | `AutoScoreCalculator.java` | Thread safety risk в singleton |
| L8 | `BoardService.java` | Нет lookback filter для done epics |

### Frontend (8)

| # | Файл | Проблема |
|---|------|----------|
| L9 | `helpers.ts` | Local icon imports (acceptable fallback) |
| L10 | `landing/DemoBoard.tsx` | Direct icon imports (OK for demo) |
| L11 | `SettingsPage.tsx` | `fetchData` not in `useCallback` |
| L12 | `ProjectsPage.tsx` | Inline local components (OK) |
| L13 | `QuarterlyPlanningPage.tsx` | 614-line monolith |
| L14 | `TeamMembersPage.tsx` | Hardcoded `SA/DEV/QA` в planning config |
| L15 | `DataQualityPage.tsx` | Mixed languages (RU + EN) |
| L16 | `WorkflowConfigPage.tsx` | 1900+ lines — monolithic |

---

## Good Practices

- **Backend:** Schema-per-tenant, JiraConfigResolver, параметризованные SQL, rate limiting, security headers
- **Frontend:** StatusBadge/TeamBadge, getIssueIcon() с Jira URL, WorkflowConfigContext
- **Design System:** Правила соблюдаются, нарушения точечные
- **Tests:** JUnit 5 для основных сервисов
