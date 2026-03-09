# QA Report: F66 Jira Priority Icons on Board
**Дата:** 2026-03-09
**Тестировщик:** Claude QA Agent
**Версия:** 0.66.0

## Summary
- **Общий статус: PASS WITH ISSUES**
- Unit tests: backend компилируется, integration tests — pre-existing failures (не связаны с F66)
- Frontend build: PASS
- API tests: 1 issue (Jira API path)
- Visual: PASS — иконки отображаются корректно

## Bugs Found

### High

**BUG-H1: Jira API path `/rest/api/3/priority` deprecated — возвращает 404**
- **Шаги:** `GET /api/admin/jira-metadata/priorities` → backend вызывает Jira `/rest/api/3/priority`
- **Ожидаемый результат:** Список приоритетов с iconUrl
- **Фактический результат:** 500 Internal Server Error (Jira returns 404 Not Found)
- **Причина:** Jira Cloud deprecated `/rest/api/3/priority`, нужен `/rest/api/3/priority/search` (paginated, returns `{ values: [...] }`)
- **Влияние:** На инстансах без OAuth (Basic Auth) приоритеты не загружаются. На инстансах с OAuth — работает (tested on tenant `test2`)
- **Файл:** `JiraMetadataService.java:214` — `callJiraApiList("/rest/api/3/priority")` → нужно `callJiraApi("/rest/api/3/priority/search")` + extract `values`
- **Workaround:** Frontend gracefully degrades — `.catch(() => [])` в WorkflowConfigContext, борд загружается без иконок приоритетов

### Low

**BUG-L1: `getPriorityIconUrl()` вызывается дважды при рендере**
- **Шаги:** В BoardRow.tsx, line 54 и 56 — `getPriorityIconUrl(node.priority)` вызывается 2 раза (в condition и в src)
- **Влияние:** Минимальное — простой lookup в Record, но лучше кэшировать в переменную
- **Рекомендация:** `const prioIconUrl = getPriorityIconUrl(node.priority)` + использовать переменную

## Visual Review

### Board с развёрнутым эпиком
- [x] Priority icons отображаются между issue type icon и issue key
- [x] Tooltip (`title`) показывает название приоритета (Medium, High)
- [x] Иконки 16x16px, не нарушают layout
- [x] Разные иконки для разных приоритетов (medium_new.svg, high_new.svg)
- [x] Иконки загружаются полностью (`complete: true`, `naturalWidth: 16`)
- [x] 372 priority icons на полной доске (соответствует 372 issue type icons)
- [x] Graceful degradation — при ошибке API борд загружается без иконок

**Скриншоты:**
- `f66_board_priorities.png` — полная доска с иконками
- `f66_board_expanded_priority.png` — развёрнутый эпик, видны иконки на stories

### Данные
- DB: 481 задача в tenant_test2, все с приоритетами (High: 2, Medium: остальные)
- DOM: Medium=370, High=2 — совпадает

## Code Review

### Backend
- [x] `BoardNode.priority` — field + getter/setter ✓
- [x] `BoardService.mapToNode()` — `node.setPriority(entity.getPriority())` ✓
- [x] `JiraMetadataService.getPriorities()` — caching 60 min ✓, error handling ✓
- [x] `JiraMetadataController` — `@GetMapping("/priorities")` ✓
- [!] **API path issue** — `/rest/api/3/priority` deprecated (BUG-H1)

### Frontend
- [x] `BoardNode` type — `priority: string | null` ✓
- [x] `JiraPriorityMetadata` interface ✓
- [x] `WorkflowConfigContext` — `priorityIcons` map + `getPriorityIconUrl()` helper ✓
- [x] `Promise.all` — 4th request with `.catch(() => [])` ✓
- [x] Fallback context — `priorityIcons: {}`, `getPriorityIconUrl: () => null` ✓
- [x] `BoardRow` — conditional render `{node.priority && getPriorityIconUrl(...) && ...}` ✓
- [x] CSS `.priority-icon` — consistent with `.issue-type-icon` ✓
- [x] `ProjectsPage.tsx` — `priority: null` added ✓
- [!] Double call of `getPriorityIconUrl()` (BUG-L1)

### Null Safety
- [x] `node.priority` null → icon not rendered ✓
- [x] `getPriorityIconUrl(null)` → returns null ✓
- [x] API error → empty `priorityIcons` → no icons rendered ✓

## Test Coverage Gaps
- No unit tests for `getPriorities()` method in `JiraMetadataService`
- No frontend test for priority icon rendering in BoardRow
- Recommendation: add test for `getPriorities()` with mocked Jira response

## Recommendations
1. **Fix BUG-H1:** Change API path to `/rest/api/3/priority/search`, extract `values` from response
2. **Fix BUG-L1:** Cache `getPriorityIconUrl()` result in a const
3. Consider adding `onerror` handler on `<img>` to hide broken priority icons gracefully
