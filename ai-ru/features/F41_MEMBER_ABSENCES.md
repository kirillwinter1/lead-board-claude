# F41 — Member Absences / Time Off

**Статус:** ✅ Реализована (v0.41.0)

## Описание

CRUD отсутствий сотрудников (отпуска, больничные, отгулы) с интеграцией в автопланирование и визуальным таймлайном.

## Компоненты

### Backend

- **V42 миграция**: таблица `member_absences` (id, member_id, absence_type, start_date, end_date, comment, timestamps)
- **AbsenceType enum**: VACATION, SICK_LEAVE, DAY_OFF, OTHER
- **MemberAbsenceEntity**: JPA entity с ManyToOne на TeamMemberEntity
- **MemberAbsenceRepository**: запросы по member/team/date range, проверка overlap
- **AbsenceService**: CRUD, валидация дат и перекрытий, getTeamAbsenceDates() для планирования
- **TeamController**: 5 новых endpoints (GET/POST/PUT/DELETE) с RBAC

### Planning Integration

- **AssigneeSchedule.blockAbsenceDates()**: заполняет usedHours = effectiveHoursPerDay для дат отсутствия → getAvailableHours() возвращает 0
- **UnifiedPlanningService**: инжектит AbsenceService, блокирует даты отсутствий перед планированием

### Frontend

- **API types**: AbsenceType, Absence, CreateAbsenceRequest, UpdateAbsenceRequest
- **teamsApi**: getTeamAbsences, getUpcomingAbsences, createAbsence, updateAbsence, deleteAbsence
- **AbsenceModal**: модалка создания/редактирования (тип, даты, комментарий)
- **AbsenceTimeline**: горизонтальный таймлайн (участники × дни), навигация, цветные бары, tooltip
- **TeamMembersPage**: раскрывающаяся секция "Отпуска и отсутствия"
- **MemberProfilePage**: секция "Предстоящие отсутствия" (read-only)

## API Endpoints

| Метод | Путь | RBAC |
|-------|------|------|
| GET | `/api/teams/{teamId}/absences?from=&to=` | public |
| GET | `/api/teams/{teamId}/members/{memberId}/absences/upcoming` | public |
| POST | `/api/teams/{teamId}/members/{memberId}/absences` | canManageTeam |
| PUT | `/api/teams/{teamId}/members/{memberId}/absences/{id}` | canManageTeam |
| DELETE | `/api/teams/{teamId}/members/{memberId}/absences/{id}` | canManageTeam |

## Цвета типов отсутствий

| Тип | Цвет | Hex |
|-----|------|-----|
| Отпуск (VACATION) | Синий | #4C9AFF |
| Больничный (SICK_LEAVE) | Красный | #FF5630 |
| Отгул (DAY_OFF) | Оранжевый | #FF991F |
| Другое (OTHER) | Серый | #97A0AF |

## Тесты

- **AbsenceServiceTest**: CRUD, overlap validation, getTeamAbsenceDates, date clipping
- **AssigneeScheduleAbsenceTest**: blockAbsenceDates, allocateHours skips blocked, simulateAllocation
