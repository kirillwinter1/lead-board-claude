# QA Report: Перенос Bug SLA в Settings
**Дата:** 2026-02-23
**Тестировщик:** Claude QA Agent

## Summary
- Общий статус: **PASS**
- Backend tests: 100% passed (BUILD SUCCESSFUL)
- Frontend tests: 235 passed, 0 failed (18 test files)
- API tests: 2/2 PASS (`/api/bug-sla`, `/api/bug-sla/priorities`)
- Visual: 0 issues found

## Scope

UI-рефакторинг: таб "Bug SLA" убран из навигации, содержимое BugSlaSettingsPage встроено inline в Settings page.

### Изменённые файлы
| Файл | Изменение |
|------|-----------|
| `frontend/src/components/Layout.tsx` | Удалён NavLink на `/board/bug-sla` |
| `frontend/src/pages/SettingsPage.tsx` | Заменена ссылка "Open Bug SLA Settings" на inline `<BugSlaSettingsPage />` |
| `frontend/src/App.tsx` | Удалён Route `bug-sla` и импорт BugSlaSettingsPage |

## Тесты

### Backend
```
BUILD SUCCESSFUL — all tests passed
```

### Frontend
```
18 test files, 235 tests passed, 0 failed
```
Включая `Layout.test.tsx` (12 тестов) — навигационные тесты не упоминали Bug SLA, регрессии нет.

## Functional Testing

### Навигация
| Проверка | Результат |
|----------|-----------|
| Таб "Bug SLA" отсутствует в nav | PASS |
| Остальные табы на месте (Board, Timeline, Metrics, DQ, Bugs, Poker, Teams, Projects, Project Timeline, Settings) | PASS |
| Settings таб работает | PASS |

### Settings Page — Bug SLA секция inline
| Проверка | Результат |
|----------|-----------|
| Секция "Bug SLA Settings" отображается | PASS |
| Таблица приоритетов (Highest 24h, High 72h, Medium 168h, Low 336h, Lowest 672h) | PASS |
| Edit/Delete кнопки видны | PASS |
| "+ Add Priority" кнопка видна | PASS |
| Описание "Maximum resolution time by priority..." | PASS |

### API
| Endpoint | Результат |
|----------|-----------|
| `GET /api/bug-sla` | PASS (5 configs returned) |
| `GET /api/bug-sla/priorities` | PASS (2 missing priorities) |

### Старый маршрут
| Проверка | Результат |
|----------|-----------|
| `/board/bug-sla` → пустая страница (route removed) | PASS (expected SPA behavior) |

## Visual Review

### Settings Page (1920x1080)
- Layout корректен — Bug SLA секция между Workflow Configuration и Jira Sync
- Таблица приоритетов выровнена, цвета приоритетов отображаются
- Edit/Delete кнопки читаемы
- "+ Add Priority" кнопка на месте
- Отступы консистентны с другими секциями

### Навигация (Board page)
- 10 табов без "Bug SLA" — корректно
- Все табы видны, не обрезаны

## Bugs Found

**0 багов обнаружено.**

## Notes

- BugSlaSettingsPage использует собственный `padding: 24px, maxWidth: 700` — визуально это выглядит хорошо внутри settings-section, но ширина таблицы Bug SLA уже чем остальных секций. Это minor cosmetic difference, не баг.
- Старый маршрут `/board/bug-sla` показывает пустую страницу (серый фон) — это стандартное поведение React Router для несуществующих вложенных маршрутов. Можно добавить catch-all 404 route, но это scope отдельной задачи.

## Recommendations

1. **Low:** Можно добавить `<Route path="*" element={<NotFoundPage />} />` внутрь Layout для несуществующих маршрутов (общее улучшение, не специфично для этого рефакторинга)
