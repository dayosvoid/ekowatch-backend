# Steps 2-3 verification repair - 31 October 2026

The subsequent 27-test/10-error run failed first during Flyway's JDBC connection: SocketTimeoutException while waiting for the SSL negotiation response (ConnectionFactoryImpl.enableSSL), SQLSTATE 08001. Later context failure-threshold messages were consequences. The original failure excerpt is preserved in engineer-a-verification.md; earlier success below remains historical evidence.

Only test transport changed: PostgresRulesTest, ResidentReportApiTest and ReportMigrationTest explicitly use sslmode=disable on their disposable, non-TLS PostgreSQL containers. Production connection/TLS settings, application code, V1/V2, constraints, canonical API contracts, seeds and all assertions are unchanged. No timeout or failure-threshold increase, skip, Step 4 work or new migration/backfill. No commit/push/deployment; previous working-tree changes remain preserved.

Requested order verified: mvn -B '-Dtest=PostgresRulesTest' test passed all 10 tests (01:43 min, finished 10:48:59+01:00), then mvn -B verify passed all 27 tests, zero failures/errors/skips, and packaging/repackaging (03:39 min, finished 10:53:00+01:00). Both used disposable PostgreSQL 17.11 with Flyway and Hibernate validation. git diff --check passed. Esther's Step 3 integration handoff remains unchanged; this setting applies only to isolated test databases.

---

# Step 3 handoff - resident setup and private report APIs

Verification: mvn -B verify passed all 27 tests (0 failures/errors/skips) and packaged successfully at 2026-10-31T10:02:21+01:00. git diff --check passed. Earlier test-import failure and complete commands/output are preserved in engineer-a-verification.md.

The current exact methods, DTO fields, enum values and defaults are in the Step 3 section of data-contract.md. All actorId arguments are private verified-principal inputs, never client JSON. Wire WardApi.listWards/listStreets, UserApi.getCurrentUser/updateMyPreferences, ReportApi.createReport/getReport/listReports and PhotoApi.getPhoto. No HTTP paths/controllers/OpenAPI were added. create/get/list aliases are gone and every caller is updated. The pre-existing authorityReports guard and its assertions remain until Step 6 replaces that read; do not use it as the canonical MVP target. No authority dashboard queries or Step 4 work were implemented.

ReportView is the full canonical private projection: no reporter identity, lifecycle status, updatedAt or submitted coordinates. mapLocation uses public street coordinates only; note is trimmed and limited to 1000 Unicode characters. Creation derives ward/reporter/time from stored street, trusted actor and injected UTC Clock. Optional photo must be owned by the actor, including when already attached elsewhere. Confirmation counts and confirmedByMe query real rows. lastEvidenceAt uses stored report/confirmation times; six-hour flood status is computed on reads, with expiry inclusive for EXPIRED. Reads never renew evidence. Confirm/reconfirm changes remain Step 5.

Resident preferences require matching non-null ward/street and cannot modify another account or be updated by operators/authorities. Existing unset preferences remain null. Reports may select a street outside home preferences; the street defines the ward. Listing requires explicit wardId, with validated optional street/type/flood filters and page/size defaults 0/20 (size 1-100). The full-team plan is absent from repository/ZIP; the documented ordering choice is CONFIRMATIONS by default, then newest/UUID; NEWEST uses creation time/UUID. Esther should reconcile that choice if the missing plan is later supplied.

PhotoUrlResolver is an internal optional hook for Esther's trusted sanitised storage adapter, not an upload flow. It resolves private storage keys without exposing them. Until that adapter is wired, photoUrl is null even for a valid persisted attachment. PhotoApi.getPhoto exposes metadata to owner or as attached public evidence, with no ownerId/storageKey. No demo asset was fabricated, and no sanitisation, MIME/size upload policy or HTTP access behaviour is claimed. Esther still owns metadata stripping, storage/upload, credentials/password encoder, authentication, HTTP DTO parsing/validation/error mapping, final OpenAPI and frontend integration. Canonical enums and UUIDs must be parsed before service invocation; server principal supplies actorId separately. There is no idempotency argument until Step 7.

Migration/backfill: none in Step 3. V1 and V2 are untouched; no database reset/shared connection. Role-backed user/report entity data and stable UUIDs/history remain. The prior V2 incompatible-note preflight and later weekly/FAILED/operator-mapping hazards remain as documented. Seed SQL stays compatible and unchanged from Step 2; Step 8 expansion remains pending. PostgreSQL tests use only disposable Testcontainers. Full actual commands/results are in engineer-a-verification.md; no HTTP/live-demo success is claimed.

Step 3 files: report/api (ReportApi, ReportView, CreateReportRequest, ReportType, FloodStatus, ReportSort), Report domain enum mapping and ReportService; new user/api UserApi/CurrentUserView/Role and UserService; new ward/api WardApi/WardView/StreetView and WardService; photo/api PhotoApi/PhotoView/PhotoUrlResolver and PhotoService; shared Location/Page/TimeConfiguration; updated PostgresRulesTest and ModuleBoundaryTest (photo included), new ResidentReportApiTest; data-contract.md, this handoff and verification notes. Prior uncommitted Step 1/2 changes and user files remain preserved. No commit, push or deployment.

---

# Step 2 handoff - persistence and compatibility

Current schema is V2__report_and_location_contract.sql after unchanged V1; the Step 1 handoff below is historical baseline/target context. Step 2 adds four-type report persistence, note, photo/date/private coordinates, public street coordinates, paired resident preferences and photos with JPA mappings. Coordinates are double precision / nullable Double. Existing ReportApi and ReportView remain unchanged until Step 3; only service SQL maps description from the renamed note. New notes are trimmed and limited to 1000 Unicode characters. No public coordinate or photo response was added.

Migration implications: legacy notes incompatible with the 1-1000-character trimmed constraint fail preflight before DDL. Explicit remediation is required; no truncation or automatic rewriting. Valid old rows, UUIDs, lifecycle/history and timestamps are preserved; new optional values stay null. Weekly schedules and FAILED history are untouched, with their previously documented Step 4 hazards still pending. No schedule-date FK until Step 4. Deploy nothing from this work; test migration against an authorised isolated copy before any later real cutover. PostgreSQL transactional rollback on a failed preflight was tested; this does not mean Flyway provides automatic undo after a successful migration.

Esther: supply storage_key only from a trusted metadata-stripping/sanitising adapter. photos has required owner FK, nonblank storage key/content type and positive size, but no upload flow, MIME allowlist or maximum-size policy here. Do not infer sanitisation from the presence of a metadata row. No demo photo asset was provisioned. Step 3 will add owner validation in report creation and canonical private DTOs/preferences; do not treat the retained old DTO as the final public contract. actorId remains a private trusted-principal input, and existing role/location checks remain enforced. No controllers, auth, HTTP advice, frontend or Step 3 work was added.

Step 2 changed files: V2 migration; Report, UserAccount and Street mappings; new photo/domain/Photo.java; ReportService; demo.sql; PostgresRulesTest; new ReportMigrationTest; data-contract.md, this handoff and verification notes. pom.xml, V1, application.yml and existing interfaces are unchanged. Prior working-tree documentation and untracked user files are preserved. Actual verification results are recorded in engineer-a-verification.md.

---

# Engineer A handoff — canonical target and V1 baseline

Step 1, 31 October 2026. The single target is [data-contract.md](data-contract.md), transcribed from [build.md](../build.md). It supersedes all previous contract proposals in this handoff. No coordination message has been sent. No controllers, HTTP paths, OpenAPI, authentication, application changes or DDL were introduced in this step.

## Current-versus-target migration

Current: V1 only, eight JPA-mapped tables, JdbcTemplate services, PostgreSQL and Hibernate validation. Reports are BLOCKED_DRAIN/description with private identity currently exposed in ReportView and stored lifecycle status; confirmations reject repeats; schedules are weekly; pickups store COMPLETED/FAILED attempts. The dashboard module is empty. These are compatibility facts, not the desired API.

Target: reports.note and four types, private photo/submitted coordinates, street-only public location, canonical ReportView without reporter identity/lifecycle status, paired resident preferences, report_confirmations/ReportConfirmation, dated collection_schedules, collections/CollectionRecord, authority and photo APIs. Confirmation and completion repeats become safe; freshness/disputes are derived. No aliases or optional correction/resolution workflow. See the contract for exact column/member maps, full response fields, existing signatures and later-step ownership.

Never edit V1 or an applied migration. Next migration is V2__report_and_location_contract.sql if still unused, in Step 2 only. Preserve existing records, stable UUIDs, lifecycle history and timestamps. Step 4 retains weekly schedules and all pickup attempts in explicitly named legacy tables; only COMPLETED attempts backfill into collections with original IDs/times. No legacy JPA queries remain after that switch.

Migration gates: inspect notes longer than 1000 characters (V1 allows 2000), FAILED history, weekly schedule cutover, historical operator validity and wards with zero/multiple possible operators. Static demo inspection finds short notes, no pickups and one operator; this says nothing about a populated real database. Ambiguous data requires an explicit mapping and must not be guessed, truncated or discarded. Never choose the first operator UUID. Define a Lagos cutover boundary and 30-day expansion horizon; create historical occurrences only as prerequisites for actual completions. Fail preflight on unresolved mappings/conflicts. Detailed prerequisite checks and empty/populated disposable PostgreSQL migration assertions are in data-contract.md. No shared/live database was inspected or reset. Flyway Community does not automatically undo migrations; later migration work requires a reviewed recovery plan.

## Esther's integration target

Wire interfaces through api packages only. Internal actorId values are private inputs from the trusted principal, never client JSON or arbitrary headers. Services retain role and ward enforcement. UUIDs become strings at HTTP boundaries, instants use UTC, calendar dates use Africa/Lagos; keep DTOs separate from entities.

Canonical methods: WardApi.listWards/listStreets; UserApi.getCurrentUser/updateMyPreferences; ReportApi.createReport/getReport/listReports/listWardHazards; ConfirmationApi.confirmReport; CollectionApi.listCollectionSchedules/listMyAssignedStreets/recordCollection; AuthorityApi.getWardDashboard; PhotoApi.getPhoto. Internal report.api locking collaboration is not an endpoint. These target APIs are not all implemented today; data-contract.md identifies the current exact signatures. Do not wire the old authorityReports/description/status/reporterId response as the MVP target.

Canonical views: CreateReportRequest, ReportView, CollectionScheduleView, CollectionView, WardHazardsView, WardDashboardView, OperatorPerformanceView, CurrentUserView, WardView, StreetView, PhotoView, Page<T>. build.md leaves some auxiliary view fields and target argument ordering unspecified; those gaps are explicitly marked in the contract, not replaced by competing proposals.

Report creation derives ward/reporter, accepts note/street/type and optional photo/location/collectionDate. Step 7 adds a separate typed UUID idempotency argument mapped from Idempotency-Key, with atomic 24-hour snapshot replay. Photo upload/storage orchestration and metadata stripping belong to Esther: persist only a trusted sanitised reference, enforce ownership, never fabricate URLs or claim raw assets are sanitised. Public map coordinates come only from streets. Never expose password hashes, reporter identity or submitted coordinates.

Esther owns authentication, password-encoder integration, controllers, validation at HTTP boundaries, sanitised exception mapping, final OpenAPI, frontend integration and deployment. Canonical RuleViolation codes are listed in data-contract.md; old implementation error codes are not final HTTP agreement. AUTHORITY protects performance metrics. HTTP wrong-role/security tests remain hers; no endpoint or login success has been demonstrated.

## Demo and operational limits

DemoSeed still requires local-demo plus ekowatch.demo.enabled=true, excludes prod/production, and uses transactional insert-only fixed-UUID SQL. Existing fixture: one fictional ward/street, five users (three residents, one operator, one authority), seven weekly days, three reports with 0/1/2 confirmations. Passwords are !DEMO_LOGIN_DISABLED, not working login hashes. Esther must deliberately provision encoder-compatible demo credentials; do not enable demo seeding in deployments. Step 8 expansion remains pending; reruns must retain user actions and original evidence times. No fake sanitised photo asset exists.

RequestIdFilter validates/echoes correlation IDs, sets/restores MDC, and runs at highest precedence; Esther must verify correlation survives final security errors. Do not log notes, identities, coordinates or secrets. Current application.yml requires DB_URL/DB_USERNAME/DB_PASSWORD; historical datasource-default claims do not describe this snapshot. No environment values were read or printed.

## Actual Step 1 verification

Latest `mvn -B verify` with approved cache/Docker access: BUILD SUCCESS, exit 0, total time 02:01 min, finished 2026-10-31T09:27:05+01:00. Java 25.0.4.1, Maven 3.9.16 and Spring Boot 3.5.7 unchanged. ModuleBoundaryTest 1, PostgresRulesTest 8 and RequestIdFilterTest 2 all passed: 11 tests, 0 failures/errors/skips. Flyway applied V1 to an empty disposable postgres:17-alpine database (PostgreSQL 17.11); Hibernate initialized with ddl-auto=validate. Rules/races, seed repeatability, packaging and repackaging succeeded. Existing classes were up to date; this was not a clean compilation.

Earlier cache-access and unavailable-Docker failures remain recorded in [engineer-a-verification.md](engineer-a-verification.md), alongside actual successful output. No shared/live database was used. This confirms V1 only, not future migration/backfill paths or HTTP/authentication behaviour. Migration prerequisites and Esther's integration responsibilities above remain pending.

Changed in Step 1: docs/data-contract.md, this handoff and docs/engineer-a-verification.md only. No tests/seeds need compatibility edits because runtime contracts are unchanged. Existing untracked build.md and ekobridge-project.zip are preserved. No commit, push or deployment. Step 2 has not begun.
