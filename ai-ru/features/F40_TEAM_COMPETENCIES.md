# F40 — Team Competencies (Компетенции команды)

**Дата:** 2026-02-19 (документация; код реализован ранее)

## Обзор

Матрица компетенций сотрудников по **компонентам** (техническим областям). Компоненты подтягиваются из Jira. Тимлид выставляет уровень экспертности (1-5) каждому сотруднику. Система рассчитывает bus-factor по областям.

## Что реализовано

### Шкала компетенций (5 уровней)

| Уровень | Название | Competency Factor |
|---------|----------|-------------------|
| 5 | Expert | 0.70 (на 30% быстрее) |
| 4 | Proficient | 0.85 |
| 3 | Competent (дефолт) | 1.00 |
| 2 | Beginner | 1.30 |
| 1 | No experience | 1.60 |

Линейная интерполяция между уровнями. Дефолт: 3 (Competent) если не заполнено.

### Backend

**Пакет:** `com.leadboard.competency`

- **MemberCompetencyEntity** — JPA entity (`member_competencies` table)
- **MemberCompetencyRepository** — Spring Data JPA
- **CompetencyService** — CRUD + матрица команды + bus-factor + загрузка компонентов из Jira
- **CompetencyScoreCalculator** — расчёт score и competencyFactor
- **CompetencyController** — REST API

**Миграция:** V32 — таблица `member_competencies`, колонка `components TEXT[]` в `jira_issues`

### API Endpoints

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/competencies/member/{memberId}` | Компетенции сотрудника |
| PUT | `/api/competencies/member/{memberId}` | Обновить компетенции `[{componentName, level}]` |
| GET | `/api/competencies/team/{teamId}` | Полная матрица команды |
| GET | `/api/competencies/team/{teamId}/bus-factor` | Bus-factor алерты |
| GET | `/api/competencies/components` | Доступные компоненты (из Jira / fallback из БД) |

### Bus Factor

| Experts (level >= 4) | Severity |
|---------------------|----------|
| 0 | CRITICAL — никто не знает область |
| 1 | WARNING — единственный эксперт |
| 2+ | OK — есть backup |

### Frontend

- **TeamCompetencyPage** — матрица команды (members × components), inline редактирование, bus-factor алерты
- **MemberProfilePage** — секция компетенций с grid и editable ratings
- **CompetencyRating** — компонент шкалы (5 цветных точек, кликабельные)
- **competencyApi.ts** — API клиент

### Тесты

- `CompetencyServiceTest` — 9 тестов (CRUD, bus-factor, Jira fallback)
- `CompetencyScoreCalculatorTest` — расчёт score и factor

## Файлы

### Backend
- `backend/src/main/java/com/leadboard/competency/` — весь пакет
- `backend/src/main/resources/db/migration/V32__create_competency_matrix.sql`
- `backend/src/test/java/com/leadboard/competency/CompetencyServiceTest.java`
- `backend/src/test/java/com/leadboard/competency/CompetencyScoreCalculatorTest.java`

### Frontend
- `frontend/src/pages/TeamCompetencyPage.tsx`
- `frontend/src/pages/MemberProfilePage.tsx` (секция компетенций)
- `frontend/src/components/competency/CompetencyRating.tsx`
- `frontend/src/api/competency.ts`
