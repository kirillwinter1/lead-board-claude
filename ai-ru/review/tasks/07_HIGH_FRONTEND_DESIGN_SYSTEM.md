# TASK: Fix Design System violations in frontend

**Priority:** High
**Review IDs:** H10, H11, H12, M33, M34
**Files:**
- `DataQualityPage.tsx:79-112` — SeverityBadge + SummaryCard дубликаты
- `ProjectsPage.tsx:30-53` — hardcoded progress colors #36B37E / #0065FF
- `ProjectTimelinePage.tsx:529,851` — hardcoded progress colors (duplicate)
- `ProjectTimelinePage.tsx:60`, `TimelinePage.tsx:287`, `EpicRoleChip.tsx:6` — duplicate lightenColor()

## Проблема

1. `SeverityBadge` дублирует `StatusBadge` с hardcoded цветами
2. `SummaryCard` дублирует `MetricCard`
3. Progress bar цвета hardcoded в 3 местах
4. `lightenColor()` скопирована в 3 файла

## Рекомендация

1. Заменить `SeverityBadge` на расширенный `StatusBadge` (добавить `colorOverride` prop если нужно)
2. Заменить `SummaryCard` на `MetricCard`
3. Вынести progress colors в CSS переменные: `--color-progress-done`, `--color-progress-active`
4. Извлечь `lightenColor()` в `src/utils/colorUtils.ts`
