# F27: RBAC (Role-Based Access Control)

## Обзор

Система ролей и разграничения доступа в Lead Board.

## Роли

| Роль | Описание |
|------|----------|
| **ADMIN** | Полный доступ, управление пользователями и настройками |
| **PROJECT_MANAGER** | Управление проектами и RICE, просмотр board/timeline, покер |
| **TEAM_LEAD** | Управление своей командой, изменение приоритетов |
| **MEMBER** | Просмотр, участие в Poker, свои метрики |
| **VIEWER** | Только чтение |

## Матрица доступа

| Функция | Admin | PM | Team Lead | Member | Viewer |
|---------|-------|----|-----------|--------|--------|
| Просмотр Board/Timeline | ✅ | ✅ | ✅ | ✅ | ✅ |
| Управление проектами/RICE | ✅ | ✅ | ✅ | ❌ | ❌ |
| Planning Poker | ✅ | ✅ | ✅ | ✅ | ❌ |
| Управление командами | ✅ | ❌ | ✅ (своя) | ❌ | ❌ |
| Изменение приоритетов | ✅ | ❌ | ✅ | ❌ | ❌ |
| Sync trigger | ✅ | ❌ | ❌ | ❌ | ❌ |
| Настройки/Admin | ✅ | ❌ | ❌ | ❌ | ❌ |

## Реализация

### База данных

Миграция `V23__add_user_app_role.sql`:
- Добавляет колонку `app_role` в таблицу `users`
- CHECK constraint: `ADMIN`, `PROJECT_MANAGER`, `TEAM_LEAD`, `MEMBER`, `VIEWER`
- Первый пользователь автоматически становится ADMIN
- Новые пользователи получают роль MEMBER по умолчанию

### Backend

**Новые файлы:**
- `AppRole.java` — enum ролей с permissions
- `LeadBoardAuthentication.java` — кастомный Authentication principal
- `LeadBoardAuthenticationFilter.java` — извлечение пользователя из OAuth токена
- `AuthorizationService.java` — helper для проверок доступа
- `SecurityConfig.java` — конфигурация Spring Security
- `AdminController.java` — API управления пользователями

**Изменённые файлы:**
- `build.gradle.kts` — добавлен spring-boot-starter-security
- `UserEntity.java` — добавлено поле appRole
- `OAuthService.java` — расширен AuthStatus (role, permissions)
- Контроллеры — добавлены @PreAuthorize аннотации

### API

**Расширенный /oauth/atlassian/status:**
```json
{
  "authenticated": true,
  "user": {
    "id": 1,
    "accountId": "abc123",
    "displayName": "John Doe",
    "email": "john@example.com",
    "avatarUrl": "...",
    "role": "ADMIN",
    "permissions": ["teams:manage", "priorities:edit", "board:view", ...]
  }
}
```

**Admin API:**
- `GET /api/admin/users` — список пользователей с ролями
- `PATCH /api/admin/users/{id}/role` — изменение роли пользователя

### Frontend

**Новые страницы:**
- `SettingsPage.tsx` — управление пользователями (только для ADMIN)
- `SettingsPage.css` — стили

**Изменения:**
- `Layout.tsx` — добавлена навигация Settings для админов
- `App.tsx` — добавлен route `/board/settings`

## Связь Team Lead с командой

Team Lead управляет командами, в которых состоит как участник:
```
users.atlassianAccountId = team_members.jiraAccountId
```

## Безопасность

- CSRF отключен (используется token-based auth)
- Все endpoints `/api/admin/**` требуют роль ADMIN
- `POST /api/sync/trigger` требует роль ADMIN
- Контроллеры используют SpEL для проверки доступа: `@PreAuthorize("@authorizationService.canManageTeam(#teamId)")`

## UI

### Settings Page (Admin)

```
┌─────────────────────────────────────────────────────────────┐
│ Settings                                                     │
├─────────────────────────────────────────────────────────────┤
│ User Management                                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ User           │ Email           │ Role      │         │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ John Doe       │ john@...        │ [ADMIN ▼] │         │ │
│ │ Jane Smith     │ jane@...        │ [TEAM_LEAD▼]│       │ │
│ │ Bob Wilson     │ bob@...         │ [MEMBER ▼]│         │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ Role Permissions                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Permission       │ Admin │ Team Lead │ Member │ Viewer │ │
│ │ View Board       │  ✅   │    ✅     │   ✅   │   ✅   │ │
│ │ Planning Poker   │  ✅   │    ✅     │   ✅   │   ❌   │ │
│ │ Manage Teams     │  ✅   │  Own team │   ❌   │   ❌   │ │
│ │ Edit Priorities  │  ✅   │    ✅     │   ❌   │   ❌   │ │
│ │ Sync Trigger     │  ✅   │    ❌     │   ❌   │   ❌   │ │
│ │ Settings/Admin   │  ✅   │    ❌     │   ❌   │   ❌   │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Тестирование

1. Залогиниться через OAuth
2. Проверить `/oauth/atlassian/status` — должен вернуть role и permissions
3. Попробовать вызвать `/api/sync/trigger` не-админом — должен быть 403
4. Открыть Settings (Admin) — должна быть таблица пользователей
5. Изменить роль пользователя — проверить что сохранилось
