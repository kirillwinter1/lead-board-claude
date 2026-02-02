# Настройка Jira Cloud для Lead Board

Пошаговая инструкция по настройке Jira Cloud проекта для работы с Lead Board.

## Содержание

1. [Типы задач](#типы-задач)
2. [Workflows](#workflows)
3. [Экраны (Screens)](#экраны-screens)
4. [Конфигурация полей](#конфигурация-полей)
5. [Кастомные поля](#кастомные-поля)
6. [Troubleshooting](#troubleshooting)

---

## Типы задач

### Используемые типы

| Тип | Уровень | Описание |
|-----|---------|----------|
| **Epic** | 1 | Крупная бизнес-инициатива |
| **Story** (История) | 0 | Пользовательская история |
| **Bug** (Баг) | 0 | Дефект |
| **Аналитика** | -1 (subtask) | Подзадача для SA |
| **Разработка** | -1 (subtask) | Подзадача для DEV |
| **Тестирование** | -1 (subtask) | Подзадача для QA |

### Иерархия

```
Epic (Эпик)
└── Story (История)
    ├── Аналитика (подзадача)
    ├── Разработка (подзадача)
    └── Тестирование (подзадача)
```

### Настройка

**Путь:** ⚙️ Settings → Issues → Issue types

1. Убедитесь что все типы созданы
2. Подзадачи (Аналитика, Разработка, Тестирование) должны иметь уровень -1

---

## Workflows

### Epic Workflow

```
НОВОЕ → REQUIREMENTS → ROUGH ESTIMATE → ЗАПЛАНИРОВАНО → DEVELOPING → E2E TESTING → ACCEPTANCE → ГОТОВО
```

| Статус | Описание |
|--------|----------|
| НОВОЕ | Эпик только создан |
| REQUIREMENTS | Пишутся бизнес-требования |
| ROUGH ESTIMATE | Грубая оценка трудозатрат |
| ЗАПЛАНИРОВАНО | Готов к разработке |
| DEVELOPING | Активная разработка |
| E2E TESTING | End-to-end тестирование |
| ACCEPTANCE | Приёмка заказчиком |
| ГОТОВО | Эпик закрыт |

### Story Workflow

```
НОВОЕ → ANALYSIS → ANALYSIS REVIEW → WAITING DEV → DEVELOPMENT → DEV REVIEW → WAITING QA → ТЕСТИРОВАНИЕ → TEST REVIEW → READY TO RELEASE → ГОТОВО
```

| Статус | Фаза | Роль |
|--------|------|------|
| НОВОЕ | - | - |
| ANALYSIS | SA | Аналитик |
| ANALYSIS REVIEW | SA | Аналитик |
| WAITING DEV | - | - |
| DEVELOPMENT | DEV | Разработчик |
| DEV REVIEW | DEV | Разработчик |
| WAITING QA | - | - |
| ТЕСТИРОВАНИЕ | QA | Тестировщик |
| TEST REVIEW | QA | Тестировщик |
| READY TO RELEASE | - | - |
| ГОТОВО | - | - |

### Sub-task Workflow

```
НОВОЕ → В РАБОТЕ → ПРОВЕРКА → ГОТОВО
```

### Настройка Workflow

**Путь:** ⚙️ Settings → Issues → Workflows

1. Создайте workflows для каждого типа
2. Добавьте статусы и переходы
3. Привяжите workflow к типу задачи через Workflow Scheme

---

## Экраны (Screens)

Экраны определяют какие поля показываются для каждого типа задачи.

### Схема экранов проекта

| Screen Scheme | Типы задач |
|---------------|------------|
| LB: Kanban Default Screen Scheme | История, подзадачи |
| LB: Kanban Bug Screen Scheme | Баг |
| LB: Epic Screen Scheme | Эпик |

### Создание отдельного экрана для Epic

**Путь:** ⚙️ Settings → Issues → Screens

#### Шаг 1: Создать экран

1. Нажмите **"Add screen"** (Добавить экран)
2. Имя: `LB: Epic Screen`
3. Описание: `Поля для эпиков`

#### Шаг 2: Добавить поля на экран

Добавьте поля:

| Поле | Обязательно | Описание |
|------|-------------|----------|
| Резюме (Summary) | ✅ | Название эпика |
| Описание (Description) | ✅ | Бизнес-требования |
| Приоритет (Priority) | ✅ | Используется в AutoScore |
| Start date | ❌ | Дата начала |
| Срок исполнения (Due date) | ❌ | Дедлайн, влияет на AutoScore |
| Исполнитель (Assignee) | ❌ | Ответственный |
| Team | ❌ | Кастомное поле — команда |

**НЕ добавляйте для Epic:**
- Story Points
- Sprint
- Time Tracking (Учёт времени)
- Компоненты (опционально)

#### Шаг 3: Создать Screen Scheme

**Путь:** ⚙️ Settings → Issues → Screen schemes

1. Нажмите **"Add screen scheme"**
2. Имя: `LB: Epic Screen Scheme`
3. Default screen: `LB: Epic Screen`

#### Шаг 4: Привязать к Issue Type Screen Scheme

**Путь:** ⚙️ Settings → Issues → Issue type screen schemes

1. Найдите `LB: Kanban Issue Type Screen Scheme`
2. Нажмите **"Configure"**
3. Нажмите **"Associate an issue type with a screen scheme"**
4. Issue type: **Эпик**
5. Screen scheme: **LB: Epic Screen Scheme**
6. Сохраните

### Итоговая структура

```
LB: Kanban Issue Type Screen Scheme
├── По умолчанию → LB: Kanban Default Screen Scheme
├── Эпик → LB: Epic Screen Scheme
├── Баг → LB: Kanban Bug Screen Scheme
├── История → LB: Kanban Default Screen Scheme
├── Аналитика → LB: Kanban Default Screen Scheme
├── Разработка → LB: Kanban Default Screen Scheme
└── Тестирование → LB: Kanban Default Screen Scheme
```

---

## Конфигурация полей

Конфигурация полей определяет обязательность и видимость полей.

### Схема конфигурации полей

| Field Configuration | Типы задач |
|--------------------|------------|
| Default Field Configuration | По умолчанию |
| Epic – Lead Board | Эпик |
| Story – Lead Board | История |
| Sub-task – Lead Board | Подзадачи |

### Настройка обязательности полей для Epic

**Путь:** ⚙️ Settings → Issues → Field configurations

1. Найдите `Epic – Lead Board`
2. Нажмите **"Configure"**
3. Включите **"Only required"** чтобы увидеть обязательные поля
4. **Выключите** обязательность для:
   - Компоненты (Component/s)
   - Учёт времени (Time Tracking)
   - Метки (Labels) — опционально
   - Связанные задачи — опционально

### Привязка конфигурации к проекту

**Путь:** Project Settings → Fields

1. Нажмите **"Actions"** → **"Use a different scheme"**
2. Выберите **"Lead Board – Field Scheme"**
3. Нажмите **"Associate"**

### Структура Field Configuration Scheme

```
Lead Board – Field Scheme
├── По умолчанию → Default Field Configuration
├── Эпик → Epic – Lead Board
├── История → Story – Lead Board
├── Аналитика → Sub-task – Lead Board
├── Разработка → Sub-task – Lead Board
└── Тестирование → Sub-task – Lead Board
```

---

## Кастомные поля

### Team (Команда)

Кастомное поле для привязки задачи к команде.

**Создание:**

1. **Путь:** ⚙️ Settings → Issues → Custom fields
2. Нажмите **"Create custom field"**
3. Тип: **Select list (single choice)** или **Team** (если доступно)
4. Имя: `Team`
5. Добавьте значения (команды):
   - Команда победителей
   - Красивые
   - и т.д.

**Настройка в Lead Board:**

Добавьте ID поля в `backend/.env`:
```bash
JIRA_TEAM_FIELD_ID=customfield_12345
```

Чтобы найти ID поля:
1. Откройте любую задачу
2. Нажмите **"..."** → **"Configure"**
3. В URL будет ID поля

### Rough Estimates (хранятся в Lead Board)

Грубые оценки (SA/DEV/QA дни) хранятся локально в Lead Board, не в Jira.

---

## Troubleshooting

### Ошибка: "Компоненты поле необходимо"

**Причина:** Поле "Компоненты" обязательно в конфигурации полей.

**Решение:**
1. ⚙️ Settings → Issues → Field configurations
2. Выберите конфигурацию для нужного типа задачи
3. Найдите "Компоненты" → выключите "Required"

### Ошибка: "Учет времени: Исходная оценка обязательно"

**Причина:** Time Tracking поля обязательны.

**Решение:**
1. ⚙️ Settings → Issues → Field configurations
2. Найдите "Учёт времени" или "Time Tracking"
3. Выключите "Required"

### Задача не появляется в Lead Board

**Проверьте:**

1. **Team field заполнен?**
   - Откройте задачу в Jira
   - Убедитесь что поле Team указано

2. **Синхронизация работает?**
   ```bash
   curl -X POST http://localhost:8080/api/sync/trigger
   curl http://localhost:8080/api/sync/status
   ```

3. **Статус не "Готово"?**
   - Задачи в статусе "Готово" скрываются

4. **Правильная команда выбрана в UI?**
   - Переключите фильтр команды

### Синхронизация падает с ошибкой FK constraint

**Причина:** Баг в порядке сохранения (исправлен 2026-02-01).

**Решение:** Обновите backend до последней версии.

---

## Навигация по настройкам Jira

### Глобальные настройки (Admin)

```
⚙️ Settings → Issues →
├── Issue types           # Типы задач
├── Issue type schemes    # Схемы типов
├── Workflows             # Рабочие процессы
├── Workflow schemes      # Схемы workflow
├── Screens               # Экраны
├── Screen schemes        # Схемы экранов
├── Issue type screen schemes  # Связь типов с экранами
├── Custom fields         # Кастомные поля
├── Field configurations  # Конфигурации полей
└── Field configuration schemes  # Схемы конфигураций
```

### Настройки проекта

```
Project Settings →
├── Details       # Основная информация
├── Issue types   # Типы задач проекта
│   ├── Types     # Список типов
│   ├── Layout    # Макет полей
│   ├── Screens   # Экраны
│   └── Fields    # Поля
├── Workflows     # Рабочие процессы
└── Components    # Компоненты
```

---

## История изменений

| Дата | Изменение |
|------|-----------|
| 2026-02-01 | Создание документа |
| 2026-02-01 | Настройка Epic Screen Scheme |
| 2026-02-01 | Настройка Field Configuration для Epic |
