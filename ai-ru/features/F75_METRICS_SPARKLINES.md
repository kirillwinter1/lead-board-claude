# F75. Metrics Sparklines (KPI Trend Mini-Charts)

**Версия:** 0.75.0 | **Дата:** 2026-06-27

Мини-графики трендов на карточках Executive Summary (Team Metrics) — динамика каждого
KPI за 10 недель с одного взгляда. Перенесено из заброшенной ветки delivery-guide на
текущий main (только sparklines, без сопутствующего boardCategory-рефактора и p85-UI).

## Backend
- `SparklineResponse` DTO: 5 недельных рядов (throughput, cycleTimeMedian,
  leadTimeMedian, predictability, utilization), каждый — `[{period, value}]`.
- `MetricsQueryRepository`: 3 новых weekly-запроса (throughput по STORY, медианы
  cycle/lead time). Существующие запросы не тронуты.
- `TeamMetricsService.getSparklines(...)`: 10-недельный lookback; predictability из
  monthly on-time rate, utilization из недельного velocity.
- `GET /api/metrics/sparklines?teamId&from&to`.
- `RateLimitFilter`: `/api/metrics/*` → 600 req/min (sparklines дают много запросов).

## Frontend
- `getSparklines()` в `api/metrics.ts`.
- `ExecutiveSummaryRow`: на каждую KPI-карточку добавлен мини-чарт (бары для
  throughput/utilization, линия для cycle/lead/predictability). Аддитивно к F59-вёрстке,
  маппинг серий по позиции карточки (устойчиво к смене лейблов). Без percentile-селектора.
  Деградирует чисто при пустых данных (n=0 → без спарклайна).

## Проверка
tsc чисто, фронт-сьют 236/236 (мок TeamMetricsPage обновлён `getSparklines`), backend
TeamMetricsServiceTest зелёный, эндпоинт отдаёт данные, спарклайны рендерятся (визуально
подтверждено на team 1).
