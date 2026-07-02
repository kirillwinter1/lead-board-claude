# Security Audit — Lead Board

**Дата:** 2026-07-01
**Область:** backend (Spring Boot, Java 21, multi-tenant) + frontend (React/TS)
**Метод:** параллельный аудит по 6 доменам (authn/authz, multi-tenancy, SQL/JQL injection, crypto/secrets/CORS, MCP/LLM/SSRF, frontend XSS) + отдельная верификация ключевых находок на ложные срабатывания.

Все находки ниже подтверждены чтением реального кода (confidence ≥ 7/10). Frontend по проверенным категориям (XSS/eval/секреты/утечка токенов) — **чист**.

---

## Сводка (что чинить в порядке приоритета)

| # | Severity | Проблема | Файлы |
|---|----------|----------|-------|
| 1 | 🔴 CRITICAL | Глобальный резолвинг OAuth Jira-токена игнорирует тенант → cross-tenant утечка/подмена Jira-данных | `OAuthTokenRepository`, `OAuthService`, `JiraClient`, `TenantSyncScheduler` |
| 2 | 🟠 HIGH | Само-присоединение к любому тенанту через OAuth callback `?tenant=` | `OAuthController`, `OAuthService`, `TenantService` |
| 3 | 🟠 HIGH | Chat/MCP write-инструменты проверяют только `isAuthenticated()` → privilege escalation | `ChatToolExecutor` |
| 4 | 🟠 HIGH | JQL-инъекция через `existingStoryKey` в Planning Poker | `JiraClient`, `PokerSessionService`, `AddStoryRequest` |
| 5 | 🟡 MEDIUM | MCP JWT: обход проверки членства/отзыва при отсутствии `tenant_id` → удержание global ADMIN | `McpJwtContextFilter` |
| 6 | 🟡 MEDIUM | Chat/MCP read-инструменты: обход team-скоупинга при `teamId=null` | `ChatToolExecutor` |
| 7 | 🟡 MEDIUM | OAuth login CSRF: `state` не привязан к браузеру инициатора | `OAuthService`, `OAuthController` |
| 8 | 🟡 MEDIUM | CORS: bare `*` + credentials → отражение Origin | `WebConfig` |
| 9 | 🟡 MEDIUM | Session-cookie `Secure` по умолчанию `false` в prod-конфиге | `application.yml`, `application-prod.yml` |
| 10 | 🟢 LOW | MCP debug static-token бэкдор, если включён в prod с oauth off | `McpDebugAuthFilter` |
| 11 | 🟢 LOW | Неаутентифицированное чтение workflow-конфига тенанта | `PublicConfigController`, `SecurityConfig` |
| 12 | 🟢 LOW | Rate-limit обходится подменой `X-Forwarded-For` | `RateLimitFilter` |
| 13 | 🟢 LOW | WebSocket Poker без проверки членства в тенанте (harden) | `PokerWebSocketHandler` |
| 14 | 🟢 LOW | Прочее (slug enumeration, static salt, MCP client auth NONE) | см. ниже |

---

## 1. 🔴 CRITICAL — Глобальный OAuth Jira-токен ломает изоляцию тенантов

- **Файлы:** `backend/src/main/java/com/leadboard/auth/OAuthTokenRepository.java:14`, `auth/OAuthService.java:218-240`, `jira/JiraClient.java:69-83`, `tenant/TenantSyncScheduler.java:50-53`
- **Категория:** cross-tenant data exposure / broken isolation
- **Confidence:** 8/10

**Описание.** `OAuthTokenRepository.findLatestToken()` выполняет запрос без фильтра по пользователю/тенанту:
```sql
SELECT t FROM OAuthTokenEntity t JOIN FETCH t.user ORDER BY t.updatedAt DESC LIMIT 1
```
Таблица `oauth_tokens` — `@Table(schema="public")`, общая для всех тенантов. `OAuthService.getValidAccessToken()` и `getCloudIdForCurrentUser()` вызывают именно этот глобальный метод (несмотря на «CurrentUser» в имени — там нет ни `SecurityContext`, ни `TenantContext`). `JiraClient.search()` пробует OAuth-ветку первой (`if accessToken != null && cloudId != null`), а per-tenant Basic Auth из `JiraConfigResolver` используется только как fallback.

**Сценарий.** Фоновый `TenantSyncScheduler` ставит `TenantContext = A`, но `getValidAccessToken()`/`getCloudIdForCurrentUser()` возвращают токен и `cloudId` пользователя того тенанта, чей токен обновился последним (при каждом логине/refresh — примерно раз в час). В результате sync тенанта A ходит в Jira-инстанс чужого тенанта B и пишет чужие данные в схему A. Особо опасный детерминированный случай: если во всём деплое OAuth-токен только у одного пользователя — sync **всех** тенантов стабильно бьёт по его Jira-сайту.

**Исправление.** Резолвить токен/`cloudId` строго по текущему тенанту (per-tenant OAuth-запись) или в multi-tenant режиме вообще не использовать глобальную OAuth-ветку — брать per-tenant Basic Auth из `JiraConfigResolver`. Уже существует корректный `getValidAccessTokenForUser(accountId)` — он должен использоваться и в sync-пути, а не только в симуляции.

---

## 2. 🟠 HIGH — Само-присоединение к чужому тенанту через OAuth callback

**Статус: исправлено в [F82](features/F82_JIRA_ACCESS_MEMBERSHIP.md)** (2026-07-02) — join
гейтится доступом к Jira-сайту тенанта вместо инвайтов; существующие членства сверяются на
каждом логине + фоновым `TenantAccessReconciler`, теряют доступ → деактивируются (не удаляются).

- **Файлы:** `auth/OAuthController.java:44-51`, `auth/OAuthService.java:159-173`, `tenant/TenantService.java:69-81`, `auth/LeadBoardAuthenticationFilter.java:61-73`
- **Категория:** broken access control / cross-tenant
- **Confidence:** 9/10

**Описание.** `/oauth/atlassian/**` — `permitAll` (`SecurityConfig.java:60`). `OAuthController.resolveTenantId(slug)` берёт тенант из анонимного query-параметра `?tenant=<slug>` через `findBySlug`, без всякой проверки связи вызывающего с тенантом. В `OAuthService.handleCallback` (159-173), если `tenantId != null` и пользователь ещё не член — он молча добавляется в `tenant_users` (`tenantHasAnyUsers ? MEMBER : ADMIN` → для существующего тенанта = MEMBER). `TenantService.addUserToTenant` не делает никаких проверок (нет приглашений/allowlist/approval — grep по `invit|allowlist|approv|whitelist` пуст). Membership-гейт в `LeadBoardAuthenticationFilter` — единственный барьер, и он теперь пройден.

**Сценарий.**
1. Атакующий (любой Atlassian-аккаунт) открывает `GET /oauth/atlassian/authorize?tenant=<victim-slug>` (анонимно).
2. Логинится своим аккаунтом; `state` несёт victim `tenantId`.
3. Callback → `addUserToTenant(victim, attacker, MEMBER)`, сессия с `tenantId=victim`.
4. Запросы к `victim.<host>/api/**` → фильтр находит членство → MEMBER читает доску, планирование, метрики, teams жертвы (`/api/**` требует лишь `.authenticated()`).

Slug = субдомен, легко энумерируется (см. п.14).

**Исправление.** Не добавлять пользователей в существующий тенант автоматически. Auto-provision (ADMIN) — только для первого пользователя нового тенанта. Все последующие членства — через явное приглашение (pre-created `tenant_users` в pending-состоянии или подписанный invite-токен) либо email-domain allowlist. Отклонять callback, если `!alreadyMember && tenantHasAnyUsers` и нет валидного инвайта.

---

## 3. 🟠 HIGH — Chat/MCP write-инструменты без проверки роли (privilege escalation)

- **Файлы:** `chat/tools/ChatToolExecutor.java:766,783,813,831,848,1057,1073,1089,1104`; `chat/ChatController.java:27`; `mcp/McpToolAdapter.java:98`
- **Категория:** broken access control / privilege escalation
- **Confidence:** 9/10

**Описание.** Все write-инструменты (`transition_issue`, `log_work`, `create_issue`, `add_comment`, `assign_issue`, `triage_matrix`, `assign_epic_quarter`, `set_epic_boost`, `set_rough_estimate`) гейтятся только `authorizationService.isAuthenticated()` — роль не проверяется. Underlying-сервисы (`MatrixService`, `EpicService`, `QuarterlyPlanningService`, `JiraWriteService`) не имеют собственных `@PreAuthorize`. Вся ролевая защита живёт только в REST-контроллерах, которые строже:
- `MatrixController.triage` → `hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')`
- `QuarterlyPlanningController.setEpicBoost/assignEpicToQuarter` → TEAM_LEAD+
- `EpicController.updateRoughEstimate` → TEAM_LEAD+

`/api/chat` и MCP `tools/call` доступны с ролью `isAuthenticated()`; дефолтная роль нового пользователя = MEMBER (у VIEWER — только `board:view`).

**Сценарий.** VIEWER/MEMBER через `/api/chat` или MCP `tools/call` вызывает `triage_matrix`, `set_epic_boost`, `assign_epic_quarter`, `set_rough_estimate` (в REST — TEAM_LEAD+) и пишет в Jira (`transition_issue`, `log_work`, `create_issue`, `add_comment`, `assign_issue`). `destructiveHint` в MCP — клиентская подсказка, не серверный контроль.

**Исправление.** Продублировать серверные проверки роли/команды внутри каждого write-инструмента (`canManageTeam(teamId)`, `hasRole(...)` аналогично REST) или маршрутизировать инструменты через уже защищённые методы. Не полагаться на LLM/`destructiveHint`.

---

## 4. 🟠 HIGH — JQL-инъекция через `existingStoryKey` (Planning Poker)

- **Файлы:** `jira/JiraClient.java:271`, `poker/service/PokerSessionService.java:131`, `poker/dto/AddStoryRequest.java`, `poker/controller/PokerController.java`
- **Категория:** jql_injection
- **Confidence:** 8/10

**Описание.** `JiraClient.getSubtasks()` строит JQL сырой конкатенацией: `String jql = "parent = " + parentKey;`. `parentKey` = `story.getStoryKey()` из пользовательского поля `AddStoryRequest.existingStoryKey`, у которого нет ни `@Pattern`, ни `@NotBlank`. URL-кодирование транспорта синтаксис JQL не нейтрализует — Jira исполняет запрос дословно. Poker включён всегда (нет feature-flag).

**Сценарий.** Аутентифицированный пользователь:
1. `POST /api/poker/sessions/{id}/stories` с `createInJira=false`, `existingStoryKey = "X OR project = SECRET"`.
2. `POST /api/poker/stories/{storyId}/final?updateJira=true` → `getSubtasks("X OR project = SECRET")`.
3. `updateSubtaskEstimates` затем **пишет** оценки (`updateEstimate`, `timetracking.originalEstimate`) в задачи, возвращённые инъектированным JQL. Чтение «слепое» (результат не отдаётся напрямую), но запись в произвольные задачи реальна. Если Jira-вызов идёт под Basic Auth сервисного аккаунта — область прав шире прав пользователя.

**Исправление.** Валидировать ключ строгим regex (`^[A-Z][A-Z0-9]+-\d+$`) на уровне DTO (`@Pattern`) и/или в `getSubtasks`; квотировать/экранировать значение (`parent = "<escaped>"`); дополнительно проверять принадлежность задачи проекту/тенанту.

---

## 5. 🟡 MEDIUM — MCP JWT: обход проверки членства/отзыва при отсутствии `tenant_id`

- **Файл:** `mcp/oauth/McpJwtContextFilter.java:83,93-98,99-113`
- **Категория:** authorization / revocation bypass
- **Confidence:** 7/10

**Описание.** `role` инициализируется глобальной ролью пользователя (`user.getAppRole()`), а проверка членства/отзыва (F80 §4.3) выполняется только внутри `if (tenantId != null)`. Если у JWT нет claim `tenant_id`, а у пользователя 0 либо >1 членств — auto-resolve не срабатывает, `tenantId` остаётся null, и пользователь аутентифицируется с глобальной ролью без tenant-контекста. Первый зарегистрированный пользователь получает global ADMIN (`OAuthService.java:127`).

**Сценарий.** Пользователь, удалённый из всех тенантов, но с валидным MCP-токеном (access 60 мин, refresh до 30 дней), продолжает вызывать `/mcp` с сохранённой глобальной ролью — вплоть до ADMIN. Контроль отзыва не срабатывает.

**Исправление.** Отклонять (401) MCP-запрос, если тенант не резолвится; всегда перепроверять членство перед аутентификацией; не откатываться на глобальную persisted-роль для MCP.

---

## 6. 🟡 MEDIUM — Chat/MCP read-инструменты: обход team-скоупинга

- **Файл:** `chat/tools/ChatToolExecutor.java:1138-1141` (`checkTeamAccess` возвращает `true` при `teamId==null`); без проверки вовсе: `rice_ranking:549`, `project_list:524`
- **Категория:** horizontal information disclosure (intra-tenant)
- **Confidence:** 8/10

**Описание.** Для non-admin team-изоляция применяется только когда передан `teamId`. Опуская `teamId`, MEMBER/TEAM_LEAD получает `board_summary`, `task_search`, `bug_metrics`, `closed_tasks`, `epic_progress` и т.д. по всем командам тенанта; `rice_ranking` и `project_list` не скоупятся никогда. Системный промпт прямо декларирует, что MEMBER/TEAM_LEAD «должны видеть данные только своих команд» — enforcement отсутствует. Cross-tenant утечки нет (`TenantContext` схему держит).

**Исправление.** При `teamId==null` и не-admin ограничивать результат `authorizationService.getUserTeamIds()`; добавить team-скоупинг в `rice_ranking` и `project_list`.

---

## 7. 🟡 MEDIUM — OAuth login CSRF: `state` не привязан к браузеру

- **Файлы:** `auth/OAuthService.java:72-101`, `auth/OAuthController.java:53-71`
- **Категория:** authentication / login CSRF / session fixation
- **Confidence:** 7/10

**Описание.** `state` — случайный UUID в глобальной in-memory `ConcurrentHashMap`, проверяется только на существование/срок. Он не привязан к браузеру инициатора (нет state-cookie/PKCE на Atlassian-леге), глобальный CSRF отключён.

**Сценарий.** Атакующий проходит consent своим аккаунтом, перехватывает `callback?code=...&state=...` не погашая его, отдаёт URL жертве. Браузер жертвы открывает callback → backend создаёт сессию **аккаунта атакующего** и ставит `LEAD_SESSION` жертве. Жертва работает в аккаунте атакующего; введённые ею данные видны атакующему. `SameSite=Lax` не спасает — флоу инициирован атакующим.

**Исправление.** Привязать `state` к браузеру инициатора: короткоживущая `HttpOnly`-cookie, значение которой должно совпасть с возвращённым `state` (или хранить state в pre-auth сессии). Отклонять callback с «чужим» state.

---

## 8. 🟡 MEDIUM — CORS: bare `*` + credentials → отражение Origin

- **Файл:** `config/WebConfig.java:36-68`
- **Категория:** CORS misconfiguration
- **Confidence:** 7/10

**Описание.** Любое значение `CORS_ALLOWED_ORIGINS` с `*` (включая литерал `"*"`) уходит в `allowedOriginPatterns(...)` при `allowCredentials(true)` на `/api/**` и `/oauth/**`. В отличие от `allowedOrigins("*")` (Spring отклонил бы), `allowedOriginPatterns("*")` заставляет Spring **отражать** `Origin` в `Access-Control-Allow-Origin` вместе с `Access-Control-Allow-Credentials: true`.

**Смягчение:** основной креденшл — cookie `LEAD_SESSION` (`HttpOnly` + `SameSite=Lax`), браузер не приложит её к cross-site fetch. Это footgun в конфиге, а не безусловный пробой.

**Исправление.** Отклонять/игнорировать bare `*` при `allowCredentials(true)`; принимать только scoped-паттерны вида `https://*.onelane.ru`; fail-fast при `*` + credentials.

---

## 9. 🟡 MEDIUM — Session-cookie `Secure` по умолчанию `false`

- **Файлы:** `application.yml:14` (`cookie-secure: ${APP_SESSION_COOKIE_SECURE:false}`), не переопределён в `application-prod.yml`; cookie ставится в `auth/OAuthController.java:107`
- **Категория:** session management
- **Confidence:** 7/10

**Описание.** Java-дефолт `AppProperties.Session.cookieSecure=true` перекрыт YAML-плейсхолдером `:false`. Если оператор не выставит `APP_SESSION_COOKIE_SECURE=true`, 30-дневный `LEAD_SESSION` уходит по plaintext HTTP (mixed content, случайная `http://`-ссылка, downgrade/MITM в одной сети) → перехват и захват аккаунта. (`HttpOnly`/`SameSite=Lax` выставлены.) Prod работает за nginx TLS, но fail-open опасен.

**Исправление.** Дефолт `cookie-secure: true`; явно `APP_SESSION_COOKIE_SECURE=true` в `application-prod.yml`. Fail-closed.

---

## 10. 🟢 LOW — MCP debug static-token бэкдор

- **Файлы:** `mcp/McpDebugAuthFilter.java:61-96`, `application.yml:120-124`
- **Confidence:** 7/10

При `mcp.enabled=true` и `mcp.oauth-enabled=false` `/mcp` аутентифицируется единственным статическим bearer-токеном, резолвящимся в фиксированного пользователя+тенант с его ролью (возможно ADMIN). Сравнение constant-time, по умолчанию выключено, помечено «local only». Риск — если удалённо включить MCP с выключенным OAuth: нестираемый, неротируемый shared-secret к фикс-аккаунту. **Исправление:** hard-fail старта при `mcp.enabled && !mcp.oauth-enabled` под prod-профилем.

---

## 11. 🟢 LOW — Неаутентифицированное чтение workflow-конфига тенанта

- **Файлы:** `config/controller/PublicConfigController.java:24-80`, `SecurityConfig.java:69-70`
- **Confidence:** 7/10

`/api/config/workflow/**` — `permitAll`, тенант резолвится по субдомену/`X-Tenant-Slug`. Анонимный запрос к `victim.<host>/api/config/workflow/roles` отдаёт роли, категории типов задач и status-mappings чужого тенанта. Данные низкочувствительные (имена ролей, цвета, маппинги), вероятно для pre-login рендера. **Исправление:** если pre-login-рендер не нужен — перенести под `.authenticated()`; иначе подтвердить, что чувствительных полей нет.

---

## 12. 🟢 LOW — Rate-limit обходится подменой `X-Forwarded-For`

- **Файл:** `config/RateLimitFilter.java:144-155`
- **Confidence:** 7/10

`getClientIp()` берёт первый токен `X-Forwarded-For` до fallback на `X-Real-IP`/`remoteAddr`. Клиент, вращая поддельный первый hop, получает новый bucket на каждый запрос — обходя per-IP лимиты (в т.ч. OAuth 20/min, регистрация 10/min). **Исправление:** читать IP позиционно от доверенного прокси, а не сырой первый элемент XFF; убедиться, что nginx перезаписывает заголовок.

---

## 13. 🟢 LOW — WebSocket Poker без проверки членства в тенанте (harden)

- **Файлы:** `poker/websocket/PokerWebSocketHandler.java:307-330`, `poker/websocket/PokerWebSocketConfig.java`, `SecurityConfig.java:76`
- **Confidence:** 7/10 (как harden; заявленный HIGH cross-tenant — **ложное срабатывание**)

`isAuthenticated()` валидирует только cookie-сессию (`findValidSession` не фильтрует `tenant_id`), `/ws/**` — `permitAll`, `TenantContext` в WS-потоки не пробрасывается. **Но** cross-tenant чтения/записи НЕ происходит: poker-таблицы удалены из `public` (`V45__drop_legacy_public_tables.sql`) и живут только в tenant-схемах, а WS message-потоки резолвятся на `public` — операции **падают**, а не утекают. `roomCode` — 6 символов, `SecureRandom`, не перечисляем. **Исправление (для корректности + defense-in-depth):** пробрасывать `TenantContext` в WS через `HandshakeInterceptor` и проверять `session.tenant_id` против тенанта комнаты.

---

## 14. 🟢 LOW — Прочее (defense-in-depth)

- **Slug enumeration:** `GET /api/public/tenants/check-slug` (`TenantRegistrationController.java:35-44`, `permitAll`) — оракул для перечисления slug тенантов; enabler для п.2. Первичное исправление — закрыть п.2.
- **Session не привязана к тенанту:** `SessionEntity.tenant_id` хранится, но `LeadBoardAuthenticationFilter` его не проверяет — тенант берётся из субдомена, гейт только по членству. Dead data; при чрезмерно либеральном создании `tenant_users` (п.2) сессия становится cross-tenant ключом. Привязать сессию к тенанту-эмитенту (defense-in-depth).
- **Статичный salt `"deadbeef"`** (`EncryptionService.java:28`) — общий для всех деплоев. Impact низкий (Spring `Encryptors.text` трактует salt как non-secret, IV случайный на значение; безопасность держится на `TOKEN_ENCRYPTION_KEY`). Сделать per-deployment.
- **MCP OAuth client регистрирует `ClientAuthenticationMethod.NONE`** даже при заданном секрете (`OAuthServerConfig.java:178`) — секрет фактически опционален. Приемлемо для public-client + PKCE с фикс redirect-URI, но лучше убрать `NONE` при наличии секрета.
- **`X-Forwarded-Host` и tenant resolution:** при `forward-headers-strategy: framework` (prod) `getServerName()` зависит от `X-Forwarded-Host`. Убедиться, что nginx перезаписывает/вырезает клиентский `X-Forwarded-Host` (иначе клиент влияет на выбор тенанта; сам по себе не даёт аутентифицированного доступа).

---

## Проверено и признано безопасным

- **SQL-слой:** все `@Query` (native/JPQL) — именованные параметры; pgvector параметризован; board/DQ search — параметризованные repo-методы + in-memory substring; schema-интерполяция защищена regex-валидацией. Path traversal нет.
- **Frontend:** ноль `dangerouslySetInnerHTML`/`innerHTML`/`eval`/`document.write`; токены не в `localStorage` (httpOnly cookie); хардкод-секретов нет; `rel="noopener noreferrer"` на внешних ссылках.
- **Crypto/secrets:** нет MD5/SHA1/DES/ECB; случайность через `SecureRandom`/`UUID.randomUUID()`; OAuth-токены шифруются at-rest (AES-CBC, random IV); нет `TrustAllCerts`/отключения проверки сертификатов; хардкод-секретов в коде нет (всё из env).
- **JWT (MCP AS):** RS256, подпись и `exp`/issuer/audience проверяются, нет `alg:none`; PKCE S256 обязателен; redirect-URI — серверный whitelist.
- **Deserialization / command injection:** нет `ObjectInputStream`, polymorphic Jackson, YAML load, `Runtime.exec`, `ProcessBuilder`.
- **SSRF / Telegram:** внешние вызовы (LLM, embeddings, Jira) — config-driven host; Telegram только outbound на фикс-хост, входящего webhook нет.
- **Multi-tenancy базис:** `TenantContext` (ThreadLocal) очищается в `finally`; `search_path` сбрасывается на `public` при возврате соединения; схемы валидируются regex; `LeadBoardAuthenticationFilter`/`TenantFilter` отклоняют не-членов и unknown-slug (не откатываются на public).
