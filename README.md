# Lead Board

Project management dashboard for tracking team leads and tasks.

## Prerequisites

- Java 21
- Node.js 18+
- Docker (for PostgreSQL)

## Quick Start

### 1. Start PostgreSQL

```bash
docker-compose up -d
```

### 2. Run Backend

```bash
cd backend
./gradlew build
./gradlew bootRun
```

Backend will be available at http://localhost:8080

### 3. Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend will be available at http://localhost:5173

## API Endpoints

- `GET /api/health` - Health check endpoint

## Project Structure

```
lead-board-claude/
├── backend/         # Spring Boot application
├── frontend/        # React + Vite application
├── docker-compose.yml
└── README.md
```
