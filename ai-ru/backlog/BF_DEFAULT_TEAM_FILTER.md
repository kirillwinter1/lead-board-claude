# BF. Default-фильтр по команде тимлида

## Контекст

Сейчас все страницы (Board, Projects, Quarterly Planning, Metrics) показывают данные по всем командам. Тимлид заинтересован видеть только свою команду — фильтрация занимает лишние клики каждый раз.

## Идея

При логине тимлида (роль `TEAM_LEAD` или member с `is_team_lead = true`) — все страницы по умолчанию фильтруются на его команду. Заглушка «my team» в каждом фильтре. Тимлид может явно снять фильтр, чтобы увидеть глобальную картину.

## Требования

- Поле `team_lead` на `team_members` или `team.leadMemberId` на `teams` (зависит от того, может ли быть несколько тимлидов одной команды)
- Каждая страница с team-фильтром (Board, Projects, Timeline, Quarterly Planning, Metrics, Pipeline) — на mount проверяет, есть ли у текущего пользователя «своя команда», и если есть — применяет фильтр
- Persist user override: если пользователь снял фильтр в текущей сессии — не возвращать его автоматически
- ADMIN/PM по умолчанию без фильтра (видят всё)

## Затрагиваемые экраны

- BoardPage
- ProjectsPage
- TimelinePage
- QuarterlyPlanningPage
- TeamMetricsPage
- (PipelinePage если будет)

## Связано с

- F69 Quarterly Planning Redesign — тимлид-центричный экран
- F70 Customer Quarter Planning — desired_quarter от заказчика
