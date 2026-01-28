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
│   └── metrics/            — Компоненты метрик (MetricCard, ThroughputChart, LtcGauge, etc.)
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

| Путь | Страница |
|------|----------|
| `/` | BoardPage |
| `/timeline` | TimelinePage |
| `/metrics` | TeamMetricsPage |
| `/data-quality` | DataQualityPage |
| `/teams` | TeamsPage |
| `/teams/:id` | TeamMembersPage |
| `/poker` | PlanningPokerPage |
| `/poker/:id` | PokerRoomPage |

## Паттерны

- **API proxy**: Vite проксирует `/api/*` на `localhost:8080` (vite.config.ts)
- **Прямые fetch вызовы**: без axios/React Query, plain fetch + async/await
- **Inline стили + App.css**: нет CSS-in-JS или CSS modules
- **Компоненты-страницы**: основная логика в page-компонентах (монолитные)

## Цветовая схема ролей
- SA — синий (#0052cc)
- DEV — зелёный (#36b37e)
- QA — оранжевый (#ff991f)
