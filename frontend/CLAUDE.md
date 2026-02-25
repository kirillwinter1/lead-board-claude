# Frontend — Lead Board

## Технологии
- React 18 + TypeScript + Vite
- Без стейт-менеджера (useState + props)
- CSS modules (App.css + inline)

## Запуск
```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend
npm run dev      # запуск на порту 5173
npm run build    # сборка
```

## Структура

```
src/
├── api/            — API клиенты (8 модулей)
│   ├── board.ts        — /api/board
│   ├── forecast.ts     — /api/planning/*
│   ├── teams.ts        — /api/teams/*
│   ├── metrics.ts      — /api/metrics/*
│   ├── poker.ts        — /api/poker/*
│   ├── stories.ts      — /api/stories/*, /api/epics/*/stories
│   ├── epics.ts        — /api/epics/*
│   └── config.ts       — /api/config/*
├── components/
│   ├── Layout.tsx          — Навигация (табы: Board, Timeline, Metrics, etc.)
│   ├── Modal.tsx           — Переиспользуемый модальный диалог
│   ├── MultiSelectDropdown.tsx
│   └── metrics/            — Компоненты метрик (MetricCard, ThroughputChart, DsrGauge, etc.)
├── hooks/
│   └── usePokerWebSocket.ts — WebSocket для Planning Poker
├── pages/                  — 8 страниц
│   ├── BoardPage.tsx       — Основная доска (1798 LOC)
│   ├── TimelinePage.tsx    — Gantt-диаграмма (1126 LOC)
│   ├── TeamMetricsPage.tsx — Метрики команды
│   ├── DataQualityPage.tsx — Качество данных
│   ├── TeamsPage.tsx       — Список команд
│   ├── TeamMembersPage.tsx — Участники + planning config
│   ├── PlanningPokerPage.tsx — Лобби Poker
│   └── PokerRoomPage.tsx   — Комната Poker
├── icons/              — SVG иконки Jira
├── App.tsx             — Роутинг (react-router-dom)
└── main.tsx            — Entry point
```

## Роутинг (App.tsx)

Все маршруты от корня `/` (без `/board` префикса). Субдомен определяет tenant.

| Путь | Страница | Доступ |
|------|----------|--------|
| `/` | BoardPage | Все авторизованные |
| `/timeline` | TimelinePage | Все авторизованные |
| `/metrics` | TeamMetricsPage | Все авторизованные |
| `/data-quality` | DataQualityPage | Все авторизованные |
| `/bug-metrics` | BugMetricsPage | Все авторизованные |
| `/poker` | PlanningPokerPage | Все авторизованные |
| `/poker/room/:roomCode` | PokerRoomPage | Все авторизованные |
| `/teams` | TeamsPage | Все авторизованные |
| `/teams/:teamId` | TeamMembersPage | Все авторизованные |
| `/teams/:teamId/member/:memberId` | MemberProfilePage | Все авторизованные |
| `/teams/:teamId/competency` | TeamCompetencyPage | Все авторизованные |
| `/projects` | ProjectsPage | Все авторизованные |
| `/project-timeline` | ProjectTimelinePage | Все авторизованные |
| `/settings` | SettingsPage | ADMIN only |
| `/workflow` | WorkflowConfigPage | ADMIN only |
| `/landing` | LandingPage | Публичная |
| `/register` | RegistrationPage | Публичная |

## Паттерны

- **API proxy**: Vite проксирует `/api/*` на `localhost:8080` (vite.config.ts)
- **Прямые fetch вызовы**: без axios/React Query, plain fetch + async/await
- **Inline стили + App.css**: нет CSS-in-JS или CSS modules
- **Компоненты-страницы**: основная логика в page-компонентах (монолитные)

## Цветовая схема ролей

| Роль | Текст | Прогресс-бар | Фон (light) |
|------|-------|--------------|-------------|
| SA   | #1558BC | #669DF1 | #E9F2FE |
| DEV  | #803FA5 | #B55FEB | #F8EEFE |
| QA   | #206A83 | #6CC3E0 | #E7F9FF |

Пример использования (см. `BoardPage.css` `.role-chip.*`).
