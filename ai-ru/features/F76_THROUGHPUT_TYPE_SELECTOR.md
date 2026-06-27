# F76. Throughput by Period — селектор типа + сплит эпики/стори

**Версия:** 0.76.0 | **Дата:** 2026-06-27

На графике «Throughput by Period» (Team Metrics → Diagnostics) добавлен селектор
типа в шапке, управляющий отображаемыми сериями. Frontend-only — бэкенд уже
фильтровал throughput по `issueType`.

## Поведение (один селектор)
| Выбор | График |
|-------|--------|
| **Epics & Stories** (по умолч.) | две линии — throughput эпиков и сторей отдельно |
| конкретный тип | одна линия — throughput этого типа |
| **All (total)** | одна линия общего throughput + скользящее среднее |

## Реализация
- Страница владеет стейтом `throughputMode` и фетчем; `ThroughputChart` —
  презентационный (props: `series`, `movingAverage`, `mode`, `modeOptions`,
  `onModeChange`), рендерит `SingleSelectDropdown` в шапке.
- Данные через существующий `getThroughput(team, from, to, issueType)`:
  два вызова (эпик+стори) / один (тип) / без запроса (all → данные сводки).
- Имена эпик/стори типов берутся из `issueTypeCategories` по категории (не хардкод).
- Скользящее среднее — только в режиме «All». Цвета серий — `constants/colors`.
- Список типов в селекторе исключает PROJECT и SUBTASK (только work-unit типы).

## Тесты
`TeamMetricsPage.test.tsx`: мок `getThroughput`; проверка что дефолт фетчит эпик+стори,
один тип — рефетч с типом, «All» — без запроса (сводка). Сьют зелёный (239).
