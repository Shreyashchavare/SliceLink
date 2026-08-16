# SliceLink

SliceLink is a scalable URL shortener being developed as a modular monolith. Phase 1 establishes the repository, local development environment, and application foundations only. Business features are intentionally not implemented yet.

## Technology stack

- Backend: Java 25, Spring Boot 4, Maven
- Frontend: React, TypeScript, Vite
- Data and messaging: PostgreSQL, Redis, Apache Kafka
- Operations: Docker Compose, Actuator, Micrometer, OpenAPI

Planned later additions include Spring Security with JWT, React Router, TanStack Query, Axios, Tailwind CSS, Recharts, Prometheus, Grafana, OpenTelemetry, Testcontainers, and k6.

## Repository structure

```text
backend/           Spring Boot modular-monolith foundation
frontend/          React and Vite application foundation
docs/              Architecture and roadmap documentation
docker/            Local Docker development configuration
infrastructure/    Reserved for future infrastructure assets
tests/             Reserved for cross-application tests
.github/workflows/ Reserved for CI workflows
```

## Prerequisites

- Java 25
- Docker Desktop with Docker Compose
- Node.js and npm

The backend includes the Maven Wrapper, so a global Maven installation is not required.

## Development

Copy `.env.example` to `.env` and adjust local values if necessary. Do not commit `.env`.

Start the local dependency services:

```powershell
docker compose -f docker/compose.yaml --env-file .env up -d
```

The backend will be started with:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The frontend will be started with:

```powershell
cd frontend
npm run dev
```

## Current status

Phase 1 provides the project foundation. Authentication, URL shortening, redirects, Redis cache behavior, Kafka event handling, analytics, rate limiting, administration, and the dashboard are not implemented.

## Roadmap

The planned phases are documented in [docs/DEVELOPMENT_ROADMAP.md](docs/DEVELOPMENT_ROADMAP.md).
