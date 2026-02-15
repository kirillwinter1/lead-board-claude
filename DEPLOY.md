# Деплой на продакшн (onelane.ru)

## Сервер
- **IP:** 79.174.94.70
- **SSH:** `ssh root@79.174.94.70`
- **Проект на сервере:** `/opt/leadboard/`
- **Nginx конфиг:** `/etc/nginx/sites-available/onelane`

## Сборка образов (локально на Mac)
```bash
# Frontend
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend
docker build --platform linux/amd64 -t onelane-frontend:latest .

# Backend
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/backend
docker build --platform linux/amd64 -t onelane-backend:latest .
```

**ВАЖНО:** Флаг `--platform linux/amd64` обязателен (Mac = arm64, сервер = amd64)

## Загрузка образов на сервер
```bash
# Сохранить образ
docker save onelane-frontend:latest | gzip > /tmp/onelane-frontend.tar.gz
docker save onelane-backend:latest | gzip > /tmp/onelane-backend.tar.gz

# Загрузить на сервер
scp /tmp/onelane-frontend.tar.gz root@79.174.94.70:/root/
scp /tmp/onelane-backend.tar.gz root@79.174.94.70:/root/

# Загрузить образы на сервере
ssh root@79.174.94.70 "docker load < /root/onelane-frontend.tar.gz"
ssh root@79.174.94.70 "docker load < /root/onelane-backend.tar.gz"
```

## Запуск контейнеров
```bash
ssh root@79.174.94.70 "cd /opt/leadboard && docker compose -f docker-compose.prod.yml up -d"
```

## Проверка статуса
```bash
ssh root@79.174.94.70 "docker ps"
ssh root@79.174.94.70 "docker logs onelane-backend 2>&1 | tail -30"
ssh root@79.174.94.70 "docker logs onelane-frontend 2>&1 | tail -10"
```

## Перезапуск контейнеров
```bash
ssh root@79.174.94.70 "cd /opt/leadboard && docker compose -f docker-compose.prod.yml down && docker compose -f docker-compose.prod.yml up -d"
```

## Полный деплой (одной командой)
```bash
# Frontend only
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend && \
docker build --platform linux/amd64 -t onelane-frontend:latest . && \
docker save onelane-frontend:latest | gzip > /tmp/onelane-frontend.tar.gz && \
scp /tmp/onelane-frontend.tar.gz root@79.174.94.70:/root/ && \
ssh root@79.174.94.70 "docker load < /root/onelane-frontend.tar.gz && cd /opt/leadboard && docker compose -f docker-compose.prod.yml up -d frontend"
```

## Структура на сервере
```
/opt/leadboard/
├── docker-compose.prod.yml   # Конфиг docker-compose
└── .env                      # Переменные окружения (НЕ используется, всё в docker-compose.prod.yml)

/etc/nginx/sites-available/onelane  # Nginx reverse proxy (SSL + proxy на localhost:3000)
```

## Сеть Docker
- Сеть: `leadboard_onelane-net`
- Backend алиас: `backend` (nginx в frontend ищет этот hostname)
- Frontend проксирует `/api/*` на `http://backend:8080`

## Порты
- `127.0.0.1:3000` — frontend (nginx внутри контейнера)
- `127.0.0.1:8080` — backend (Spring Boot)
- Внешний nginx проксирует 443 → localhost:3000

## Troubleshooting

**SSH зависает:**
- Сервер может быть перегружен сборкой Docker
- Подождать 1-2 минуты и попробовать снова

**Backend не стартует (Connection refused to localhost:5432):**
- Контейнер запущен без переменных окружения
- Использовать `docker compose` вместо `docker run`

**Frontend рестартится (host not found in upstream "backend"):**
- Контейнеры не в одной сети
- Backend должен иметь алиас `backend` в сети

**Сайт недоступен:**
```bash
# Проверить nginx на сервере
ssh root@79.174.94.70 "systemctl status nginx"
ssh root@79.174.94.70 "nginx -t"

# Проверить контейнеры
ssh root@79.174.94.70 "curl -s http://localhost:3000"
ssh root@79.174.94.70 "curl -s http://localhost:8080/api/health"
```
