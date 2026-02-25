# F46 — Security Hardening

**Версия:** 0.46.0
**Дата:** 2026-02-25
**Launch Plan:** L4

## Обзор

Комплексное усиление безопасности приложения перед продакшн-запуском как SaaS.

## Реализованные меры

### 1. Rate Limiting

**Файл:** `RateLimitFilter.java`

Token bucket алгоритм с фиксированным окном (1 минута). Разные лимиты для разных групп endpoints:

| Группа | Лимит | Endpoints |
|--------|-------|-----------|
| OAuth | 20 req/min per IP | `/oauth/**` |
| Sync | 5 req/min per IP | `/api/sync/trigger` |
| Registration | 10 req/min per IP | `/api/public/tenants/register` |
| General API | 200 req/min per IP | `/api/**` |

- При превышении возвращается `429 Too Many Requests` с `Retry-After: 60`
- Автоматическая очистка устаревших bucket'ов каждые 5 минут
- IP определяется по `X-Forwarded-For` → `X-Real-IP` → `remoteAddr`
- Health check (`/api/health`) не лимитируется

### 2. CORS Hardening

**Файл:** `WebConfig.java`

- **Убрана поддержка wildcard-паттернов** (`https://*.domain`) — только явные origins
- **Явный whitelist headers** вместо `allowedHeaders("*")`: Content-Type, Authorization, X-Requested-With, X-Tenant-Slug, Accept, Origin
- **Добавлен `maxAge(3600)`** — браузер кэширует preflight на 1 час
- **Убран CORS для WebSocket** — WebSocket использует свой механизм origin-проверки

### 3. WebSocket Authentication

**Файл:** `PokerWebSocketHandler.java`, `PokerWebSocketConfig.java`

- При подключении проверяется cookie `LEAD_SESSION` в handshake headers
- Валидируется сессия через `SessionRepository.findValidSession()`
- Проверяется существование комнаты (`roomCode`)
- Невалидные подключения закрываются с `CloseStatus.POLICY_VIOLATION`
- Убран `setAllowedOrigins("*")` — используется тот же whitelist что и CORS

### 4. Tenant Isolation (Header Bypass Fix)

**Файл:** `TenantFilter.java`

- **Subdomain проверяется ПЕРВЫМ** (не может быть подделан клиентом)
- `X-Tenant-Slug` header принимается **ТОЛЬКО для localhost** (dev-режим)
- На production: header полностью игнорируется, tenant определяется только по subdomain

**Было (уязвимо):**
```
1. Header → 2. Subdomain  // Атакующий мог подменить tenant через header
```

**Стало (безопасно):**
```
1. Subdomain → 2. Header (только localhost)
```

### 5. Security Headers

**Файлы:** `SecurityHeadersFilter.java` (backend), `nginx.conf` (frontend)

#### Backend Filter (для всех API responses):
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: geolocation=(), microphone=(), camera=(), payment=()`
- `Cache-Control: no-store` для API/OAuth endpoints

#### Nginx (для frontend):
- `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload` (HSTS)
- `Content-Security-Policy` — whitelist `self`, Atlassian API, WebSocket
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy` — отключены ненужные API браузера
- `X-Frame-Options: DENY` (было SAMEORIGIN)

### 6. Error Message Sanitization

**Файл:** `GlobalExceptionHandler.java`

- Добавлен catch-all `@ExceptionHandler(Exception.class)` — возвращает generic "An internal error occurred"
- Детали ошибки логируются server-side (`log.error`), НЕ отправляются клиенту
- `AccessDeniedException` → `403 "Access denied"` (без деталей)
- `MethodArgumentNotValidException` → `400` с перечнем полей
- `MissingServletRequestParameterException` → `400` с именем параметра
- OAuth callback: убрана передача `error message` в URL redirect

### 7. Что проверено и безопасно (по результатам аудита)

| Область | Статус |
|---------|--------|
| SQL Injection | ✅ Все запросы параметризованы (JPA @Param) |
| XSS | ✅ React auto-escape, нет dangerouslySetInnerHTML |
| CSRF | ✅ Отключён (stateless API + HttpOnly/Secure/SameSite cookie) |
| Session cookie | ✅ HttpOnly, Secure, SameSite=Lax |
| Secrets in git | ✅ .env в .gitignore |
| Tokens in logs | ✅ Не логируются |

## Новые файлы

| Файл | Описание |
|------|----------|
| `config/RateLimitFilter.java` | Rate limiting filter с token bucket |
| `config/SecurityHeadersFilter.java` | Security response headers |
| `test/security/RateLimitFilterTest.java` | 5 тестов token bucket |
| `test/security/TenantFilterSecurityTest.java` | 4 теста tenant isolation |
| `test/security/GlobalExceptionHandlerTest.java` | 4 теста error sanitization |

## Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `config/WebConfig.java` | Убраны wildcards, явные headers, maxAge |
| `config/GlobalExceptionHandler.java` | Catch-all + validation handlers |
| `poker/websocket/PokerWebSocketHandler.java` | Session auth + room validation |
| `poker/websocket/PokerWebSocketConfig.java` | Origin whitelist вместо `*` |
| `tenant/TenantFilter.java` | Subdomain priority, header only for localhost |
| `auth/OAuthController.java` | Убрана утечка error message в URL |
| `frontend/nginx.conf` | HSTS, CSP, Referrer-Policy, Permissions-Policy |

## Тесты

13 новых тестов:
- `RateLimitFilterTest` (5): token bucket consume/reject/expiry
- `TenantFilterSecurityTest` (4): subdomain priority, header ignored on production, allowed on localhost, inactive tenant 403
- `GlobalExceptionHandlerTest` (4): 400/404/403/500 responses, no detail leakage
