# Lead Board — Документация

## Что такое Lead Board?

SaaS-продукт для управления IT-доставкой поверх Jira. Отвечает на вопросы:
- Сколько работы реально есть?
- Успеем ли доставить в квартале?
- Где переоцениваем или недооцениваем?

**Пользователи:** Team Leads, Engineering Managers, Product Managers, Delivery Managers

## Содержание

| Документ | Описание |
|----------|----------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Карта кодовой базы (пакеты, сервисы, entities, API, frontend) |
| [FEATURES.md](FEATURES.md) | Индекс фич со статусами и ссылками на спецификации |
| [RULES.md](RULES.md) | Бизнес-правила + правила разработки |
| [TECH_DEBT.md](TECH_DEBT.md) | Технический долг и архитектурные проблемы |
| [JIRA_WORKFLOWS.md](JIRA_WORKFLOWS.md) | Jira workflows (Epic, Story, Subtask) |
| [API_PLANNING.md](API_PLANNING.md) | API Planning документация |
| [ROADMAP_V2.md](ROADMAP_V2.md) | Роадмап будущих фич (F24-F29) |
| [features/](features/) | Детальные спецификации фич (F14-F23) |
| [archive/](archive/) | Устаревшие документы |

## Текущие страницы UI

| Путь | Страница | Описание |
|------|----------|----------|
| `/` | Board | Доска Epic→Story→Subtask с прогрессом, оценками, alerts |
| `/timeline` | Timeline | Gantt-диаграмма с фазами SA/DEV/QA |
| `/metrics` | Metrics | Командные метрики: Delivery Speed Ratio, throughput, forecast accuracy |
| `/data-quality` | Data Quality | Отчёт о качестве данных (17 правил) |
| `/teams` | Teams | Управление командами |
| `/teams/:id` | Members | Участники, planning config |
| `/poker` | Poker | Planning Poker (лобби + комнаты) |

## Технологии

| Компонент | Технология |
|-----------|------------|
| Backend | Java 21, Spring Boot 3, PostgreSQL, Flyway |
| Frontend | React 18, TypeScript, Vite |
| Auth | Atlassian OAuth 2.0 (3LO) |
| Infra | Docker Compose |

## Быстрый старт

1. Прочитай [RULES.md](RULES.md) — ключевые принципы
2. Прочитай [ARCHITECTURE.md](ARCHITECTURE.md) — карта кодовой базы
3. Смотри [FEATURES.md](FEATURES.md) — что реализовано и что планируется
