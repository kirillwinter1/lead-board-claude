# TASK: Remove dead code

**Priority:** Medium
**Review IDs:** M12, M16, M2
**Files:**
- `MetricsQueryRepository.java` — `getMetricsByAssignee()` never called
- `TeamMetricsPage.tsx:26` — `WipHistoryChart` exported but never imported
- `JiraClient.java:257` — deprecated `createSubtask(3 args)` hardcodes "Sub-task"

## Рекомендация

- Удалить `getMetricsByAssignee()` из MetricsQueryRepository
- Удалить или сделать non-exported `WipHistoryChart` (или удалить полностью до необходимости)
- Удалить deprecated `createSubtask(parentKey, summary, projectKey)` если нет вызовов
