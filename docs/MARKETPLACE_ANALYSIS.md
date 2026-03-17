# Три подхода к выходу на Atlassian Marketplace

> Дата анализа: 2026-03-13

## Контекст

Lead Board — IT delivery management платформа (67 фич), построенная поверх Jira Cloud. Уникальная комбинация: автоприоритизация (AutoScore 9 факторов + RICE), capacity-aware forecasting по ролям (SA→DEV→QA pipeline), delivery metrics (DSR, forecast accuracy, velocity). Ни один продукт на Marketplace не объединяет все три.

---

## Платформа: Atlassian Forge

С **сентября 2025** Atlassian принимает на Marketplace **только Forge-приложения**. Connect (старая платформа) закрывается в декабре 2026.

**Forge** — serverless-платформа Atlassian:
- Код живёт на серверах Atlassian (AWS Lambda)
- Язык — только Node.js
- UI рендерится внутри Jira через iframe (Custom UI) или нативные компоненты (UI Kit)
- Хранилище — Forge Storage (Key-Value), Forge SQL (managed TiDB/MySQL), или Remote Backend (обращение к внешнему серверу)

### Модули Forge (точки расширения UI)

| Модуль | Что это | Пример для Lead Board |
|--------|---------|----------------------|
| **Issue Panel** | Панель на карточке задачи | AutoScore breakdown, RICE badge, forecast date |
| **Project Page** | Отдельная вкладка в проекте | Board, Timeline, Metrics dashboard |
| **Dashboard Gadget** | Виджет на Jira Dashboard | Team velocity chart, DSR gauge, capacity summary |
| **Project Settings Page** | Настройки проекта | Workflow config, role mapping |
| **Global Page** | Полноэкранная страница приложения | Quarterly Planning, full metrics |
| **Issue Context** | Контекстная панель справа | Quick priority score, dependencies |

### Ограничения Forge

| Ограничение | Значение |
|-------------|----------|
| Execution timeout (sync) | 25 секунд |
| Execution timeout (async) | 15 минут (900 сек) |
| Memory per invocation | 512 MB (до 1 GiB) |
| Forge SQL storage | 1 GiB (production) |
| Forge SQL SELECT timeout | 5 секунд |
| Forge SQL INSERT/UPDATE/DELETE timeout | 10 секунд |
| Forge SQL DML rate | 150/sec |
| Forge SQL memory per query | 16 MiB |
| Forge SQL foreign keys | НЕ поддерживаются |
| Frontend request payload | 500 KB |
| Frontend response payload | 5 MB |
| Rate limit per installation | 5,000 invocations/min |
| Rate limit per app | 30,000 invocations/min |
| Egress requests per app | 50,000/min |

---

## Конкуренты на Marketplace

### Приоритизация / RICE

| Приложение | Что делает | Установки | Чего НЕ делает (а Lead Board делает) |
|------------|-----------|-----------|--------------------------------------|
| **Foxly** (Appfire) | RICE/WSJF/ICE шаблоны, Planning Poker | 1,617 | Нет AutoScore, нет forecasting, нет capacity planning |
| **Priority Scoring Calculator** | Формулы скоринга | 164 | Нет автоматического расчёта из subtask estimates |
| **Dynamic Scoring for Jira** | RICE/WSJF/ROI внутри issues | ? | Нет 9-факторного AutoScore, нет alignment boost |
| **Backlog Prioritization Pro** | Скоринг + ручная сортировка | ? | Нет прогнозов, нет командных метрик |

### Capacity / Forecasting

| Приложение | Что делает | Установки | Чего НЕ делает |
|------------|-----------|-----------|----------------|
| **Tempo Capacity Planner** | Resource management, plan vs actual | 10,860 | Нет авто pipeline SA→DEV→QA, нет forecast dates |
| **Flow Analytics Pro** | Monte Carlo forecasting, flow metrics | 24 | Нет RICE, нет автоприоритизации |
| **Velocity/Capacity Sprint Planning** | Sprint velocity + absences | ? | Нет quarterly planning, нет cross-team demand |
| **AgileLens** | Monte Carlo, throughput viz | 5 | Нет role-based capacity, нет per-phase scheduling |
| **BigPicture** (Appfire/Tempo) | Portfolio, Gantt, ресурсы, SAFe | 12,365 | Нет auto-prioritization, нет DSR, нет forecast accuracy |

### Tempo — главный "сосед"

Tempo (20,000+ клиентов, 7 продуктов) отвечает на вопрос **"Кто чем занят и сколько стоит?"**
Lead Board отвечает на вопрос **"Что делать дальше и когда будет готово?"**

| Возможность | Tempo | Lead Board |
|-------------|-------|------------|
| Time tracking | Отлично | Использует Jira worklogs |
| Resource allocation | Хорошо | Авто-назначение по ролям |
| Автоприоритизация | Нет | 9-факторный AutoScore |
| "Когда будет готово?" | Нет | UnifiedPlanning: дата на каждый эпик |
| Pipeline SA→DEV→QA | Нет | Да, с capacity каждой роли |
| DSR (delivery speed) | Нет | Автоматический |
| Forecast Accuracy | Нет | План vs факт по закрытым эпикам |
| Data Quality (17 checks) | Нет | Да |
| RICE auto-effort | Нет | Авто из subtask estimates |

---

## Подход A: Pure Forge (полный переезд на платформу Atlassian)

### Суть

Полностью переписать Lead Board как Forge-приложение. Весь код живёт на серверах Atlassian. Java → Node.js. PostgreSQL → Forge SQL (TiDB/MySQL). React → Custom UI в iframe.

### Архитектура

```
┌─────────────────── Atlassian Cloud ───────────────────┐
│                                                        │
│  Jira UI                                               │
│  ├── Issue Panel  → AutoScore badge, RICE, forecast    │
│  ├── Project Page → Board, Timeline, Metrics           │
│  ├── Dashboard    → Velocity chart, DSR gauge          │
│  └── Global Page  → Quarterly Planning, full analytics │
│                                                        │
│  Forge Backend (Node.js Lambda)                        │
│  ├── AutoScore calculator                              │
│  ├── RICE assessment engine                            │
│  ├── Unified Planning algorithm                        │
│  ├── Metrics services (DSR, velocity, throughput)      │
│  └── Data Quality rules                                │
│                                                        │
│  Forge SQL (TiDB, MySQL-compatible)                    │
│  ├── issues (synced from Jira API)                     │
│  ├── rice_assessments                                  │
│  ├── forecast_snapshots                                │
│  ├── team_members, absences                            │
│  └── workflow_config                                   │
│                                                        │
└────────────────────────────────────────────────────────┘
```

### Что нужно переписать

| Компонент | Текущее | Forge-эквивалент | Сложность |
|-----------|---------|------------------|-----------|
| 50+ Java-сервисов | Spring Boot, Java 21 | Node.js serverless functions | Огромная — полный rewrite |
| PostgreSQL | 50+ индексов, pgvector, JSONB | Forge SQL (TiDB/MySQL, 1 GiB max) | Высокая — потеря pgvector, JSONB |
| Flyway миграции | V/T-prefixed SQL | Forge SQL DDL в коде | Средняя |
| Multi-tenancy | Schema-per-tenant | Автоматическая (per-install isolation) | Упрощение |
| OAuth | Собственный OAuthService | Forge auth (из коробки) | Упрощение |
| React frontend | 14 pages, 80+ components | Custom UI (iframe) | Средняя — адаптация |

### Forge SQL vs Lead Board потребности

| Параметр | Forge SQL | Lead Board | Проблема? |
|----------|-----------|------------|-----------|
| Storage | 1 GiB | ~500 MB средний клиент | Возможно при >5000 issues |
| Max tables | 200 | ~25 entity таблиц | Ок |
| SELECT timeout | 5 секунд | UnifiedPlanning — тяжёлые запросы | **Проблема** |
| DML rate | 150/sec | Batch sync 500+ issues | Близко к лимиту |
| Memory per query | 16 MiB | Aggregate метрики за год | **Возможная проблема** |
| Foreign keys | Не поддерживаются | Используем FK везде | Переработка schema |
| pgvector | Нет | Semantic Search (F54) | **Потеря фичи** |

### Что теряешь

1. Semantic Search (pgvector)
2. Гибкость инфраструктуры
3. Multi-project сложные запросы (5s timeout)
4. Prometheus/Grafana мониторинг

### Что получаешь

1. Zero infrastructure — не нужен сервер
2. Встроенная безопасность — auth, tenant isolation из коробки
3. Нативный Jira experience
4. Автоматическое масштабирование
5. $1M Forge incentive (100% выручки до миллиона)
6. Simplified multi-tenancy
7. Marketplace discovery

### Оценка трудоёмкости

| Этап | Время |
|------|-------|
| Проектирование Node.js архитектуры | 2-3 недели |
| Переписать scoring (AutoScore + RICE) | 3-4 недели |
| Переписать planning (Unified + Forecast) | 4-6 недель |
| Переписать metrics (DSR, velocity, etc.) | 4-6 недель |
| Forge SQL schema + миграции | 2 недели |
| Custom UI (адаптация React) | 4-6 недель |
| Тестирование | 3-4 недели |
| Security review + Marketplace submission | 2-3 недели |
| **ИТОГО** | **6-9 месяцев** (1 разработчик) |

### Вердикт

**Риск: ВЫСОКИЙ.** Полный rewrite с неизвестными. Лимиты Forge SQL могут быть критичны для аналитических запросов. Потеря pgvector. Но: максимально нативный Jira experience и $1M incentive.

---

## Подход B: Forge + Remote Backend (гибрид) — РЕКОМЕНДУЕМЫЙ

### Суть

Лёгкая Forge-обёртка (Node.js, ~2-4K LOC) для UI внутри Jira. Вся бизнес-логика остаётся на Java-backend. Forge вызывает сервер через **Forge Remote**.

### Архитектура

```
┌──────── Atlassian Cloud ────────┐     ┌──────── Свой сервер ──────────┐
│                                  │     │                               │
│  Jira UI                         │     │  Spring Boot (Java 21)        │
│  ├── Issue Panel (Custom UI)     │     │  ├── AutoScoreService          │
│  ├── Project Page (Custom UI)    │     │  ├── RiceAssessmentService     │
│  ├── Dashboard Gadget            │     │  ├── UnifiedPlanningService    │
│  └── Global Page (Custom UI)     │     │  ├── TeamMetricsService        │
│                                  │     │  ├── DsrService                │
│  Forge Backend (thin proxy)      │     │  ├── VelocityService           │
│  ├── JWT validation              │────▶│  ├── DataQualityService        │
│  ├── invokeRemote bridge        │     │  ├── AI Chat (OpenRouter)      │
│  └── Context forwarding          │     │  └── Semantic Search (pgvector)│
│                                  │     │                               │
│  @forge/bridge                   │     │  PostgreSQL                    │
│  ├── invokeRemote()             │     │  ├── All 25+ tables            │
│  └── requestRemote()            │     │  ├── pgvector embeddings       │
│                                  │     │  └── Full multi-tenancy       │
└──────────────────────────────────┘     └───────────────────────────────┘
```

### Как работает Forge Remote

**Manifest (manifest.yml):**
```yaml
modules:
  jira:issuePanel:
    - key: autoscore-panel
      title: Lead Board — Priority
      resource: autoscore-ui
      resolver:
        endpoint: autoscore-endpoint

  jira:projectPage:
    - key: board-page
      title: Lead Board
      resource: board-ui
      resolver:
        endpoint: board-endpoint

  jira:dashboardGadget:
    - key: velocity-gadget
      title: Team Velocity
      resource: velocity-ui
      resolver:
        endpoint: metrics-endpoint

  jira:globalPage:
    - key: planning-page
      title: Quarterly Planning
      resource: planning-ui
      resolver:
        endpoint: planning-endpoint

endpoint:
  - key: autoscore-endpoint
    remote: leadboard-backend
    auth:
      appSystemToken:
        enabled: true
      appUserToken:
        enabled: true

remotes:
  - key: leadboard-backend
    baseUrl: https://api.leadboard.app
```

**Frontend (Custom UI в iframe):**
```tsx
import { invokeRemote } from "@forge/bridge";
import { view } from "@forge/bridge";

const context = await view.getContext();
const { cloudId, extension } = context;

const response = await invokeRemote({
  path: `/api/board?cloudId=${cloudId}`,
  method: "GET",
});
```

**Авторизация (JWT — Forge Invocation Token):**

Forge отправляет JWT в заголовке Authorization. Backend валидирует:

```java
var jwtConsumer = new JwtConsumerBuilder()
    .setVerificationKeyResolver(httpsJwksKeyResolver) // Atlassian JWKS
    .setExpectedAudience(appId)
    .setExpectedIssuer("forge/invocation-token")
    .build();

JwtClaims claims = jwtConsumer.process(token);
String cloudId = claims.getClaimValue("context.cloudId");
String accountId = claims.getClaimValue("principal");
```

**JWT (FIT) содержит:**

| Поле | Что это | Использование |
|------|---------|---------------|
| `context.cloudId` | ID Jira-инстанса | tenant resolution |
| `principal` | Atlassian account ID | авторизация |
| `app.installationId` | ID установки | per-install storage |
| `app.license.isActive` | Лицензия активна? | paywall |
| `app.license.type` | COMMERCIAL/COMMUNITY/ACADEMIC | tier features |
| `app.license.subscriptionEndDate` | Когда истекает | grace period |

### Что нужно изменить

| Изменение | Объём | Сложность |
|-----------|-------|-----------|
| Forge-обёртка (Node.js) | ~2000 LOC: manifest, endpoints, bridge | Средняя |
| Auth adapter (Java) | Новый ForgeAuthFilter — валидация FIT JWT | Низкая |
| Tenant mapping | cloudId → tenant schema | Низкая |
| License check | Проверка app.license.isActive | Низкая |
| API Gateway | Публичный HTTPS endpoint | Низкая |
| Custom UI адаптация | React → iframe: убрать Layout/nav | Средняя |
| CORS | Разрешить *.atlassian.net | Низкая |

### Что НЕ нужно менять

- Все 50+ Java-сервисов — работают as-is
- PostgreSQL — остаётся, pgvector тоже
- Multi-tenancy — расширяется (cloudId → tenant)
- AI Chat, Semantic Search — работают
- Jira sync — работает
- Все 67 фич — сохраняются

### Лимиты Forge vs Lead Board

| Лимит | Значение | Lead Board | OK? |
|-------|----------|------------|-----|
| Remote timeout (UI) | 25 сек | Board API: 0.5-2 сек | Ок |
| Frontend request | 500 KB | Filters, small POST | Ок |
| Frontend response | 5 MB | Board ~200KB, Timeline ~1MB | Ок |
| Invocations | 5000/min/install | ~50/min active user | Ок |
| Latency overhead | +100-300ms | Приемлемо | Ок |

### Phased Rollout

**Phase 1 — MVP (4-6 недель):**

| Модуль | Что показывает |
|--------|---------------|
| Issue Panel: AutoScore | Score breakdown + RICE badge на каждой задаче |
| Dashboard Gadget: Team Health | DSR gauge + velocity + capacity |

**Phase 2 (6-10 недель):**

| Модуль | Что показывает |
|--------|---------------|
| Project Page: Board | Доска с AutoScore сортировкой |
| Project Page: Timeline | Gantt с forecast dates |
| Dashboard Gadget: Forecast Accuracy | Planned vs Actual scatter plot |

**Phase 3 (10-16 недель):**

| Модуль | Что показывает |
|--------|---------------|
| Global Page: Quarterly Planning | Capacity vs Demand, RICE ranking |
| Global Page: Full Metrics | DSR, throughput, lead/cycle time |
| Project Settings: Workflow Config | Pipeline config, role mapping |

### Security Review

| Требование | Статус | Доработка |
|------------|--------|-----------|
| HTTPS everywhere | Есть | Ок |
| Data encryption at rest | PostgreSQL + disk | Подтвердить FDE |
| JWT validation | Нет (новое) | ForgeAuthFilter |
| No PII in logs | Частично | Audit rules |
| Tenant isolation | Есть | Ок |
| GDPR DPA | Нужен | Юридический документ |
| End User Terms | Нужен | Юридический документ |
| Partner Security Questionnaire | Нужен | Заполнить |
| KYC/KYB verification | Нужно | Бизнес-документы |

### Оценка трудоёмкости

| Этап | Время |
|------|-------|
| Forge-обёртка + manifest | 1-2 недели |
| Auth adapter (ForgeAuthFilter) | 3-5 дней |
| Custom UI для Issue Panel + Dashboard (MVP) | 2-3 недели |
| Тестирование + debug | 1-2 недели |
| Security compliance + документация | 1-2 недели |
| Marketplace submission + review | 2-4 недели |
| **Phase 1 ИТОГО** | **2-3 месяца** |
| Phase 2 (Board, Timeline, Forecast) | +1-2 месяца |
| Phase 3 (Planning, Full Metrics) | +1-2 месяца |

### Вердикт

**Риск: НИЗКИЙ-СРЕДНИЙ.** Сохраняет все 67 фич и весь Java-код. Forge Remote — официально поддерживаемая архитектура. MVP за 2-3 месяца. $1M incentive сохраняется. Главные риски: latency overhead, надёжный хостинг backend, security review.

---

## Подход C: Standalone SaaS + Forge Connector

### Суть

Lead Board остаётся автономным SaaS. На Marketplace публикуется "лёгкий" Forge-коннектор с ссылками и виджетами.

### Архитектура

```
┌──── Atlassian Cloud ─────┐     ┌──────── SaaS-портал ────────────┐
│                           │     │                                   │
│  Jira UI                  │     │  https://app.leadboard.io         │
│  ├── Issue Panel:         │     │                                   │
│  │   "Score: 78 ★"       │     │  Full Lead Board (as-is)          │
│  │   [Open in Lead Board]│────▶│  ├── Board Page                   │
│  │                        │     │  ├── Timeline Page                │
│  ├── Dashboard Gadget:    │     │  ├── Metrics Dashboard            │
│  │   Summary metrics     │     │  ├── Quarterly Planning           │
│  │   [View Details →]    │────▶│  ├── RICE Assessment              │
│  │                        │     │  ├── AI Chat                      │
│  └── Project Sidebar:     │     │  ├── Data Quality                 │
│      [Lead Board Portal] │────▶│  └── All 67 features              │
│                           │     │                                   │
│  Forge Backend (minimal)  │     │  Spring Boot + PostgreSQL         │
│  ├── Fetch summary data   │     │  (полный текущий стек)            │
│  └── Generate deep links  │     │                                   │
└───────────────────────────┘     └───────────────────────────────────┘
```

### Ограничение Marketplace

Для Cloud-приложений разрешены только модели **Free** или **Paid via Atlassian**. "Paid via Vendor" запрещён.

Правило: *"If your app listing is free, ensure your app provides some useful function in the Atlassian app 'as is'."*

### Сценарии монетизации

**C1: Freemium Connector + Paid SaaS** — коннектор бесплатный, подписка на портале. Нет $1M incentive.

**C2: Paid Forge App** — платный через Atlassian, но нужно достаточно ценности внутри Jira. Risk отказа Atlassian.

**C3: Dual Listing** — бесплатный connector для discovery, отдельный лендинг для продажи.

### Что нужно изменить

| Изменение | Сложность |
|-----------|-----------|
| Forge-коннектор (~500-1000 LOC) | Низкая |
| 2-3 API endpoints для Forge | Низкая |
| Deep linking + SSO | Средняя |

### Оценка трудоёмкости

| Этап | Время |
|------|-------|
| Forge connector | 1-2 недели |
| API endpoints | 3-5 дней |
| Deep linking + SSO | 1 неделя |
| Marketplace submission | 2-3 недели |
| **ИТОГО** | **1-1.5 месяца** |

### Вердикт

**Риск: НИЗКИЙ технически, СРЕДНИЙ коммерчески.** Минимум изменений. Но: нет $1M incentive, пользователь уходит из Jira (friction), ограниченный Marketplace discovery.

---

## Сравнительная таблица

| Критерий | A: Pure Forge | B: Forge + Remote | C: SaaS + Connector |
|----------|:---:|:---:|:---:|
| Время до MVP | 6-9 мес | 2-3 мес | 1-1.5 мес |
| Сохранение Java-кода | Нет (rewrite) | Да (100%) | Да (100%) |
| Сохранение всех 67 фич | ~60 (потеря pgvector) | 67 | 67 |
| Нативность в Jira | Максимальная | Высокая | Минимальная |
| $1M Forge incentive | Да | Да | Нет (free listing) |
| Нужен свой сервер | Нет | Да | Да |
| Latency | Минимальная | +100-300ms | +redirect |
| Security review | Средняя | Средняя-высокая | Низкая |
| Масштабируемость | Atlassian | Ты | Ты |
| Vendor lock-in | Высокий | Низкий | Минимальный |
| Монетизация | Paid via Atlassian | Paid via Atlassian | Отдельная подписка |
| Enterprise trust | Высокий | Высокий | Средний |
| Risk | Высокий | Низкий-средний | Низкий техн. / средний бизнес |

---

## Финансовая модель Marketplace

| Параметр | Значение |
|----------|----------|
| Revenue share (Forge) | 16% (апрель 2026), 17% (июль 2026) |
| Forge incentive | **100% выручки до $1M lifetime** |
| Минимальная выплата | $500 USD |
| Модель | Per-user/month |
| Типичная цена конкурентов | $1-5 per user/month |

### Прогноз при $2/user/month

```
Средний клиент: 100 users × $2 = $200/month = $2,400/year

Консервативный (1 год):  50 клиентов  × $2,400 = $120,000/year
Умеренный (2 года):      200 клиентов × $2,400 = $480,000/year
Оптимистичный (3 года):  500 клиентов × $2,400 = $1,200,000/year
```

---

## Рекомендация

**Подход B (Forge + Remote Backend)** — оптимальный баланс:

1. Быстрый выход — MVP за 2-3 месяца
2. Нулевой risk потери функционала — все 67 фич
3. $1M incentive — как полноценное Forge-приложение
4. Инкрементальный путь — Issue Panel → Dashboard → Board → Full Portal
5. Путь к Pure Forge — при успехе можно переносить логику на Forge
6. Уникальное предложение — AutoScore + Capacity Forecasting + Delivery Metrics

---

## Источники

- [Forge Platform](https://developer.atlassian.com/platform/forge/)
- [Forge Remote](https://developer.atlassian.com/platform/forge/remote/)
- [Forge SQL Limits](https://developer.atlassian.com/platform/forge/limits-sql/)
- [Forge Invocation Limits](https://developer.atlassian.com/platform/forge/limits-invocation/)
- [Forge Custom UI](https://developer.atlassian.com/platform/forge/custom-ui/)
- [Marketplace Revenue Share 2026](https://www.atlassian.com/blog/developer/updates-to-marketplace-revenue-share-2026)
- [Marketplace Security Requirements](https://go.atlassian.com/security-requirements-for-cloud-apps)
- [Marketplace Pricing](https://developer.atlassian.com/platform/marketplace/pricing-payment-and-billing/)
- [Tempo Capacity Planner](https://www.tempo.io/products/capacity-planner)
- [BigPicture](https://marketplace.atlassian.com/apps/1212259/bigpicture-portfolio-resource-management-for-jira)
- [Foxly](https://marketplace.atlassian.com/apps/1222824/foxly-requirements-backlog-prioritization-planning-poker)
