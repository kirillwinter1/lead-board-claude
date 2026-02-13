# Правила Lead Board

> Объединяет бизнес-правила (бывший PROJECT_RULES.md) и правила разработки

## Часть 1: Бизнес-правила

### Ключевые принципы

1. **Jira — единственный источник истины.** Lead Board НИКОГДА не модифицирует данные в Jira. Хранит локальный кэш + overlay-данные.
2. **Backend — единственный источник расчётов.** Frontend НЕ вычисляет метрики.
3. **Конфигурируемость.** НЕ хардкодить Jira-специфику (статусы, поля, типы задач).
4. **Роли, типы задач, статусы — ТОЛЬКО из БД.** Вся конфигурация workflow хранится в PostgreSQL (таблицы `workflow_roles`, `issue_type_mappings`, `status_mappings`, `link_type_mappings`). Доступ через `WorkflowConfigService`. **ЗАПРЕЩЕНО** хардкодить роли (SA/DEV/QA), типы задач (Epic/Story/Sub-task), статусы или pipeline в коде. Если нужно проверить тип — использовать `workflowConfigService.isEpic()`, `isStory()`, `isSubtask()`. Если нужны роли pipeline — `getRolesInPipelineOrder()`. Если нужен маппинг статуса — `categorize(status, issueType)`.

### Оценки и time logging ТОЛЬКО в Subtask

- Все оценки (Original Estimate) и time spent хранятся ТОЛЬКО на уровне Subtask
- Story БЕЗ subtasks с estimates = НЕ МОЖЕТ быть запланирована
- Estimate на уровне Story/Epic игнорируется

### Assignee ТОЛЬКО в Subtask

- Assignee назначается ТОЛЬКО на Subtask
- При планировании смотрим на assignee subtasks

### Иерархия задач

```
Epic (принадлежит команде через Team field)
└── Story / Bug
    └── Sub-task (Аналитика / Разработка / Тестирование)
```

### Единицы времени
- 1 человеко-день = 8 часов

### Прогресс
```
progress = min(logged_hours / estimated_hours, 1.0)
```

### Агрегация
- Sub-task → Story: сумма по ролям (динамические, из `workflow_roles`)
- Story → Epic: агрегация по ролям

### Pipeline планирования
Последовательность ролей определяется `sort_order` в таблице `workflow_roles`. Дефолт: SA → DEV → QA, но может быть любой набор ролей (UX → SA → DEV → DEVOPS → QA).

### Коэффициенты грейдов
- Senior: 0.8 (делает быстрее)
- Middle: 1.0 (базовый)
- Junior: 1.5 (делает медленнее)

### Флаги (Flagged)
- Flagged story → работа НЕ ведётся, понижаем приоритет, не включаем в планирование

### Блокировки
- "A blocks B" → A получает повышенный приоритет, B откладывается
- Топологическая сортировка для зависимостей

### Цвета ролей
Цвета хранятся в `workflow_roles.color`. Дефолтные: SA — синий (#3b82f6), DEV — зелёный (#10b981), QA — оранжевый (#f59e0b).

---

## Часть 2: Правила разработки

### Обязательно для каждой фичи

1. **Тесты обязательны** — JUnit5, покрывать основные сценарии и edge cases
2. **Запуск тестов** — `./gradlew test` перед коммитом, не коммитить с падающими тестами
3. **Документация** — обновить FEATURES.md, README.md, для крупных фич — спецификация
4. **Минимальное стабильное состояние** — проект собирается и запускается после каждой фичи

### Синхронизация с Jira

- Инкрементальная: JQL с `updated >= lastSyncTime`
- Upsert-логика
- Запрет параллельных синхронизаций
- НЕ перезаписывать локальные поля: role, grade, hoursPerDay, roughEstimateDays

### Валидация конфигурации

Порядок резолюции: ScopeConfig → CompanyConfig → System defaults

### API ошибки

- 400 — невалидный payload
- 401 — не аутентифицирован
- 403 — нет доступа
- 404 — не найдено
- 409 — конфликт
- 502 — Jira недоступна

### Работа с ассетами

**ЗАПРЕЩЕНО** без явного указания: модифицировать, заменять, переименовывать, удалять изображения.
Путь к иконкам: `frontend/src/assets/icons/`

### Чеклист завершения задачи

1. Добавлены тесты
2. Запущены тесты backend + frontend
3. Обновлена документация
4. Конфигурируемо (никаких хардкодов)
5. Миграции совместимы назад

---

## Часть 3: Доменная модель

### Иерархия организации
```
Company
└── JiraSpace (Jira-проект)
    └── Team
        └── TeamMember (roleCode: динамический из workflow_roles, grade: Junior|Middle|Senior, hoursPerDay)
```

### Роли RBAC (запланировано)
COMPANY_ADMIN > JIRA_SPACE_ADMIN > TEAM_LEAD > TEAM_MEMBER

### Workflows

См. [JIRA_WORKFLOWS.md](./JIRA_WORKFLOWS.md) для детального описания статусов Epic, Story, Subtask.
