# TASK: Fix JiraProperties direct usage + ObjectMapper

**Priority:** Medium
**Review IDs:** M4, M5, M6
**Files:**
- `PokerJiraService.java:27-28` — `@Value("${jira.project-key}")` bypasses JiraConfigResolver
- `DsrService.java:64` — `new ObjectMapper()` instead of Spring bean
- `SimulationService.java` — `new ObjectMapper()` instead of Spring bean

## Проблема

1. `PokerJiraService` читает project key напрямую из properties, игнорируя tenant DB config
2. Два сервиса создают собственный ObjectMapper вместо использования Spring bean

## Рекомендация

1. Inject `JiraConfigResolver` в PokerJiraService, вызывать `jiraConfigResolver.getProjectKey()`
2. Inject Spring-managed `ObjectMapper` bean через конструктор в DsrService и SimulationService
