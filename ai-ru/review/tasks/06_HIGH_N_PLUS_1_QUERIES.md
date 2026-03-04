# TASK: Fix N+1 query patterns in backend

**Priority:** High
**Review IDs:** H7, H8, H9, M9, M10, L5
**Files:**
- `DataQualityService.java:496-560` — findByParentKey в циклах, recursive findByIssueKey
- `DsrService.java:189-238` — findByParentKey + findByParentKeyIn в per-epic loop
- `StoryPriorityService.java:101` — `findAll()` без фильтрации (OOM risk)
- `RiceAssessmentService.java:101-123` — findByParentKey в цикле computeEffortAuto()
- `AutoScoreService.java:50-55` — `save()` per epic в цикле
- `ChatToolExecutor.java:155-169` — count members per team в цикле

## Проблема

На бордах с 50+ эпиками и множеством стори, один вызов DataQuality/DSR может генерировать сотни SQL-запросов. StoryPriorityService загружает ВСЮ таблицу jira_issues в память.

## Рекомендация

1. **DataQualityService / DsrService:** Pre-load все children разом через `findByParentKeyIn(epicKeys)`, сгруппировать в Map по parent key
2. **StoryPriorityService:** Добавить `findAllStoriesAndBugs()` с WHERE board_category IN ('STORY','BUG')
3. **RiceAssessmentService:** Batch-load subtasks через `findByParentKeyIn(childKeys)`
4. **AutoScoreService:** Использовать `saveAll(epics)` вместо save() в цикле
5. **ChatToolExecutor:** Один aggregate query: `SELECT team_id, COUNT(*) FROM team_members WHERE active GROUP BY team_id`
