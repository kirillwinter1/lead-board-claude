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

### Core
- `GET /api/health` - Health check endpoint
- `GET /api/board` - Get hierarchical board (Epic → Story → Sub-task)
- `POST /api/sync/trigger` - Trigger Jira sync

### Planning & AutoScore
- `GET /api/planning/forecast?teamId=1` - Get forecast for team epics
- `GET /api/epics/{epicKey}/stories` - Get stories sorted by AutoScore
- `PATCH /api/stories/{storyKey}/priority` - Update story manual priority boost
- `POST /api/planning/recalculate-stories` - Recalculate AutoScore for stories

### Teams & Members
- `GET /api/teams` - List teams
- `GET /api/teams/{id}/members` - List team members
- `PUT /api/teams/{id}/planning-config` - Update planning configuration

### Data Quality
- `GET /api/data-quality?teamId=1` - Get data quality violations

## Project Structure

```
lead-board-claude/
├── backend/         # Spring Boot application
├── frontend/        # React + Vite application
├── docker-compose.yml
└── README.md
```
