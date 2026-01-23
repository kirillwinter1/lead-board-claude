# Инструкции для Claude

## Запуск проекта

### Перед запуском — освободить порты
```bash
lsof -ti:8080 -ti:5173 | xargs kill -9 2>/dev/null || true
```

### Запуск backend (порт 8080)
```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/backend
./gradlew bootRun
```

### Запуск frontend (порт 5173)
```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend
npm run dev
```

## Сборка

### Backend
```bash
cd backend && ./gradlew build
```

### Frontend
```bash
cd frontend && npm run build
```

## Проверка работоспособности

```bash
curl http://localhost:8080/api/health   # Backend
curl http://localhost:5173/api/health   # Frontend (через proxy)
```

## Важно

- **Всегда убивать процессы на портах перед запуском** — не допускать запуск на альтернативных портах
- Backend запускать из директории `backend/`
- Frontend запускать из директории `frontend/`
- Credentials хранятся в `backend/.env` (не коммитить!)

## Структура проекта

```
lead-board-claude/
├── backend/          # Spring Boot (Java 21)
│   ├── .env          # Jira credentials (gitignore)
│   └── src/
├── frontend/         # React + Vite + TypeScript
│   └── src/
├── ai-ru/            # Документация на русском
└── docker-compose.yml
```
