# F80 — Подключение к MCP-серверу Lead Board (фаза 1, локально с ПК)

Инструкция для подключения приложения Claude к доске через MCP по отладочному
bearer-токену. **Это фаза 1 — только для локального ПК** (Claude Code CLI / MCP
Inspector). Подключение с телефона / claude.ai требует OAuth 2.1 — см. План 2.

## 1. Настроить переменные окружения

В `backend/.env` (не коммитить):

```bash
MCP_ENABLED=true
MCP_DEBUG_TOKEN=<сгенерировать: openssl rand -hex 32>
MCP_DEBUG_TENANT_SLUG=test2
MCP_DEBUG_ACCOUNT_ID=<atlassian_account_id вашего пользователя>
```

`MCP_DEBUG_ACCOUNT_ID` для локального tenant `test2`:

```bash
PGPASSWORD=leadboard psql -h localhost -U leadboard -d leadboard -tAc \
"select u.atlassian_account_id from public.users u \
 join public.tenant_users tu on tu.user_id=u.id \
 join public.tenants t on t.id=tu.tenant_id where t.slug='test2'"
```

## 2. Запустить backend

```bash
cd backend && ./gradlew bootRun
```

MCP-сервер поднимется на `http://localhost:8080/mcp` (Streamable HTTP).

## 3. Подключить в Claude Code CLI

```bash
claude mcp add --transport http lead-board http://localhost:8080/mcp \
  --header "Authorization: Bearer <MCP_DEBUG_TOKEN>"
```

Проверка в сессии Claude Code:

```
/mcp
```

Должен показать сервер `lead-board` подключённым; инструменты: `ping`,
`board_summary`, `team_metrics`, …, `team_readiness_briefing` (16 шт.).

## 4. Проверить компаньона

Спросите в Claude:

> Дай брифинг готовности команды

Claude вызовет `team_readiness_briefing` и вернёт человекочитаемую сводку по 4 линзам
(планирование / загрузка / качество данных / поток) с реальными цифрами и ключами задач.

## Альтернатива: MCP Inspector (без Claude)

```bash
npx @modelcontextprotocol/inspector
```

Transport: **Streamable HTTP**, URL `http://localhost:8080/mcp`,
заголовок `Authorization: Bearer <MCP_DEBUG_TOKEN>` → Connect → List Tools / Call Tool.

## Безопасность

- Bearer-токен фазы 1 — **только локально**. claude.ai/телефон его не принимают
  (там только OAuth 2.1) — это План 2.
- При `MCP_ENABLED=false` сервер не поднимается, `/mcp` отвечает 401.
- RBAC и tenant-изоляция наследуются: инструменты исполняются от имени
  пользователя из `MCP_DEBUG_ACCOUNT_ID` с его ролью в tenant.
- Запрос без валидного токена → 401.

## Ограничения фазы 1 (доработки — следующие фазы)

- Линзы `load` и `flow` — эвристики (полный расчёт: capacity F55 vs назначенный
  объём + worklog по людям; stuck epics из F79 + at-risk дедлайны).
- OAuth 2.1, деплой с HTTPS, подключение с телефона — План 2.
- Проактивный ежедневный пуш (Telegram/email) — MCP это pull; отдельная фича.
