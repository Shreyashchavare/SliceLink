# SliceLink project specification

## Purpose

SliceLink is a scalable URL shortener. It will provide secure URL management, fast redirects, and click analytics while evolving incrementally from a modular monolith.

## Technology decisions

- Java 25 LTS and Spring Boot 4.x, built with Maven
- React, TypeScript, and Vite for the web application
- PostgreSQL as the source of truth
- Redis as a cache and performance layer
- Apache Kafka as the durable event-streaming layer
- Docker and Docker Compose for local development

Spring Security, JWT, Spring Data JPA, Spring Data Redis, Spring Kafka, Actuator, Micrometer, OpenAPI, Prometheus, Grafana, OpenTelemetry, Testcontainers, GitHub Actions, and k6 are planned parts of the architecture.

## Architecture

SliceLink starts as a modular monolith, not as microservices. Backend modules will keep authentication, users, URLs, redirects, analytics, rate limiting, and administration logically separated. This preserves clear boundaries and leaves open the option of extracting services later if that becomes necessary.

## Application direction

- Short URLs will use Base62 encoding over Snowflake-style generated identifiers.
- REST endpoints will be versioned under `/api/v1` when implemented.
- Security will use Spring Security and JWT-based authentication in a later phase.
- PostgreSQL will retain authoritative application data; Redis will never be the system of record.
- Click activity will be published to Kafka for durable asynchronous analytics processing.

## Quality and operations direction

- Tests will grow from application-context tests to unit, integration, and Testcontainers-based tests.
- Docker Compose supports local dependencies first; production containerization follows later.
- GitHub Actions will provide CI/CD direction in a later phase.
- Prometheus, Grafana, and OpenTelemetry will provide metrics, dashboards, and tracing in a later phase.

## Development phases

Development follows the ten phases listed in [docs/DEVELOPMENT_ROADMAP.md](docs/DEVELOPMENT_ROADMAP.md). Phase 1 contains foundation work only; it does not implement business behavior.
