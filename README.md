# EkoWatch Backend

Team 1 TeSA Cohort 6 Capstone — **Neighborhood Waste & Flood Alert System (Lagos)**.
Built as a modular monolith with **Java 25**, **Spring Boot 3.5.7**, **Maven**, **PostgreSQL**, **Flyway**, and **OpenAPI 3.0.3**.

## Architecture & Module Rules

The application is structured as a modular monolith under `com.tesa.ekowatch`:
- `auth` — Session creation (`POST /api/v1/auth/sessions`), HMAC-SHA256 Bearer token verification, Spring Security filter chain.
- `user` — Principal resolution (`ActorAccess`), current user profile and ward/street preference updates.
- `ward` — Ward and street directory lookups.
- `collection` — Collection schedules (`SCHEDULED`, `DONE`, `MISSED`, `DISPUTED`), operator assigned streets, idempotent collection completion recording.
- `photo` — Resident photo upload (`POST /api/v1/photos`), magic-byte JPEG/PNG verification, 5 MiB limit, and pixel re-encoding that strips all EXIF/GPS/XMP metadata before storage.
- `report` — Idempotent report creation (`Idempotency-Key` UUID header), ward report feed, single report view, and ward hazard layers (`blockedDrains`, `currentFloods`, `historicalFloods`).
- `confirmation` — Community report confirmation (`PUT /api/v1/reports/{reportId}/confirmations/me`) with self-confirmation prevention and flood-freshness renewal.
- `authority` — Local-authority ward analytics dashboard (`GET /api/v1/wards/{wardId}/dashboard`).
- `shared` — Uniform `application/problem+json` error handling, `X-Request-Id` correlation filter, `OperationLog`, `Page<T>`, `Location`, and opt-in `DemoSeed`.

Each module exposes cross-module contracts exclusively via its `.api` package (`ModuleBoundaryTest` enforced via ArchUnit). No Lombok is used.

## Canonical API Endpoints (`/api/v1`)

Full OpenAPI specification: [`contracts/openapi.yaml`](contracts/openapi.yaml) (mirrored at [`openapi.yaml`](openapi.yaml)).

| Method & Path | Operation ID | Role |
| --- | --- | --- |
| `POST /api/v1/auth/sessions` | `createSession` | Public |
| `GET /api/v1/users/me` | `getCurrentUser` | Authenticated |
| `PUT /api/v1/users/me/preferences` | `updateMyPreferences` | `RESIDENT` |
| `GET /api/v1/wards` | `listWards` | Authenticated |
| `GET /api/v1/wards/{wardId}/streets` | `listStreets` | Authenticated |
| `GET /api/v1/collection-schedules` | `listCollectionSchedules` | Authenticated |
| `GET /api/v1/operators/me/streets` | `listMyAssignedStreets` | `OPERATOR` |
| `PUT /api/v1/streets/{streetId}/collections/{collectionDate}` | `recordCollection` | `OPERATOR` |
| `POST /api/v1/photos` | `uploadPhoto` | `RESIDENT` |
| `GET /api/v1/photos/{photoId}` | `getPhoto` | Authenticated |
| `POST /api/v1/reports` | `createReport` | `RESIDENT` |
| `GET /api/v1/reports` | `listReports` | Authenticated |
| `GET /api/v1/reports/{reportId}` | `getReport` | Authenticated |
| `PUT /api/v1/reports/{reportId}/confirmations/me` | `confirmReport` | `RESIDENT` |
| `GET /api/v1/wards/{wardId}/hazards` | `listWardHazards` | Authenticated |
| `GET /api/v1/wards/{wardId}/dashboard` | `getWardDashboard` | `AUTHORITY` |

## Run Locally

1. Install **JDK 25** and **Maven 3.9+**.
2. Start a PostgreSQL instance and configure environment variables from `.env.example` (never commit secrets).
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```
4. To enable the fictional local demo seed (`2` wards, `6` streets, `8` fictional users across `RESIDENT`, `OPERATOR`, `AUTHORITY`, `12` schedules, `8` reports):
   ```bash
   SPRING_PROFILES_ACTIVE=local-demo \
   EKOWATCH_DEMO_ENABLED=true \
   EKOWATCH_DEMO_PASSWORD=DemoPass123! \
   mvn spring-boot:run
   ```
5. Run the full verification suite (unit tests, ArchUnit boundary checks, and Testcontainers PostgreSQL integration tests):
   ```bash
   mvn -B verify
   ```

## Team Ownership

- **Adedayo (Engineer A — Data & Rules)**: Schema migrations `V1`–`V6`, domain entities, transactional data services, concurrency/idempotency rules, demo seed, and query-plan verification.
- **Esther (Engineer B — API & Access)**: OpenAPI 3.0.3 contract, `/api/v1` controllers, session & Bearer authentication, role guards, photo upload & EXIF/GPS/XMP sanitisation, `application/problem+json` error handling, and CI pipeline.
