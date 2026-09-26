# TicketBox — Repository Guide

## Project overview

TicketBox is a concert management and ticket sales application for audiences, organizers, and administrators. It handles high-demand reservations, per-user ticket limits, duplicate payment callbacks, QR ticket issuance, and notifications.

## Tech stack

- Backend: Java 21, Spring Boot 4.0.6, Maven; Spring Web MVC, Spring Data JPA, Spring Security/OAuth2, Validation, AMQP, Mail, Flyway; Lombok, JJWT 0.11.5, MinIO SDK 8.5.17.
- Frontend: TypeScript 5, React 19, React Router 7, Vite 6, Tailwind CSS 4.3; `lucide-react`, `motion`, `qrcode`.
- Local data and infrastructure: PostgreSQL 15 (primary database), Redis 7 (cache), RabbitMQ 3.12 (messaging), MinIO (file storage); H2 for backend tests.

## Directory structure

```text
backend/
  pom.xml                         Maven dependencies and build
  src/main/java/com/ticketbox/api/
    module/                       Business modules by domain
    infrastructure/               Security, configuration, exceptions, API responses
    worker/                       Catalog cache warm-up listener
  src/main/resources/
    application*.yml              Shared and api/worker profile configuration
    db/migration/                 Flyway schema and seed data
  src/test/                       Java tests
frontend/
  src/routes/                     Audience, auth, admin, organizer, checker, payment pages
  src/services/                   Domain-specific API calls
  src/lib/                        HTTP client, auth session, helpers
  src/components/, src/styles/    Shared UI and CSS
  src/main.tsx                    Router and entry point
blueprint/                        Architecture, feature, and API specifications
postman/                          API collections and environments
docker-compose.yml                Local supporting services
```

`backend/.../module/` contains `auth`, `catalog`, `order`, `payment`, `ticket`, `notification`, `audit`, `guestlist`, `artistbio`, and `shared`. Inventory logic currently lives in `order` and `catalog`; there is no separate `inventory` package. Modules use applicable packages such as `controllers`, `services`, `repositories`, `domain/entities`, and `domain/dtos`; some also have `config`, `producers`, `consumers`, or `schedulers`.

## Setup, run, and checks

Requirements: JDK 21, Node.js/npm, Docker Compose. Run these commands from the repository root unless a command changes directory.

```bash
touch backend/.env
docker compose up -d postgres redis rabbitmq minio
cd backend
./mvnw spring-boot:run
```

The backend defaults to the `api` profile on port `8080`; Flyway runs migrations automatically. Maven Wrapper downloads dependencies on first use. In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

The frontend runs at `http://localhost:3001`; Vite proxies API requests to `localhost:8080`. Check with `cd backend && ./mvnw test` and `cd frontend && npm run build` (the frontend has no test script).

- `application.yml` loads `backend/.env`; do not commit secrets. Local defaults for PostgreSQL, Redis, RabbitMQ, and MinIO are in `application.yml` and `docker-compose.yml`. Override them with `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_*`, `RABBITMQ_*`, and `MINIO_*` as needed.
- Set `JWT_ACCESS_TOKEN`, `JWT_REFRESH_TOKEN`, and `TICKET_QR_SECRET` to environment-specific secrets outside local development. Google OAuth2 requires `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`; email requires `MAIL_*`; payment providers require their respective `VNPAY_*` or `MOMO_*` values. Set frontend `VITE_API_BASE_URL` only when the API is not reached through the same origin or Vite proxy.
- See `backend/src/main/resources/application.yml` for all settings and defaults. Backend tests use `backend/src/test/resources/application.yml` with H2.

## Architecture Notes (non-obvious decisions)
- DB is the single source of truth for inventory. Redis is a read-optimization layer and is NEVER the gate for a purchase decision this prevents overselling if cache and DB drift.


## Coding conventions

- Java: use lowercase domain package names, `PascalCase` classes/interfaces, and `camelCase` methods/fields. Follow `Controller -> Service -> Repository`; keep entities separate from DTOs. Use `@Transactional` for business writes and Jakarta Validation for requests. Return `ApiResponse`; report coded errors through `AppException` and the shared handler.
- Change the database schema through Flyway migrations named `V<n>__<description>.sql`; Hibernate uses `ddl-auto: validate`. Access Redis and RabbitMQ through the existing configuration and service/producer/consumer patterns.
- TypeScript/React: keep `strict` enabled; use `PascalCase` for components and component files, `camelCase` for functions/variables, and `kebab-case.service.ts` for service files. Place pages in `routes/<group>/`, HTTP calls in `services/` or `lib/api-client.ts`, and reusable UI in `components/`.
- When `blueprint/` differs from the implementation, inspect the running code and migrations before changing behavior. Add backend tests for significant business logic changes.


## Post-implementation

- After completing a non-trivial coding task, invoke the implementation-explain skill to explain the implemented changes.