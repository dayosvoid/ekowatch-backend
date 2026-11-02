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

## Demonstration Credentials (MVP Evaluation)

By security design, seeded accounts in `demo.sql` are initialized with an unusable password marker (`!DEMO_LOGIN_DISABLED`). For local and staging demonstration, BCrypt credentials are deliberately provisioned for the **five approved demo accounts** (see [`docs/demo-provisioning.md`](docs/demo-provisioning.md)):

| Role | Email | Seeded Persona / Location | Use Case |
| --- | --- | --- | --- |
| `RESIDENT` | `demo1@example.invalid` | Ward 1, Street 1 (*Fictional Palm Street*) | Creates waste & flood reports, uploads sanitised photos |
| `RESIDENT` | `demo2@example.invalid` | Ward 1, Street 2 (*Fictional Bamboo Street*) | Confirms existing community reports |
| `OPERATOR` | `demo4@example.invalid` | Assigned to Ward 1 (Streets 1–4) | Views assigned streets, records pickup completion |
| `OPERATOR` | `demo8@example.invalid` | Deliberately unassigned | Exercises the `403 WARD_ASSIGNMENT_REQUIRED` guard |
| `AUTHORITY` | `demo5@example.invalid` | Authority oversight | Accesses Ward 1 KPIs, hotspots, and triage dashboard |

*Accounts `demo3@example.invalid`, `demo6@example.invalid`, and `demo7@example.invalid` remain locked at `!DEMO_LOGIN_DISABLED`.*

## Known Limitations & Simulated Features

- **In-App Camera vs Gallery Capture:** Browsers and web clients cannot reliably guarantee direct camera capture versus camera gallery file selection. The API accepts standard multipart file uploads (`POST /api/v1/photos`), completely strips all EXIF/GPS/XMP metadata via pixel re-encoding, and attaches an authoritative server timestamp upon ingestion.
- **Fictional Data Notice:** All names, telephone numbers, and community activity in the demo seed are completely fictional. Wards and streets use real Lagos place names for geographical context, as allowed by the project fairness guidelines.
- **Out of Scope (Phase 2):** Route dispatch optimization, resident chat/push notifications, SMS/USSD alerts, and live identity verification.

## Deployment & Free-Tier Verification

- **Backend Service:** Spring Boot application deployed via container/JVM hosting on a free tier.
- **Database:** Managed PostgreSQL instance on Neon free tier (checked 31 October 2026; within free storage limits and no project expiration during evaluation).
- **Frontend Interface:** Deployed on Qocent hosting (`https://eko-watch--cflk38sh3.qocent.app`), with full CORS support enabled.
- **CI / CD Pipeline:** GitHub Actions workflow ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) running `mvn -B verify` (107 unit, integration, and architecture tests) on every push and PR.

## Decision Records & AI Log

- **Architecture Decision Records:** See [`docs/decisions.md`](docs/decisions.md) and [`docs/adr/`](docs/adr/) for ADR 001 (Confirmation & Flood Freshness) and ADR 002 (Derived Disputed Pickups).
- **AI Tooling & Verification Log:** See [`docs/ai-log.md`](docs/ai-log.md) for tools used, prompts, accepted code, and three concrete cases where AI output was checked, found incorrect, and corrected.

## Team Ownership

- **Adedayo (Engineer A — Data & Rules)**: Schema migrations `V1`–`V6`, domain entities, transactional data services, concurrency/idempotency rules, demo seed, and query-plan verification.
- **Esther (Engineer B — API & Access)**: OpenAPI 3.0.3 contract, `/api/v1` controllers, session & Bearer authentication, role guards, photo upload & EXIF/GPS/XMP sanitisation, `application/problem+json` error handling, and CI pipeline.
