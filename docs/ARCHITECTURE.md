# SliceLink architecture

## Current state

SliceLink is a modular-monolith foundation with separate backend and frontend applications. No business modules or business behavior are implemented in Phase 1.

## Intended modular boundaries

The backend will later organize its code around authentication, users, URLs, redirects, analytics, rate limiting, and administration. These are logical module boundaries inside one deployable application, not microservices.

## Data and integration roles

- PostgreSQL will be the authoritative data store.
- Redis will be introduced as a cache and performance layer.
- Kafka will carry durable asynchronous events, beginning with click events.

## Interfaces and operations

Future HTTP APIs will be RESTful and versioned. The backend foundation exposes Spring Boot operational capabilities through Actuator and is prepared for OpenAPI documentation. Local services are run through Docker Compose.
