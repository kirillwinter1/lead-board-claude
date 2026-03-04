# TASK: Replace key={index} with stable keys

**Priority:** Medium
**Review IDs:** M17-M23
**Files:**
- `TeamMetricsPage.tsx:136-191` — key={i} на chart элементах → key={value}
- `DataQualityPage.tsx:153` — key={i} на violations → key={v.rule}
- `ProjectsPage.tsx:149` — key={i} на recommendations → key={r.message}
- `ChatWidget.tsx:322` — key={i} на messages → key={msg.timestamp} (ВАЖНО для streaming!)
- `TimeInStatusChart.tsx:96-199` — key={i} → key={item.status}
- `WorkflowConfigPage.tsx:1145-1720` — key={i} → key={role.code} / key={it.jiraTypeName}
- `ProjectTimelinePage.tsx:683-707` — key={i} → key={h.date.toISOString()}

## Проблема

`key={index}` при динамических списках вызывает неправильный DOM reconciliation. Особенно критично для ChatWidget (streaming updates) и WorkflowConfigPage (add/remove items).
