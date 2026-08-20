# SliceLink Production Deployment & Infrastructure Guide

This guide details the steps to build, orchestrate, deploy, and operate the complete SliceLink platform using Docker and Docker Compose.

---

## 1. Architecture Overview

SliceLink is composed of 5 containerized services running on a private Docker bridge network (`slicelink-network`):

```
                                    +-----------------------------------------+
                                    |               Host Network              |
                                    +--------------------+--------------------+
                                                         |
                                  Port 80 (HTTP)         |        Port 8080 (API)
                                         |               |               |
                                         v               |               v
+--------------------------------------------------------+------------------------------------+
| slicelink-network (Private Docker Bridge)              |                                    |
|                                                        |                                    |
|   +-----------------------+               +------------+----------+                         |
|   |   slicelink-frontend  |               |   slicelink-backend   |                         |
|   |  (Nginx 1.27 + React) | ------------> | (Spring Boot 4 / JRE) |                         |
|   +-----------------------+   Proxy /api  +------------+----------+                         |
|                                                        |                                    |
|                                       +----------------+----------------+                   |
|                                       |                |                |                   |
|                                       v                v                v                   |
|                            +--------------------+ +----------+ +------------------+         |
|                            | slicelink-postgres | |  redis   | | slicelink-kafka  |         |
|                            |  (PostgreSQL 18)   | | (Redis 8)| |  (Apache Kafka)  |         |
|                            +--------------------+ +----------+ +------------------+         |
+---------------------------------------------------------------------------------------------+
```

### Services Summary

| Service | Image / Base | Internal Address | Host Port | Role |
| :--- | :--- | :--- | :--- | :--- |
| **`frontend`** | `nginx:1.27-alpine` (multi-stage) | `http://frontend:80` | `80` | SPA static web server & reverse proxy |
| **`backend`** | `eclipse-temurin:25-jre` (multi-stage) | `http://backend:8080` | `8080` | Core REST API, JWT auth, redirect engine |
| **`postgres`** | `postgres:18-alpine` | `postgres:5432` | `5432` | Authoritative relational database store |
| **`redis`** | `redis:8-alpine` | `redis:6379` | `6379` | High-speed redirect cache & rate limiter |
| **`kafka`** | `apache/kafka:4.1.1` | `kafka:9092` | `9092` | KRaft durable event streaming for click events |

---

## 2. Prerequisites

- **Docker Desktop** (or Docker Engine 24+)
- **Docker Compose v2** (included with modern Docker Desktop)
- **Node.js 22+ & Java 25** (for local development outside containers)

---

## 3. Environment Configuration

1. Create a `.env` file from the provided template:
   ```bash
   cp .env.example .env
   ```
2. Adjust environment variables as needed:

| Variable | Default | Description |
| :--- | :--- | :--- |
| `POSTGRES_DB` | `slicelink` | PostgreSQL database name |
| `POSTGRES_USER` | `slicelink` | Database username |
| `POSTGRES_PASSWORD` | *(safe dev default)* | Database user password (**Change in production!**) |
| `POSTGRES_PORT` | `5432` | Port exposed on Docker host |
| `REDIS_PORT` | `6379` | Redis port exposed on Docker host |
| `KAFKA_PORT` | `9092` | Kafka broker port exposed on Docker host |
| `KAFKA_CLUSTER_ID` | `MkU3OEVBNTcwNTJENDM2Qk` | Base64 KRaft cluster identifier |
| `BACKEND_PORT` | `8080` | Spring Boot backend host port |
| `FRONTEND_PORT` | `80` | Nginx frontend host port |
| `SLICELINK_JWT_SECRET` | *(32+ char string)* | HMAC-SHA256 signature key for JWT tokens (**Secret**) |
| `SLICELINK_CORS_ALLOWED_ORIGIN`| `http://localhost` | Allowed frontend origin for CORS headers |

> [!CAUTION]
> Never commit `.env` containing real credentials to version control. `.env` is ignored by `.gitignore`.

---

## 4. Starting and Stopping the Platform

### Starting the Complete Stack

To build container images and start all 5 services in detached mode:

```bash
docker compose -f docker/compose.yaml --env-file .env up -d --build
```

Docker Compose enforces strict service dependencies:
1. `postgres`, `redis`, and `kafka` start first and perform health checks.
2. `backend` starts once all three databases are `service_healthy`.
3. `frontend` starts once `backend` is `service_healthy`.

### Stopping the Stack

To gracefully stop all services while preserving data volumes:

```bash
docker compose -f docker/compose.yaml down
```

To stop and remove all persistent volumes (fresh start):

```bash
docker compose -f docker/compose.yaml down -v
```

---

## 5. Monitoring & Operational Health Checks

### Check Container Status

```bash
docker compose -f docker/compose.yaml ps
```

All 5 containers should display `healthy` status.

### Real-Time Log Streaming

Stream logs for all services:
```bash
docker compose -f docker/compose.yaml logs -f
```

Stream logs for a specific service:
```bash
docker compose -f docker/compose.yaml logs -f backend
docker compose -f docker/compose.yaml logs -f frontend
docker compose -f docker/compose.yaml logs -f kafka
```

### Backend Actuator Health

Query the live backend health status:
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

---

## 6. Database Migrations & Kafka Initialization

- **Flyway Migrations**: The backend automatically applies Flyway migrations (`V1__create_users_and_refresh_tokens.sql`, `V2__create_urls.sql`) on startup.
- **Kafka Topics**: The backend automatically provisions the click events topic (`slicelink.url.clicks.v1`) on startup if it does not already exist.

---

## 7. Production Security Checklist

- [x] Unprivileged user execution in backend container (`USER slicelink:slicelink`).
- [x] Multi-stage container builds minimizing final attack surface.
- [x] Security HTTP headers injected by Nginx (`X-Frame-Options`, `X-Content-Type-Options`, `X-XSS-Protection`).
- [x] Container internal networking isolated on dedicated `slicelink-network` bridge.
- [x] Environment variable isolation for all secrets, credentials, and configuration.
- [x] Zero secrets committed to source control.
