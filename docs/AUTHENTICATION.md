# Authentication

Phase 2 implements stateless JWT-based authentication for the SliceLink modular monolith. All tokens are signed with HMAC-SHA256 using a secret supplied through environment variables. No session state is stored on the server.

## Overview

- Access tokens are short-lived JWTs accepted by the `Authorization: Bearer` header.
- Refresh tokens are rotated on every use. The raw refresh token is never stored — only its SHA-256 hex digest is persisted in PostgreSQL.
- Logout revokes the refresh-token record only. Already-issued access tokens remain valid until their configured TTL expires, because Phase 2 does not implement an access-token revocation list.

---

## Endpoints

All endpoints are versioned under `/api/v1`.

### `POST /api/v1/auth/register`

Registers a new user. Returns `201 Created` with an authentication response containing both tokens and public user data.

**Request body:**
```json
{
  "email": "alice@example.com",
  "password": "SecurePass123!",
  "name": "Alice"
}
```

Password requirements: minimum 12 characters, maximum 72 characters, must contain at least one letter and at least one digit.

**Success response — 201:**
```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "email": "alice@example.com",
    "name": "Alice",
    "status": "ACTIVE",
    "createdAt": "2026-08-16T08:00:00Z"
  }
}
```

**Error responses:** `400 VALIDATION_FAILED` (constraint violations), `409 EMAIL_ALREADY_REGISTERED`.

---

### `POST /api/v1/auth/login`

Authenticates an existing active user. Returns `200 OK` with the same authentication response shape as registration.

**Request body:**
```json
{
  "email": "alice@example.com",
  "password": "SecurePass123!"
}
```

Email comparison is case-insensitive (normalised to lowercase before lookup).

**Error responses:** `400 VALIDATION_FAILED`, `401 INVALID_CREDENTIALS` (wrong email, wrong password, or inactive account — the same error code is returned in all cases to prevent user enumeration).

---

### `POST /api/v1/auth/refresh`

Rotates a refresh token. The submitted refresh token is revoked and a new access/refresh token pair is issued.

**Request body:**
```json
{
  "refreshToken": "<jwt>"
}
```

**Success response — 200:** same authentication response shape as register/login.

**Error responses:** `400 VALIDATION_FAILED`, `401 INVALID_REFRESH_TOKEN` (expired, revoked, malformed, or subject mismatch).

---

### `POST /api/v1/auth/logout`

Revokes the submitted refresh-token record. Returns `204 No Content`. The request body is the same as `/refresh`.

Logout is idempotent: submitting an already-revoked token produces `204` without error.

**Error responses:** `400 VALIDATION_FAILED` (blank token field only — structural JWT problems are swallowed silently and produce `204`).

---

### `GET /api/v1/users/me`

Returns the authenticated user's public profile. Requires a valid access token.

**Request header:** `Authorization: Bearer <access-token>`

**Success response — 200:**
```json
{
  "id": 1,
  "email": "alice@example.com",
  "name": "Alice",
  "status": "ACTIVE",
  "createdAt": "2026-08-16T08:00:00Z"
}
```

The response never contains the password hash.

**Error responses:** `401 UNAUTHORIZED` (missing or invalid token).

---

## Token design

### Access token

- Algorithm: HS256
- Claims: `sub` (user ID as string), `iat`, `exp`, `jti` (UUID), `token_type: "access"`, `email`
- Default TTL: 15 minutes (configurable via `SLICELINK_JWT_ACCESS_TOKEN_TTL`)
- Accepted by `JwtAuthenticationFilter` only when `token_type` is `"access"`

### Refresh token

- Algorithm: HS256
- Claims: `sub` (user ID), `iat`, `exp`, `jti` (UUID), `token_type: "refresh"`, `email`
- Default TTL: 7 days (configurable via `SLICELINK_JWT_REFRESH_TOKEN_TTL`)
- Never accepted by the authentication filter (type check rejects it for endpoint access)
- Validated by `AuthenticationService.refresh()` and `logout()` only

### Refresh-token persistence

The `refresh_tokens` table stores:
- `token_hash` — SHA-256 hex digest of the raw token (64 hex chars)
- `token_id` — the `jti` claim UUID (unique index for fast lookup)
- `user_id` — foreign key to `users`
- `expires_at` — expiry timestamp
- `revoked_at` — set when revoked; `null` while active
- `created_at` — record creation timestamp

Rotation on each `/refresh` call: `revoke()` is called on the stored record before issuing new tokens.

---

## Security properties

| Property | Detail |
|---|---|
| Password hashing | BCrypt, strength 12 |
| Plaintext password storage | Never — only the BCrypt hash is persisted |
| JWT algorithm | HS256 (HMAC-SHA256) |
| Token type enforcement | `JwtAuthenticationFilter` rejects tokens whose `token_type` is not `"access"` |
| Refresh token storage | SHA-256 hash only; raw token never persisted |
| Refresh token rotation | Every `/refresh` call revokes the old token and issues a new pair |
| Revocation | Refresh tokens only; access tokens rely on short TTL |
| Session management | Stateless — `SessionCreationPolicy.STATELESS` |
| CSRF | Disabled — stateless API, no cookie-based session |
| CORS | Configurable allowed origin; restricted to `GET`, `POST`, `OPTIONS` |
| Security context | Cleared on JWT validation failure — request proceeds unauthenticated |
| Rate limiting | Not implemented in Phase 2 |

---

## Configuration variables

All variables are read from the environment. Copy `.env.example` to `.env` and fill in local values. Never commit `.env`.

| Variable | Description | Default |
|---|---|---|
| `SLICELINK_JWT_SECRET` | HMAC signing secret — minimum 32 characters, high entropy | required |
| `SLICELINK_JWT_ACCESS_TOKEN_TTL` | Access token TTL in ISO-8601 duration format | `PT15M` |
| `SLICELINK_JWT_REFRESH_TOKEN_TTL` | Refresh token TTL in ISO-8601 duration format | `P7D` |
| `SLICELINK_CORS_ALLOWED_ORIGIN` | Single allowed CORS origin | `http://localhost:5173` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | required |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username | required |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password | required |

---

## Database migrations

Schema is managed by Flyway. The application uses `spring.jpa.hibernate.ddl-auto: validate` — Hibernate validates the schema but does not modify it. Flyway must run before the application starts.

Migration history:
- `V1__create_users_and_refresh_tokens.sql` — creates the `users` and `refresh_tokens` tables with all indexes and constraints.

---

## Security assumptions

- The JWT secret must be at least 32 characters and must not be committed to source control.
- A compromised access token remains valid until its TTL expires. Phase 2 does not provide early revocation for access tokens.
- A compromised refresh token can be revoked by calling `/logout` with that token. Subsequent refresh attempts with the revoked token return `401`.
- The `users.status` field is checked on login and on every refresh — a `DISABLED` account cannot obtain new tokens.
- Logout does not invalidate other active refresh-token sessions for the same user (per-session revocation only). Full account logout requires revoking all refresh tokens, which is not implemented in Phase 2.
