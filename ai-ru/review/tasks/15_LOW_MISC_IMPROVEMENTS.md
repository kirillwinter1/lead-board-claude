# TASK: Low-priority improvements

**Priority:** Low
**Review IDs:** L1-L16

## Backend

| # | Файл | Проблема | Действие |
|---|------|----------|----------|
| L1 | WorkCalendarService.java:257 | Unimplemented loadFromFile() TODO | Реализовать или убрать CalendarSource.FILE |
| L2 | JiraIssueRepository.java | findByProjectKey() без пагинации | Добавить WHERE фильтр |
| L3 | ProjectService.java:54-84 | Cold cache N calculations | Добавить log для cache misses |
| L4 | VelocityService.java | Missing @Transactional(readOnly=true) | Добавить аннотацию |
| L6 | ProjectAlignmentService.java:165 | Full table scan | Кэшировать проекты в batch |
| L7 | AutoScoreCalculator.java:283-358 | Thread safety — instance fields в singleton | Move to local vars / ThreadLocal |
| L8 | BoardService.java | Нет lookback filter для done epics | Добавить конфигурируемый фильтр |

## Frontend

| # | Файл | Проблема | Действие |
|---|------|----------|----------|
| L11 | SettingsPage.tsx:31-33 | fetchData not useCallback | Wrap в useCallback |
| L13 | QuarterlyPlanningPage.tsx | 614-line monolith | Split на подкомпоненты |
| L14 | TeamMembersPage.tsx:12-16 | Hardcoded SA/DEV/QA defaults | Init from backend |
| L15 | DataQualityPage.tsx | Mixed RU/EN languages | Унифицировать |
| L16 | WorkflowConfigPage.tsx | 1900+ lines | Split на подкомпоненты |
| M35 | Множество файлов | Hardcoded 'ru-RU' locale | Extract to config const |
