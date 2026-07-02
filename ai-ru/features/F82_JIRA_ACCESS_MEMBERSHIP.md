# F82. Membership тенанта привязан к доступу в Jira

**Версия:** 0.82.0 | **Дата:** 2026-07-02

## Проблема

`ai-ru/SECURITY_AUDIT.md` §2 (🟠 HIGH): `OAuthController.resolveTenantId(slug)` брал тенант
из анонимного query-параметра `?tenant=<slug>`, а `OAuthService.handleCallback` молча
добавлял любого аутентифицированного через Atlassian пользователя в `tenant_users`
(`tenantHasAnyUsers ? MEMBER : ADMIN`). Единственным барьером было членство в
`tenant_users` — а его создание не проверялось никак. Итог: любой Atlassian-аккаунт мог
самостоятельно присоединиться к чужому тенанту через `/oauth/atlassian/authorize?tenant=<victim-slug>`.

Дополнительно: даже у легитимных членов не было механизма отзыва — уволенный сотрудник,
убранный из Jira, оставался MEMBER/ADMIN в Leadboard навсегда.

## Бизнес-правило

Тенант привязан к конкретному Jira-сайту (`tenant_jira_config.jira_cloud_id`). Пользователь
может состоять в тенанте Leadboard **только если** его Atlassian-аккаунт имеет доступ к
этому Jira-сайту (проверяется через Atlassian `accessible-resources`). Если доступ утерян —
членство деактивируется (не удаляется), пользователь теряет доступ к тенанту.

## Реализация

### Миграция
`V52__tenant_user_active.sql` — добавляет в `tenant_users` (public schema):
`active BOOLEAN NOT NULL DEFAULT true`, `deactivated_at TIMESTAMPTZ`,
`deactivated_reason VARCHAR(255)` + индекс `idx_tenant_users_active`.

`TenantUserEntity` — методы `deactivate(reason)` / `reactivate()` (не удаляют строку,
только переключают флаг + таймстемп + причину).

### Резолвинг Jira-сайтов (OAuthService)
- `getAccessibleCloudIds(accessToken)` — ВСЕ cloudId, доступные аккаунту (не только первый,
  как старый `getCloudId`, который теперь построен поверх этого списка).
- `userHasJiraAccess(accessToken, tenantCloudId)` — есть ли доступ к конкретному сайту.
- `resolveTenantJiraCloudId(tenant)` — читает `tenant_jira_config.jira_cloud_id` тенанта.
  `tenant_jira_config` живёт в схеме тенанта (без колонки `tenant_id`), поэтому метод
  временно переключает `TenantContext` на нужный тенант и восстанавливает прежний контекст
  (по аналогии с `TenantSyncScheduler`).

### Гейт на входе (OAuthService.applyMembershipGate, вызывается из handleCallback)
1. **Bootstrap** — первый пользователь нового тенанта (`tenantHasAnyUsers == false`) —
   всегда ADMIN, без проверки (у тенанта ещё нет Jira-конфига — он его только настроит).
2. **Не член, у тенанта есть cloudId** — join разрешён, только если cloudId тенанта входит
   в список доступных сайтов пользователя (`accessibleCloudIds`). Иначе — отказ
   (`errorCode = jira_access_denied`), в `tenant_users` ничего не создаётся.
3. **Не член, у тенанта НЕТ cloudId** — проверить нельзя → авто-join закрыт (отказ), это
   заменяет старую логику «пускаем любого». Тенант без Jira-конфига просто не принимает
   новых участников через OAuth, пока не подключит Jira.
4. **Уже член** — сверка при каждом логине: если cloudId тенанта задан и доступа больше нет
   → `deactivate("jira_access_lost")`; если доступ снова появился, а членство было
   неактивно → `reactivate()`. Сам вход (создание сессии) не блокируется — принудительное
   применение флага `active` происходит ниже по стеку (фильтры). Если у тенанта нет
   cloudId — сверка пропускается, существующее членство не трогается.

### Редирект при отказе (OAuthController)
`?auth=error` — как раньше для всех прочих ошибок (без изменений). Добавлен необязательный
`&reason=jira_access_denied` — только когда `CallbackResult.errorCode()` заполнен.

### Фоновый отзыв доступа (TenantAccessReconciler)
Новый `@Scheduled`-компонент (`com.leadboard.tenant.TenantAccessReconciler`), по образцу
`TenantSyncScheduler`: дешёвый тик раз в 60с проверяет, не пора ли запустить полный проход
(интервал из конфига), затем — раз в `app.access-reconcile.interval-seconds` (по умолчанию
4 часа) — проходит по ВСЕМ активным `tenant_users`:
- нет cloudId у тенанта → пропуск, членство не трогаем;
- нет сохранённого OAuth-токена у пользователя → пропуск (проверить нельзя);
- `getValidAccessTokenForUser` вернул `null` (refresh не удался — Atlassian отозвал токен) →
  `deactivate("jira_access_lost")`;
- `userHasJiraAccess(token, cloudId) == false` → `deactivate("jira_access_lost")`.

Закрывает разрыв между офф-бордингом в Jira и следующим логином пользователя — если он
просто перестаёт логиниться, его членство не остаётся активным навсегда.

### Enforcement в фильтрах
`TenantUserRepository.findByTenantIdAndUserIdAndActiveTrue(tenantId, userId)` — новый метод
(активные only). Используется в:
- `LeadBoardAuthenticationFilter` — неактивное членство трактуется как «не член» (401 на
  защищённых эндпоинтах), как и раньше для отсутствующего членства.
- `McpJwtContextFilter` — то же самое для MCP JWT-потока (было: `findByTenantIdAndUserId`
  без проверки активности).

Старый `findByTenantIdAndUserId` (без фильтра по `active`) сохранён — используется там, где
нужно видеть и неактивных участников (`AdminController`, `McpDebugAuthFilter` — debug-only
локальный режим, не в проде).

## Конфигурация

```yaml
app:
  access-reconcile:
    enabled: ${APP_ACCESS_RECONCILE_ENABLED:true}
    interval-seconds: ${APP_ACCESS_RECONCILE_INTERVAL_SECONDS:14400}   # 4 часа
```

`AppProperties.AccessReconcile` (`enabled`, `intervalSeconds`). Компонент условный —
`@ConditionalOnProperty(prefix = "app.access-reconcile", name = "enabled", matchIfMissing = true)`.

## Тесты

- `OAuthServiceTest.ApplyMembershipGateTests` — join разрешён/отказан по cloudId, bootstrap,
  тенант без cloudId, деактивация/реактивация существующего членства, восстановление
  `TenantContext` после резолвинга.
- `TenantServiceTest` — `deactivateMembership`/`reactivateMembership`.
- `TenantAccessReconcilerTest` — деактивация при потере доступа / мёртвом токене, пропуск без
  токена и без cloudId, устойчивость батча к ошибке на одной записи.
- `LeadBoardAuthenticationFilterTest` — деактивированное членство отклоняется как не-член.
- `McpJwtContextFilterTest` — то же для MCP JWT-фильтра.

## Не входит в этот фикс

Приглашения/allowlist по email-домену (альтернатива из аудита) — не реализованы: продуктовое
решение — привязка к Jira-доступу вместо инвайтов. `ChatToolExecutor`/`AuthorizationService`
(HIGH #3, privilege escalation) правятся отдельно, параллельно этой задаче — не затронуты.
