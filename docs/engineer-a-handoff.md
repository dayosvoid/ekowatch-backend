# Merged integration repair - canonical note contract

Verification: requested PostgresRulesTest passed 10 tests; mvn -B clean verify passed all 41 tests (0 failures/errors/skips), clean compilation and packaging at 2026-10-31T11:53:11+01:00. Full file inventory and actual command evidence are in engineer-a-verification.md.

The current data contract is docs/data-contract.md, restored from dayo/data-rules (6b9c51bd4db453d927f028e45c6eac907ca76f6f). Earlier integration proposals below are historical where they differ. The merged dev/branch had reverted the report entity/service/DTOs/seed to V1 description, omitted V2 and the completed Steps 2-3 dependencies/tests/docs, and left V2 in generated target/classes. That mixed resource/code state explains Hibernate's missing-description error.

Restored the existing Steps 2-3 data code and tests from that reference: Report.note explicitly maps @Column(name="note"), ReportService uses note and the canonical createReport/getReport/listReports methods, and ReportView has its full private canonical fields. Supporting photo/user/ward APIs/services, Location/Page/Clock and JPA preferences/coordinates mappings are restored, not newly designed. Old create/get/list service aliases are absent. The guarded transitional authorityReports read remains as documented until Step 6.

V1 is untouched from the incoming dev branch. V2__report_and_location_contract.sql is restored byte-for-byte from dayo/data-rules (Git blob 664e384729264dd322ea38440467bedc1d65f6bd, SHA256 8ECDC97077E460D007B83A433B241070F831E8630049F2E40985DB93B20878C8). No applied migration was rewritten, no duplicate description column or Step 4 migration added, and Hibernate ddl-auto=validate remains enabled. Description is intentional only in V1, V2 preflight/rename, populated-V1 historical fixtures, and a negative JSON assertion. Compatible data/UUID/history preservation and incompatible-note preflight continue to be tested on disposable PostgreSQL. Existing shared databases were not inspected or reset.

Esther's auth/security classes, ActorAccess.id(Authentication), DatabaseActorAccess, confirmation/collection controllers and auth tests are preserved. ReportController keeps /api/reports and /api/reports/{reportId}, and resolves the trusted principal through actors.id(authentication). It now takes report.api.CreateReportRequest (reportType, streetId, note, optional photoId/location/collectionDate), calls createReport/getReport, and lists using required wardId with optional street/type/flood filters, page/size and ReportSort. The response is Page<ReportView>. There is no actorId/reporterId/wardId in the create DTO and no description alias. Existing HTTP advice now recognizes VALIDATION_FAILED as 400 and RESOURCE_NOT_FOUND as 404 while retaining its other handlers.

ReportControllerTest retains principal/delegation coverage, strengthens blank-note refusal and adds actual MockMvc binding/serialization/error/default-pagination checks, including ignoring a client actorId in favour of the principal and excluding private response fields. Existing PostgreSQL assertions are restored without weakening rules. The original pom.xml conflict-resolution edit belongs to the user and remains untouched; no dependency was added by this repair.

No authentication/session, upload/storage, dashboard, dated schedules, collection completion or Step 4 feature was added. Esther's photo URL/sanitisation adapter and other handoff items in data-contract.md still apply. Requested verification results and any encountered failures are recorded in engineer-a-verification.md. No commit/push/deployment.

---

# Engineer A handoff — Monday MVP

Working branch: `dayo/data-rules`. Integration target: `dev/branch`.
Contracts below are proposals for Esther and Muizz to agree; no coordination message has been sent.
No controllers or authentication have been implemented in this change.

## Schema and migration

`V1__mvp_foundation.sql` creates:
- users: UUID ID, normalized unique email, password hash, display name, checked RESIDENT/OPERATOR/AUTHORITY role, creation timestamp.
- wards: UUID ID, unique name, creation timestamp.
- streets: ward FK, unique ward/name and ID/ward pair.
- operator_wards: user/ward FKs and composite primary key prevents repeated assignment.
- collection_schedules: street FK, ISO weekday 1–7, active flag, unique street/weekday.
- pickups: street/user FKs, date, checked COMPLETED/FAILED status; named `uq_pickup_street_date` enforces one attempt per street/date.
- reports: reporter/ward FKs, composite street/ward FK prevents mismatched locations, BLOCKED_DRAIN type, nonblank description up to 2000 characters, OPEN/RESOLVED/REJECTED status, creation/update timestamps.
- confirmations: report/user FKs; named `uq_confirmation_report_user` enforces one resident confirmation per report.

All timestamps are PostgreSQL timestamptz. UUIDs originate in application/demo code.
FK indexes support street, reporter, ward, user and recorder lookup. Unique indexes cover report counts, schedules and pickups.
A report update trigger refreshes updated_at. No cascading deletion is provided.
Flyway owns DDL; Hibernate `ddl-auto: validate` checks mapped tables at startup. All eight tables have JPA mappings, but transactional services use JdbcTemplate for explicit PostgreSQL insert-on-conflict behavior and aggregate projections. There is no mutable cached confirmation counter.

Roles on operator assignments and reporter/confirmation/pickup rows are enforced by services, not cross-table SQL role constraints.
Direct database writes must remain restricted to trusted code and migrations.

## Public module APIs

Inject interfaces, never another module's service/domain/repository:
- `report.api.ReportApi.create(actorId, wardId, streetId, description)` → ReportView.
- `ReportApi.get(actorId, reportId)` → ReportView.
- `ReportApi.list(actorId, limit, offset)` → List<ReportView>.
- `ReportApi.authorityReports(actorId, limit, offset)` → List<ReportView>, authority only.
- `ReportApi.requireConfirmable(reportId, actorId)` is an inter-module eligibility operation used within confirmation's transaction; not an endpoint.
- `confirmation.api.ConfirmationApi.confirm(actorId, reportId)` → void after transactional commit.
- `collection.api.CollectionApi.recordPickup(actorId, streetId, date, status)` → void.
- `CollectionApi.missed(authorityId, from, through)` → List<MissedPickup>.
- `user.api.ActorAccess` validates stored actor existence/role.

Actors must come from Esther's verified principal. UUID parameters are trusted internal inputs, not authentication credentials. Never bind actorId/reporterId/userId from request JSON or an arbitrary header.
Read endpoints currently allow any stored authenticated user; the authority-specific operation checks AUTHORITY.
Only a RESIDENT can report/confirm; self-confirmation and non-OPEN reports are rejected.
No ward-residency restriction exists in the agreed minimum schema.

Confirmation uses READ COMMITTED, shared report row locking and INSERT ON CONFLICT against the named unique constraint. Different residents can hold compatible shared locks. Concurrent repeated resident inserts yield one committed success and one DUPLICATE_CONFIRMATION. No unique-violation exception is swallowed inside an aborted transaction.
Counts use a SQL count of confirmation rows and exclude other transactions' uncommitted inserts. Fetch a fresh ReportView after confirm returns. A count may increase immediately afterward as another resident commits.

## Proposed HTTP contract (Esther owns implementation)

| Endpoint | Authorization | Request | Success |
| --- | --- | --- | --- |
| POST /reports | RESIDENT | {wardId, streetId, reportType: "BLOCKED_DRAIN", description} | 201 ReportView + Location |
| GET /reports?limit=20&offset=0 | authenticated | limit 1–100, offset >=0 | 200 ReportView[] |
| POST /reports/{reportId}/confirmations | RESIDENT | empty body | 201; optionally fresh ReportView |
| GET /reports/{reportId} | authenticated | UUID path | 200 ReportView |
| GET /authority/reports?limit=20&offset=0 | AUTHORITY | same paging | 200 ReportView[] |

ReportView JSON: `{id, reporterId, wardId, streetId, reportType, description, status, createdAt, updatedAt, confirmationCount}`.
Use UUID strings, ISO-8601 UTC timestamps and numeric counts. Reporter IDs are included; no email/hash is exposed.
Reject unsupported reportType at the controller boundary; create currently supports only blocked drains.
Proposed errors: `{code, message, requestId}`. Never expose SQL or credentials.

| RuleViolation code | Proposed HTTP |
| --- | --- |
| FORBIDDEN | 403 |
| REPORT_NOT_FOUND | 404 |
| DUPLICATE_CONFIRMATION, DUPLICATE_PICKUP | 409 |
| REPORT_NOT_ELIGIBLE, SELF_CONFIRMATION | 409 |
| INVALID_REPORT, INVALID_LOCATION, INVALID_PAGE, INVALID_PICKUP, INVALID_DATE_RANGE | 400 |

Authentication failures → 401 at Esther's security layer. Malformed UUID/JSON → 400.
Unexpected integrity failures → sanitized 500; do not map every integrity exception to duplicate.
Esther must add controllers, Bean Validation, Spring Security, exception advice, authentication tests and final OpenAPI agreement with Muizz.
RequestIdFilter runs at highest servlet precedence so security-generated errors retain the correlation header; verify this in the final security integration.

## Collection rules and limits

Assigned operators may record COMPLETED or FAILED pickups up to today's Lagos date.
The unique street/date rule covers both statuses: a failed record cannot be retried through this API. Agree a correction workflow separately.
Missed pickups are derived, never stored: active scheduled weekday and no COMPLETED pickup on a fully elapsed date.
Authority query allows at most 367 inclusive dates (difference <=366), excludes today/future dates, and uses Africa/Lagos.
Schedules have no effective-date history; changing active/weekday changes historical derived results. No schedule-management API is included.

## Local configuration and opt-in seed

Environment: DB_URL (default jdbc:postgresql://localhost:5434/ekowatch), DB_USERNAME (default ekowatch), DB_PASSWORD, PORT (default 8081).
Preserved the user's existing datasource defaults; set DB_PASSWORD explicitly outside local use.
No local database or unrelated project database was used by verification.

PowerShell local demo (only an isolated local EkoWatch database):
```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5434/ekowatch'
$env:DB_USERNAME = 'ekowatch'
$env:DB_PASSWORD = '<your local password>'
$env:SPRING_PROFILES_ACTIVE = 'local-demo'
$env:EKOWATCH_DEMO_ENABLED = 'true'
mvn spring-boot:run
```

Both local-demo profile and explicit demo flag are required. prod/production profiles block the runner.
Do not enable local-demo or demo flag in deployment configuration.
Seed is transactional and insert-only with fixed UUIDs and ON CONFLICT DO NOTHING. It does not overwrite existing rows; reserved demo IDs must not be reused.
Fictional demo accounts demo1–demo3 are residents, demo4 operator, demo5 authority, all @example.invalid.
Demo passwords are deliberately disabled (`!DEMO_LOGIN_DISABLED`), not working hashes. Esther must provision local credentials with her configured password encoder to demonstrate login; never reuse production credentials.
Seed supplies a fictional ward/street, all seven scheduled days, reports with 0/1/2 confirmations and no pickup yesterday, ensuring a missed-pickup example.
For a clean showcase use a fresh local database; repeat seeding preserves subsequent demo interactions.

## Logging

RequestIdFilter validates X-Request-Id as 1–64 ASCII letters/digits/dot/underscore/hyphen, otherwise creates a UUID.
Echoes response header and sets SLF4J MDC requestId, restoring previous MDC in finally even on failure.
Existing logging is preserved with requestId added to the level pattern. No body, password, token or secret is logged.
Async execution requires explicit MDC propagation if introduced later.

## Verification

```powershell
mvn -B verify
mvn -B '-Dtest=PostgresRulesTest' test
```

Docker is required. Tests fail rather than silently skip if Docker is unavailable.
PostgresRulesTest starts a disposable postgres:17-alpine container with dynamic datasource properties; never points at ekowatch-db or production.
A two-thread start barrier drives races; successful service returns mean transactions committed. Futures have timeouts.
Constraint demonstration removes/restores uniqueness on a temporary LIKE-confirmations table inside disposable PostgreSQL, retaining the primary key; no shared/domain constraint is removed.
Tests assert SQLSTATE 23505 for restored constraint and direct duplicate inserts. See docs/engineer-a-verification.md for actual run evidence.

## Changed files

Created: V1 migration; db/demo/demo.sql; public ReportApi/ReportView, ConfirmationApi, CollectionApi, ActorAccess; their services; eight entity mappings; RuleViolation, RequestIdFilter, DemoSeed; PostgresRulesTest and RequestIdFilterTest; handoff and verification documents.
Modified: ModuleBoundaryTest replaces placeholder with enforced internal-package boundaries; application.yml adds MDC logging while preserving existing datasource changes; .env.example aligns local database port/user; README/architecture update scaffold status.
pom.xml already had uncommitted user changes and all required dependencies; no dependency was added.

## Monday blockers and integration

Data/rules are usable through Spring interfaces. HTTP, login, authorization wiring, DTO validation/error advice, final frontend contracts and deployment remain Esther's work.
No endpoint/auth behavior has been tested because controllers/authentication do not yet exist.
Agree self-confirmation prohibition, OPEN-only eligibility, authenticated read visibility, response envelopes/paging, and pickup correction semantics before integration.
No commit, push, merge, production connection or shared-branch modification was performed.

After review and explicit approval only:
```powershell
git switch dayo/data-rules
git diff --check
git add .env.example README.md docs/architecture.md docs/engineer-a-handoff.md docs/engineer-a-verification.md src/main/java src/main/resources src/test/java
git diff --cached
git commit -m "Implement EkoWatch MVP schema and transactional data rules"
git push -u origin dayo/data-rules
```

pom.xml is deliberately omitted from staging because its existing edits belong to the user; review and stage it separately if desired.
Do not merge dev/branch without permission.
