# F47 — Setup Wizard Improvements

**Статус:** ✅ Готово
**Версия:** 0.47.0
**Дата:** 2026-02-28

## Описание

4 улучшения Setup Wizard, обнаруженные при E2E-тестировании multi-tenancy (F44).

## Изменения

### Fix 1: Убран Team Field ID из визарда, авто-определение

- `JiraMetadataService.getCustomFields(keyword)` — получает кастомные поля Jira, фильтрует по ключевому слову
- `GET /api/admin/jira-metadata/custom-fields?keyword=team` — новый endpoint
- При сохранении Jira-конфига: если teamFieldId пустой, автоматически ищет поле с "team" в названии
- Если найдено ровно 1 совпадение → устанавливает его автоматически
- Убрано поле Team Field ID из интерфейса визарда (шаг 1)

### Fix 2: Типы задач на шаге 2 (Period)

- При переходе на шаг 2 загружаются типы задач из `GET /api/admin/jira-metadata/issue-types`
- Отображаются как бейджи с иконками Jira и пометкой (subtask)
- Информационное отображение, без изменений бэкенда

### Fix 3: Pipeline hint для ролей

- В таблице ролей (и в визарде, и в WorkflowConfigPage) добавлен текст-подсказка: "Sort order defines the pipeline sequence"
- Строки таблицы сортируются по sortOrder (ранее — по порядку в массиве)
- Визуальный превью пайплайна: `SA → DEV → QA` с цветами ролей

### Fix 4: setup_completed флаг (Browser back/forward fix)

- **Проблема:** после sync (шаг 3) устанавливался `lastSyncCompletedAt`, и навигация назад/вперёд пропускала шаги 4-5
- **Решение:** новый флаг `setup_completed` в `tenant_jira_config`
- Миграция T4: `ALTER TABLE tenant_jira_config ADD COLUMN setup_completed BOOLEAN NOT NULL DEFAULT FALSE`
- `POST /api/jira-config/setup-complete` — устанавливает флаг
- `Layout.tsx`: проверяет `setupCompleted` вместо `lastSyncCompletedAt`
- `SetupWizardPage`: при нажатии "Go to Board" вызывает setup-complete endpoint
- При монтировании: если `setupCompleted === true`, пропускает визард

## Файлы

### Backend
- `config/service/JiraMetadataService.java` — getCustomFields()
- `config/controller/JiraMetadataController.java` — custom-fields endpoint
- `tenant/TenantJiraConfigEntity.java` — setupCompleted field
- `tenant/TenantJiraConfigController.java` — setup-complete endpoint, auto-detect, GET response
- `sync/SyncService.java` — SyncStatus record + getSyncStatus() с setupCompleted
- `db/tenant/T4__add_setup_completed.sql` — миграция

### Frontend
- `pages/SetupWizardPage.tsx` — убран teamFieldId, issue types на шаге 2, setup-complete
- `pages/WorkflowConfigPage.tsx` — pipeline hint, sorted roles
- `components/Layout.tsx` — wizard gate по setupCompleted
