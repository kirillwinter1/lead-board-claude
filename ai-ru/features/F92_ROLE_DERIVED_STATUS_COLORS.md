# F92 — Цвета статусов из цветов ролей + чистый статус-режим Timeline

**Версия:** 0.93.0 (release-ordered: 0.92.0 занята F84)
**Статус:** Реализовано (2026-07-22)
**Ветка:** `feat/f92-role-derived-status-colors` (stacked поверх `fix/timeline-logged-time-worklog-span`)

## Проблема

1. Цвета статусов в `status_mappings` — плоские категорийные дефолты (все IN_PROGRESS —
   `#DEEBFF`), поэтому режим «Story statuses» на Timeline бледный и нечитаемый, а бейджи
   на Board не несут информации о том, **чья фаза** (SA/DEV/QA) сейчас идёт.
2. Соответствие статус → роль уже есть в конфиге (`workflow_role_code`), но на цвет оно
   не влияет; связь с цветами ролей wizard'а отсутствует.
3. В статус-режиме Timeline рисуются и NEW, и DONE интервалы — бар начинается задолго до
   реального старта работ и не совпадает с внутренней семантикой борды.

## Решение (утверждённые развилки)

| Развилка | Решение |
|----------|---------|
| Охват новых цветов | **Везде** — единый resolved-цвет в `/api/config/status-styles`; StatusBadge, Board, DQ, Timeline перекрашиваются автоматически |
| Семантика waiting/review | Новое поле `status_kind` (`WORK` / `REVIEW` / `WAITING`) в `status_mappings`; правится в wizard; имена статусов в рантайм-логике не хардкодятся |
| «Waiting SA» (категория NEW у STORY) | Остаётся NEW → на Timeline скрыт; бар начинается с первого IN_PROGRESS-интервала |
| Оттенок Review | **Насыщеннее** роли (work — светлый тон роли, review — «пики» на баре) |
| Истина о цвете | **Вычисляемый цвет**; `color` — nullable override (NULL = derived из роль+kind) |

### Формула цвета (backend, единственный источник)

Для статуса **с ролью** (цвет роли из `workflow_roles.color`):

| kind | Цвет |
|------|------|
| `WORK` (и NULL) | светлый тон роли: mix(roleColor, white, ≈75% white) — тот же тон, что фазы Timeline |
| `REVIEW` | цвет роли как есть (насыщенный) |
| `WAITING` | серый токен `#DFE1E6` |

Статус **без роли**: `WAITING` → тот же серый; иначе — текущие категорийные дефолты
(NEW `#DFE1E6`, IN_PROGRESS `#DEEBFF`, DONE `#E3FCEF`, REQUIREMENTS `#E6FCFF`,
PLANNED `#EAE6FF`). Ручной `color` (не NULL) всегда побеждает.

Смена цвета роли в wizard мгновенно меняет все производные статусы.

## Backend

### 1. Миграции (`V53` public + `T16` tenant — номера перепроверить перед созданием)

- `ALTER TABLE status_mappings ADD COLUMN status_kind VARCHAR(16)` (nullable).
- Одноразовый seed `status_kind` по имени (только в миграции, это data-fix, не рантайм-логика):
  имя содержит `review`/`ревью`/`проверка` (case-insensitive) → `REVIEW`;
  содержит `waiting`/`ожидан` → `WAITING`; прочие IN_PROGRESS → `WORK`; NEW/DONE → NULL.
- Сброс override'ов: `UPDATE ... SET color = NULL WHERE color IN (<категорийные дефолты>)`
  — ручные цвета тенантов выживают, дефолтные становятся derived.

### 2. `WorkflowConfigService` / ConfigSnapshot

- Новый метод `resolveStatusColor(StatusMappingEntity)` по формуле выше; цвета ролей —
  из `workflow_roles` того же config'а. Хелпер смешивания цветов — маленький util
  (Java-аналог `lightenColor`).
- `PublicConfigController /api/config/status-styles`: отдаёт resolved-цвет в поле `color`
  (контракт фронта не меняется), плюс новое поле `statusKind` (additive).
- Admin CRUD конфига статусов: принимает/отдаёт `statusKind`; `color=null` в PUT означает
  «derived».

## Frontend

### 3. Timeline, режим «Story statuses» (`TimelinePage.tsx`)

- `StatusInterval` рендерится только если категория статуса ≠ NEW и ≠ DONE
  (категория берётся из `statusStyles[status].statusCategory` — уже в контракте).
- Границы бара в статус-режиме: от начала первого не-NEW интервала до конца последнего
  не-DONE интервала (для завершённой стори — момент перехода в DONE; для активной — today).
- Возвраты в NEW посреди истории просто не рисуются (серый трек).
- Цвета сегментов — как сейчас, через `resolveStatusBgColor` (получит новые derived-цвета
  автоматически). Статусы, отсутствующие в конфиге, — текущий fallback StatusBadge-палитры.
- Режим «Logged time» (F-фикс worklog-span) не меняется.
- **Уточнения по живому фидбеку 22.07:** (a) стори с пустым видимым статус-путём (вся
  история NEW/DONE или пустой changelog) не рисует прошлое; если это hybrid-стори с
  остатком работ — рисуется штрихованный остаток автопланера от today (статус лагает,
  но работа видна), завершённая — скрыта целиком; (b) штрихованный остаток рисуется в
  статус-режиме у всех hybrid-сторей (бар тянется до конца прогноза); (c) тултип Status
  path тоже НЕ показывает NEW-сегменты — Total пересчитывается по показанным, строка
  «Excl.» убрана (Board-тултип не тронут, проп `showExcl`).

### 3a. Тултип бара в статус-режиме — «Status path» как на Board (F81)

- В режиме «Story statuses» тултип бара стори заменяется на Board-стиль «Status path»:
  заголовок стори (иконка типа + ключ + summary, как `TooltipIssueHeader`), затем список
  сегментов «StatusBadge — время в статусе», метка `now` у текущего, снизу `Total` и
  `Excl. "<первый статус>"` — один в один как тултип `StatusAgeBadge` на Board.
- Данные — существующий API статус-истории (`getStatusHistory(issueKey)`, F81): точные
  длительности из `status_changelog`; грузится лениво при ховере с debounce, как на Board.
  Новых эндпоинтов не нужно.
- Рендер-блок «Status path» выносится из `StatusHistoryTooltip` в переиспользуемый
  компонент (например `StatusPathContent`), который используют и Board-тултип, и
  Timeline-тултип — без дублирования (правило реюза Design System).
- В тултипе показывается **полный** путь (включая New/Done — как на Board), даже если на
  баре NEW/DONE скрыты; итог `Excl.` компенсирует «лежание в New».
- В режиме «Logged time» тултип бара не меняется.

### 4. Wizard (`/workflow`, страница конфигурации статусов)

- Для IN_PROGRESS-статусов — селект **Kind**: Work / Review / Waiting (рядом с ролью);
  для NEW/DONE не показывается.
- ColorPicker: показывает вычисленный цвет как текущий; ручной выбор пишет override;
  кнопка сброса «↺ derived» (шлёт `color=null`).
- Изменение kind/роли сразу перерисовывает предпросмотр цвета: формула дублируется
  маленьким фронт-хелпером (`deriveStatusColor(roleColor, kind, category)`), истина
  после сохранения всё равно приходит из `/status-styles`.

## Тесты

- **Backend:** юнит-матрица `resolveStatusColor` (роль × kind × override × без роли),
  контроллер `/status-styles` (resolved-цвет + statusKind), seed-миграция на копии данных.
- **Frontend:** Timeline — NEW/DONE скрыты, границы бара по не-NEW/не-DONE интервалам,
  цвет сегмента = derived; тултип статус-режима — Status path (лениво, сегменты и Total);
  wizard — селект kind, override и сброс; `StatusPathContent` — общий для Board и Timeline.
- Полный прогон backend+frontend, live-QA на tenant_test2 (англ. статусы с ролями).

## Риски / заметки

- После деплоя бейджи статусов у существующих тенантов сменят цвета «сами» — это цель
  (согласованность), но выглядит как редизайн; упомянуть в release-заметке.
- Дефолт серого (`#DFE1E6`) совпадает с текущим NEW-цветом — WAITING и NEW визуально
  близки; на Timeline NEW скрыт, на Board это ок (оба «не активная работа»).
- Мультипроектные конфиги (F48): resolver работает на уровне config'а, per-project
  конфиги получают derived-цвета от своих ролей автоматически.
