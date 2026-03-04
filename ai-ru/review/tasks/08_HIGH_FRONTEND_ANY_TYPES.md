# TASK: Replace `any` types in frontend

**Priority:** High
**Review IDs:** H13, H14, H15, H16, H18
**Files:**
- `DsrBreakdownChart.tsx:101` — `CustomYTick` props: any
- `DsrBreakdownChart.tsx:205` — `handleBarClick` data: any
- `QuarterlyPlanningPage.tsx:67` — double `as any` cast
- `WorkflowConfigPage.tsx:789-879` — 4× `value: any` в update functions
- `WorkflowConfigPage.tsx:717-999` — 7× `catch (err: any)`

## Проблема

`any` тип обходит TypeScript проверки и скрывает потенциальные баги. Catch blocks с `any` не позволяют корректно обработать ошибки.

## Рекомендация

- `CustomYTick` → определить `CustomYTickProps` interface
- `handleBarClick` → типизировать как `{ payload?: ChartRow }`
- QuarterlyPlanningPage → типизировать axios вызов: `axios.get<ProjectDto[]>`
- update functions → `value: DtoType[keyof DtoType]`
- catch blocks → `catch (err: unknown)` + narrowing через `err instanceof Error`
