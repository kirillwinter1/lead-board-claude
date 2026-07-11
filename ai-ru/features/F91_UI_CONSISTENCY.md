# F91: UI Consistency Pass

**Версия:** 0.91.0
**Статус:** Реализовано
**Дата спеки:** 2026-07-11
**Тип:** Frontend-only
**Ветка:** `feat/f91-ui-consistency`

## Контекст

Визуальный аудит продукта 2026-07-07 нашёл: (1) реальные визуальные баги
(неверные цвета статусов, легенда не совпадающая с графиком, ложный «успех»
на форме лендинга при сетевой ошибке), (2) массовое дублирование одних и тех
же примитивов (бейджи ролей/грейдов, прогресс-бары, empty state, color picker,
тёмные тултипы) с разной вёрсткой в каждом файле, (3) остатки русского текста
в англоязычных экранах продукта, (4) точечные хардкод-цвета мимо
`constants/colors.ts`, (5) проблемы доступности (icon-only кнопки без
aria-label, `alert()` вместо инлайн-ошибок, `display:none` вместо
visually-hidden на клавиатурно-доступных полях), (6) устаревший/дублированный
CSS (в т.ч. утечка `.status-badge` в несколько файлов).

F91 закрывает всё это одним фронтенд-проходом без изменений API/данных.

## Что сделано

### 1. Визуальные баги
- **MemberProfilePage** — статусы задач рендерились фиксированным CSS-классом
  (цвет мог не совпадать с реальным статусом); теперь через `StatusBadge` +
  `StatusStylesProvider` (стили статусов подгружаются `getStatusStyles()`).
- **TrendChart** (member) — легенда («DSR» / «Tasks closed») использовала
  цвета, не совпадающие с фактической заливкой графика; выровнены через
  токены `LINK_COLOR` / `CHART_GRID`.
- **Landing AuditModal** — при ошибке отправки формы аудита модалка ранее
  всё равно показывала «заявка отправлена» (`setSubmitted(true)` в catch) —
  лид молча терялся. Теперь ошибка отображается в форме, ввод сохраняется,
  пользователь может повторить попытку.

### 2. Новые shared-компоненты (`frontend/src/components/`)
| Компонент | Назначение | Ключевое API |
|---|---|---|
| `RoleBadge` | Единый бейдж роли участника (SA/DEV/QA + кастомные) | `role: string`; цвет — `getRoleColor()` из `WorkflowConfigContext`, фон — `hexToRgba(color, 0.125)` |
| `GradeBadge` | Бейдж грейда (junior/middle/senior) | `grade: string`; цвета из `GRADE_COLORS`, неизвестный грейд → серый fallback |
| `ProgressBar` | Одна полоса прогресса с ARIA (`role="progressbar"`, valuemin/max/now) | `value, max=100, color?, trackColor?, height=6, width='100%', ariaLabel` (обязателен), `minFillPx=2`; эталон — `ProjectCommitmentView` |
| `EmptyState` | Единый «нет данных» | `message, hint?, icon?, variant='page'|'inline', children?`; заменил ~30 точечных `.empty*` блоков |
| `ColorPicker` | Единый color picker (попап-палитра) | `value, onChange, palette, columns=6, swatchShape='circle'|'square', trigger?, closeOnSelect=true, ariaLabel`; объединил 3 копии (TeamsPage inline popup + 2 реализации в WorkflowConfigPage) |
| `DarkTooltip` | Портальный тёмный тултип (навигационная палитра) + подкомпоненты `.Title/.Label/.Value/.Divider/.Progress` | `top, left, minWidth=300, maxWidth=420, interactive=false`; используется Timeline (EpicLabel, StoryBars, RoughEstimateBars) |

Инвариант, зафиксированный в коде (`DarkTooltip.tsx`): светлые hover-карточки
Board (`IssueTooltip`/`ProjectTooltip`/`HoverInfoCard`, `StatusHistoryTooltip`)
и CSS-заякоренные тултипы со стрелкой (`MyWorklogCalendar`, `AbsenceTimeline`)
— это **намеренно другой** паттерн (белый фон, привязка к триггеру / ленивая
подгрузка) и НЕ должны переезжать на `DarkTooltip`.

`StatusBadge` получил новый проп `maxWidth` (обрезка длинных статусов с
эллипсисом) — Timeline передаёт `130`.

### 3. RU → EN
Переведены: Quarterly Planning, Matrix, `MemberProfilePage`, `TeamMembersPage`
(секция настроек планирования), общие тултипы (`StatusHistoryTooltip`,
`HoverInfoCard`, `ProjectTooltip`, `IssueTooltip`), `StatusAgeBadge` (`д`→`d`),
`formatDuration` (`д`/`ч`→`d`/`h`), админ-подсказки `WorkflowConfigPage`.
Соответствующие тесты обновлены под английские строки.

**Осознанно не тронуто:**
- `GuidePage` — билингвальна по замыслу;
- лендинг (`pages/landing/`) — RU by design (маркетинг для рынка СНГ);
- кириллический fallback-матчинг ролей в бэкенде (`jira-integration.md` —
  "Role mapping with Cyrillic: use fallback via `String.contains()`").

### 4. Палитра — всё на `constants/colors.ts`
Новые токены:
- `WARNING_ORANGE` — RICE mid-band, WARNING-severity текст/точки;
- `GRADE_COLORS` — цвета `GradeBadge` (перенесены буквально из
  `.grade-badge.*` в `TeamsPage.css`);
- `ABSENCE_COLORS` — переехал из `AbsenceModal` в constants (используется
  также `AbsenceTimeline`, `WorklogTimeline`, `MyWorklogCalendar`,
  `MemberProfilePage`, `MyWorkPage`);
- `TIMELINE_*` (muted Gantt palette) — `TIMELINE_PHASE_TINT=0.65`,
  `TIMELINE_PHASE_TINT_ROUGH=0.8`, `TIMELINE_ROLE_BORDER_TINT=0.5`,
  `TIMELINE_BAR_TRACK`, `TIMELINE_FLAGGED_BORDER`, `TIMELINE_BLOCKED_BORDER`,
  `TIMELINE_ROUGH_BG`, `TIMELINE_ROUGH_BADGE_BG`, `TIMELINE_ROUGH_BADGE_TEXT` —
  **инвариант, задокументированный прямо в токенах:** палитра Timeline
  намеренно приглушённей эквивалентов на Board — это дизайн-решение, а не
  недосмотр. Не «осветлять» Timeline при последующих правках без явного
  запроса на редизайн;
- `TOOLTIP_DANGER` — для `DarkTooltip.Progress` (over-logged).

Дедупликация: `SEVERITY_COLORS` теперь определён один раз в
`constants/colors.ts`, `SeverityBadge.tsx` реэкспортирует его (было отдельное
определение). Хаки вида `color + '20'` (псевдо-прозрачность через конкатенацию
строки) заменены на `hexToRgba()` по всему `src/`. `RegistrationPage` и
`SetupWizardPage` были на чужой синей палитре (`#2563eb` и т.п.) — переведены
на продуктовую (Atlassian-based). Опечатка `#E9F2FE`→`#E9F2FF` устранена везде,
где встречалась. На лендинге три независимых набора цветов ролей сведены к
одной продуктовой палитре.

### 5. Доступность (a11y)
- `aria-label` на icon-only кнопках: `Modal` (close), `ChatWidget`, `PokerRoom`,
  `WorkflowConfigPage`.
- Клавиатурная доступность `CompetencyRating` и RICE-инпутов — `display:none`
  заменён на visually-hidden паттерн (элемент остаётся в DOM/tab-order для
  скринридеров и клавиатуры).
- Инлайн-ошибки вместо `alert()` — `EpicRoleChip`, `PlanningRoleChip`.

### 6. CSS-гигиена
- Суммарно ≈ −1180 строк мёртвого/дублированного CSS:
  `TimelinePage.css` 1502 → 449 строк (`git diff --stat`: −1058);
  `BoardPage.css` −272 строки.
- `.status-badge` больше не дублируется в нескольких CSS-файлов — единственное
  определение осталось в `BoardPage.css`; конфликтующий костыль в
  `QuarterlyPlanningPage.css` удалён (проблема решена пропом `StatusBadge.maxWidth`
  вместо CSS-переопределения).
- `TeamMembersPage` получил собственный `TeamMembersPage.css` — 42 правила,
  ранее жившие (и конфликтовавшие) в `BoardPage.css`, выделены в отдельный файл.
- `AlertIcon` — эмодзи заменены на `SeverityDot`, цвета из `SEVERITY_COLORS`.
- Покерный `SessionStatusBadge` больше не коллидирует по имени класса с
  глобальным `.status-badge`.

## Новые shared-примитивы, обязательные к использованию

См. таблицу выше (`RoleBadge`, `GradeBadge`, `ProgressBar`, `EmptyState`,
`ColorPicker`, `DarkTooltip`). Правило зафиксировано в
`.claude/skills/desktop-ui-consistency/SKILL.md` — перед добавлением
локального бейджа/прогресс-бара/empty-state/color-picker/тёмного тултипа
проверить, нет ли уже готового.

## Осознанно оставлено кастомным (не мигрировано на `ProgressBar`)

- **`CapacityBars`** (Quarterly Planning) — многосегментный (stacked) индикатор
  capacity/demand с несколькими цветными зонами; `ProgressBar` — одна заливка,
  не покрывает этот случай.
- **`ProjectGanttView`** — gantt-бары с позиционированием по датам и
  собственной логикой цвета по статусу/прогрессу; это не «progress bar» в
  смысле общего компонента, а визуализация временной шкалы.

Оба зафиксированы как намеренное исключение прямо в JSDoc `ProgressBar.tsx`
("Stacked / multi-segment bars (CapacityBars, Gantt) intentionally stay custom").

## Ограничения / вне скоупа

- Лендинг (`pages/landing/`) остаётся русскоязычным — маркетинговое решение,
  не технический долг.
- `GuidePage` остаётся билингвальной.
- Бэкенд не тронут (0 backend-коммитов, 0 миграций) — это чисто frontend pass.
- Полная замена inline-стилей на CSS-классы не выполнялась целиком — только
  там, где дублирование/несогласованность были явными (см. коммиты).

## Изменения

### Backend
Нет.

### Frontend
- Новые компоненты: `RoleBadge.tsx`, `GradeBadge.tsx`, `ProgressBar.tsx`,
  `EmptyState.tsx` (+`.css`), `ColorPicker.tsx` (+`.css`), `DarkTooltip.tsx`
  (+тест `DarkTooltip.test.tsx`).
- `constants/colors.ts` — новые токены (см. раздел «Палитра»), `SEVERITY_COLORS`
  перенесён из `SeverityBadge.tsx`.
- ~102 изменённых файла (страницы, компоненты metrics/planning/projects/rice,
  landing), diff: +1645 / −2463 строк.
- Затронутые страницы: `MemberProfilePage`, `MatrixPage`, `QuarterlyPlanningPage`,
  `TeamMembersPage`, `TeamsPage`, `WorkflowConfigPage`, `TimelinePage`,
  `BoardPage`, `MyWorkPage`, `ProjectsPage`, `RegistrationPage`,
  `SetupWizardPage`, `PlanningPokerPage`/`PokerRoomPage`, `landing/*`.

## API Endpoints

Нет новых — фича не затрагивает backend/API.

## Конфигурация

Нет новых переменных окружения/конфигурации.

## Тесты

- Frontend: 321 green (включая новый `DarkTooltip.test.tsx`) + `npm run build` OK.
- Backend: полный suite не менялся (backend не тронут).
- Ревью: APPROVED WITH NOTES — 2 High-замечания исправлены (`c6cc43d`), затем
  доп. дедуп `SEVERITY_COLORS` и токенизация точечных литералов (`6d1a4db`).

## Связанные документы

`.claude/rules/design-system.md` (правила дизайн-системы, дополнены разделом
про сегментные цвета charts — прецеденты `DsrBreakdownChart`, F87 `StoryBar`),
`.claude/skills/desktop-ui-consistency/SKILL.md` (обновлён этим коммитом).
