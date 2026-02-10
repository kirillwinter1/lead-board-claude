# F1. Bootstrap проекта

## Обзор

Инициализация монорепозитория с базовой инфраструктурой для SaaS-приложения управления IT-доставкой.

## Tech Stack

### Backend
- **Java 21** + **Spring Boot 3.2.5** (Gradle Kotlin DSL)
- **PostgreSQL 15** — основная БД
- **Flyway 10.10.0** — миграции (включая `flyway-database-postgresql`)
- **Spring Data JPA + Hibernate** — ORM (`ddl-auto: validate`)
- **Spring Security** — аутентификация/авторизация
- **Spring WebFlux (WebClient)** — HTTP-клиент для Jira API
- **Spring WebSocket** — real-time (Planning Poker)
- **spring-dotenv** — загрузка переменных из `.env`
- **JUnit 5 + Mockito** — тестирование
- **Testcontainers 2.0.3** — интеграционные тесты

### Frontend
- **React 18** + **TypeScript 5.4** + **Vite 5.2**
- **Recharts 3.7** — графики
- **Framer Motion 12.29** — анимации
- **React Router DOM 7.12** — роутинг
- **dnd-kit** — drag & drop
- **Axios 1.6** — HTTP-клиент

### Деплой
- **Docker** — multi-stage build (Gradle → JRE 21 alpine, Node → Nginx alpine)
- **Docker Compose** — оркестрация
- **Nginx** — reverse proxy (production)

## Структура проекта

```
lead-board-claude/
├── backend/              # Spring Boot
│   ├── src/main/java/com/leadboard/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/     # Flyway
│   ├── build.gradle.kts
│   └── Dockerfile
├── frontend/             # React + Vite
│   ├── src/
│   ├── package.json
│   └── Dockerfile
├── ai-ru/                # Документация
├── docker-compose.yml    # Dev
└── docker-compose.prod.yml  # Production
```

## Начальная схема БД

**V1** — `jira_issues` (кэш задач Jira), `jira_sync_state` (состояние синхронизации)
**V2** — `users` (пользователи), `oauth_tokens` (OAuth токены Atlassian)

## Ключевые решения

- **Монорепозиторий** — backend и frontend в одном репо для простоты
- **Flyway + validate** — миграции применяются при старте, Hibernate проверяет соответствие
- **Multi-stage Docker** — минимальный размер образов, non-root пользователь
- **JVM memory** — 75% доступной RAM контейнера
- **Vite proxy** — `/api/*` проксируется на backend:8080 в dev-режиме
