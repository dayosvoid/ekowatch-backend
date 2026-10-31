# EkoWatch database build steps

Owner: Adedayo Aboderin · Engineer A · 31 October 2026

## Purpose and sources

Complete the data and business-rule work in the supplied EkoWatch backend, using one naming convention. Run the prompts in order, one step at a time. Each prompt makes a bounded implementation change and verifies it; it must not implement later steps.

Sources: `ekobridge-project.zip`, archive commit `23b649534572d659e01586c24e21a5b26483f505`; the previously supplied EkoWatch `build.docx` and `build-plan.md`; capstone brief pages 14–16. No `plan.md` or `intent.md` is present in the ZIP. The previous full-team plan is the baseline, not evidence that the current code implements it.

This document was prepared by static inspection. The application and tests were not executed for this review. Existing verification notes describe earlier runs and do not establish the status of the uploaded snapshot.

## Your scope

Implement schema migrations, entity mappings, transactional data services, module API interfaces and views, business-rule tests, seed data and data-layer logging. Supply contract definitions for Esther to expose through controllers. Esther owns authentication, password-encoder integration, controllers, HTTP exception mapping, final OpenAPI and deployment. Muizz owns frontend implementation. Do not add those tiers here.

No alerts, reminders, hotspot analysis, forecasts, SMS, payments, schedule-management interface, moderation or resolution workflow. No full load-test project is required for this side project. Photo metadata stripping is required, but the upload/storage adapter is Esther's integration responsibility; your part supplies persistence, ownership rules and a clear handoff. Do not add Cloudinary to EkoWatch merely because FoodBridge uses it.

## What the uploaded code contains

| Area | Observed implementation | Work remaining |
|---|---|---|
| Schema | `V1__mvp_foundation.sql`; eight tables; Flyway and JPA validation | Add forward migrations; reconcile naming and missing fields |
| Reports | BLOCKED_DRAIN only; `description`; OPEN/RESOLVED/REJECTED; public view includes reporterId | Four types, canonical note/view fields, private location/photo reference, freshness |
| Confirmations | `confirmations`; unique report/user; shared row lock; duplicate returns an error | Canonical names, repeat-safe result and expired-flood reconfirmation |
| Collections | Weekly schedules; `pickups` COMPLETED/FAILED; one attempt per date | Dated schedule occurrences, canonical completion model and derived disputes |
| Dashboard | Empty `dashboard` package | Authority projections and operator metrics |
| Seed | One ward, one street, five disabled-login users, three blocked-drain reports | Eight streets, thirty-day coverage, all required evidence cases |
| Tests | Postgres 17 Testcontainers races, rules, seed, request-ID and ArchUnit tests | Update contracts and add missing-rule/migration evidence |
| Delivery | Java 25, Spring Boot 3.5.7, Maven CI | Keep stack; no Maven wrapper exists in ZIP; use `mvn -B verify` |

## Rules for every prompt

Paste this common instruction block before each numbered prompt. Keep this file in the repository root as `build.md`.

```text
Work in the EkoWatch backend repository. Read build.md and the relevant existing files before editing. Implement only the requested step. Preserve Java 25, Spring Boot 3.5.7, PostgreSQL, Flyway, JdbcTemplate services, JPA validation and module boundaries. Do not add frontend, controllers, login, refresh tokens, deployment, optional product features or unrelated refactors. Never edit V1 or any applied migration; create the next unused forward migration. Keep existing records and stable UUIDs. Do not reset a database, use a shared/live database, print .env values, commit, push or deploy.

Use only the canonical names in build.md. Internal actorId parameters come from a trusted principal through Esther's layer, never client JSON. Services still enforce role and ward rules. Keep DTOs separate from JPA entities. Do not import another module's domain/service/repository; use its api package. For each step, update affected existing tests and seeds enough to keep them compatible; the final seed expansion is Step 8. Never weaken a valid assertion merely to make tests pass.

Run meaningful targeted tests on disposable PostgreSQL Testcontainers. Use mvn, not a nonexistent Maven wrapper. If Docker/JDK/dependencies are unavailable, report the exact blocker and which checks did not run; never claim success. Finish with changed files, behaviour, actual commands/results, migration/backfill implications and any handoff to Esther. Preserve unrelated working-tree changes. Do not proceed to the next step.
```

## One naming convention

Case conversion is deliberate: SQL snake_case, Java/JSON/TypeScript lowerCamelCase, types PascalCase. There are no alternative names.

| Existing name | Canonical target |
|---|---|
| `pickups` / `Pickup` | `collections` / `CollectionRecord` |
| `pickup_date` / `date` in collection views | `collection_date` / `collectionDate` |
| `recorded_by` | `completed_by` |
| pickup `created_at` | `completed_at` |
| `confirmations` / `Confirmation` | `report_confirmations` / `ReportConfirmation` |
| confirmation `user_id` / `created_at` | `resident_id` / `confirmed_at` |
| reports `description` | `note` |
| ReportView `id` | `reportId` |
| ReportApi `create` / `get` / `list` | `createReport` / `getReport` / `listReports` |
| ConfirmationApi `confirm` | `confirmReport` |
| CollectionApi `recordPickup` | `recordCollection` |
| empty `dashboard` module | `authority` module |

Unique constraints: `uq_report_confirmation_resident(report_id,resident_id)`, `uq_collection_street_date(street_id,collection_date)`, `uq_schedule_street_date(street_id,collection_date)`.

Enums: Role = RESIDENT, OPERATOR, AUTHORITY. ReportType = MISSED_PICKUP, ILLEGAL_DUMPING, BLOCKED_DRAIN, FLOODED_ROAD. FloodStatus = CURRENT, EXPIRED. CollectionStatus = SCHEDULED, DONE, MISSED, DISPUTED. Do not add new values. Do not expose old report lifecycle states through the MVP contract or implement transitions for them; preserve existing stored history during migration.

Module service APIs: `WardApi.listWards`, `WardApi.listStreets`, `UserApi.getCurrentUser`, `UserApi.updateMyPreferences`, `ReportApi.createReport`, `ReportApi.getReport`, `ReportApi.listReports`, `ReportApi.listWardHazards`, `ConfirmationApi.confirmReport`, `CollectionApi.listCollectionSchedules`, `CollectionApi.listMyAssignedStreets`, `CollectionApi.recordCollection`, `AuthorityApi.getWardDashboard`, `PhotoApi.getPhoto`. Photo upload orchestration belongs to Esther; do not add an upload flow in these steps.

Use `CreateReportRequest`, `ReportView`, `CollectionScheduleView`, `CollectionView`, `WardHazardsView`, `WardDashboardView`, `OperatorPerformanceView`, `CurrentUserView`, `WardView`, `StreetView`, `PhotoView` and `Page<T>` exactly. `Page<T>` fields: items, page, size, totalElements, totalPages. All UUIDs become strings at HTTP boundaries; instants are UTC; calendar dates use Africa/Lagos.

### Data and view contract

ReportView fields: reportId, reportType, wardId, streetId, streetName, note, photoUrl, createdAt, collectionDate, confirmationCount, confirmedByMe, lastEvidenceAt, floodStatus, expiresAt, mapLocation, locationPrecision. The value of locationPrecision is STREET. photoUrl, collectionDate, floodStatus, expiresAt and mapLocation can be null. Never return reporterId, reporter name, email or private submitted coordinates. Map coordinates are street coordinates only.

CollectionScheduleView fields: scheduleId, wardId, streetId, streetName, collectionDate, collectionStatus, completedAt, missedReportCount. CollectionView fields: collectionId, streetId, collectionDate, completedAt, collectionStatus. completedAt is null when there is no completion.

WardDashboardView fields: wardId, fromDate, toDate, generatedAt, missedPickupCount, disputedPickupCount, blockedDrainCount, currentFloodCount, newestReportAt, operatorPerformance. OperatorPerformanceView fields: operatorId, operatorName, scheduledPickupCount, completedPickupCount, missedPickupCount, disputedPickupCount.

Error codes handed to Esther: VALIDATION_FAILED, UNAUTHENTICATED, FORBIDDEN, RESOURCE_NOT_FOUND, WARD_ASSIGNMENT_REQUIRED, SELF_CONFIRMATION_NOT_ALLOWED, IDEMPOTENCY_KEY_REQUIRED, IDEMPOTENCY_KEY_REUSED, PHOTO_TOO_LARGE, UNSUPPORTED_PHOTO_TYPE, INTERNAL_ERROR. This work uses service RuleViolation codes; it does not implement HTTP advice.

## Step 1 Establish the database contract and migration baseline

**Goal:** Resolve the actual compatibility work before changing schema. This is a documentation and baseline-verification step.

**Read:** V1; all existing module API interfaces, entity mappings and services; PostgresRulesTest; ModuleBoundaryTest; demo.sql; engineer-a-handoff.md; pom.xml.

**Output:** `docs/data-contract.md`, updated `docs/engineer-a-handoff.md`, baseline test result. No DDL yet.

```text
Apply the common instructions. Implement Step 1 only. Inventory the current V1 schema, service signatures, SQL, JPA mappings, tests and demo fixtures against build.md. Write docs/data-contract.md with the exact canonical table/column, Java type/member, service method and response-field mappings. Include the full ReportView and collection/dashboard views from build.md. Mark internal actor IDs private. Replace superseded contract proposals in engineer-a-handoff.md with the single target and a clearly labelled current-versus-target migration section.

Inspect legacy reports longer than the new 1000-character note limit, FAILED pickup history, weekly schedules and wards with multiple assigned operators as migration hazards. State that ambiguous data needs an explicit mapping and must not be guessed, truncated or discarded. Define prerequisite checks for the later migration. Keep the historical verification document honest. Run mvn -B verify if the environment supports it and record actual output. Do not modify application behaviour or introduce OpenAPI paths/controllers in this step.
```

**Done when:** One contract exists and baseline pass/failure is recorded without claiming an unrun test passed.

## Step 2 Migrate identity preferences and report persistence

**Goal:** Support the required four report types, private evidence and ward/street setup.

**Touch:** next migration `V2__report_and_location_contract.sql` if V2 is still unused; UserAccount, Report, Street; new `photo` module persistence; related tests and compatible seed statements.

```text
Apply the common instructions. Implement Step 2 only using the next unused migration version. Preserve V1. Rename reports.description to note and extend the report_type CHECK to the four canonical types. Add nullable photo_id, latitude, longitude and collection_date to reports; add public nullable latitude/longitude to streets. Add users.ward_id and users.street_id with a paired-null rule and a composite street/ward FK. Keep existing report street/ward integrity and stable IDs.

Create photos with id, owner_id, storage_key, content_type, size_bytes and created_at, plus required FKs/checks, and map its JPA entity. Treat photo storage_key as the trusted sanitised asset reference supplied by Esther's adapter. No upload implementation, provider SDK or image-processing flow. Validate complete latitude/longitude pairs and geographic ranges. Require collection_date only for MISSED_PICKUP and null for other report types. The schedule-date FK is added in Step 4 after dated schedules exist.

Preserve legacy report lifecycle/history columns without adding API transitions. New notes must be trimmed and 1–1000 characters. Fail preflight clearly on incompatible existing notes rather than silently truncating. Update JPA mappings, existing ReportService SQL and fixtures for renamed columns while retaining the old service interface until Step 3. Add a disposable PostgreSQL migration test from V1 with sample data and an empty-database migration test; assert IDs/data are preserved and constraints reject invalid input. Keep ddl-auto validate enabled.
```

**Done when:** Both migration paths and JPA validation succeed; all four report types are persistable without weakening location/privacy constraints.

## Step 3 Implement resident setup and the canonical report APIs

**Goal:** Deliver typed, private data contracts for Esther without implementing HTTP.

```text
Apply the common instructions. Implement Step 3 only. Add WardApi.listWards/listStreets and UserApi.getCurrentUser/updateMyPreferences using existing modules. Enforce RESIDENT preferences and ward/street matching; never return password_hash. Introduce canonical report enums, CreateReportRequest and ReportView. Rename ReportApi create/get/list to createReport/getReport/listReports and update every caller and test in this repository in the same change; no aliases.

The create request contains reportType, streetId, note and optional photoId/location/collectionDate. Derive ward from the stored street, reporter from internal actorId and timestamps from an injected Clock. Confirm role and existence; reject photo references owned by another user. Persist precise coordinates privately. Populate public street coordinates, never submitted coordinates, in mapLocation. Remove reporterId and lifecycle status from ReportView. Add streetName and confirmedByMe; count actual confirmation rows. Until Step 5, lastEvidenceAt uses existing evidence timestamps and the same fixed six-hour rule; do not return fabricated freshness values.

Implement ward-scoped report filtering, page/size (0/20 defaults, size 1–100), optional street/type/floodStatus and deterministic CONFIRMATIONS or NEWEST ordering as defined in the full-team plan. Reject cross-ward street filters and floodStatus without FLOODED_ROAD. Add real PostgreSQL tests for four types, validation, preference integrity, photo ownership, filtering/pagination and no private fields in public views. Do not implement auth sessions, controllers, photo upload or dashboard queries.
```

**Done when:** Canonical interfaces compile and provide private, correctly filtered views to the API owner.

## Step 4 Migrate dated schedules and enforce collection rules

**Goal:** Replace weekly scheduling and pickup attempts with the plan's dated collection facts, without losing history.

```text
Apply the common instructions. Implement Step 4 only. Add a forward migration for dated collection_schedules and collections. The dated schedule fields are id, street_id, operator_id, collection_date and created_at, unique street/date. collections has id, street_id, collection_date, completed_by and completed_at, unique street/date and a composite FK to the dated schedule. Replace Pickup with CollectionRecord and update JPA validation, SQL, module API and tests together.

Do not erase weekly schedule or FAILED pickup history: retain those legacy structures under explicit legacy table names, remove their JPA mappings and stop querying them in the current application. Backfill COMPLETED pickups into collections with original IDs/times. Produce prerequisite dated occurrences for historical completions; assign the recorded operator for those dates only if ward assignment is valid. Expand active legacy weekly schedules into a documented 30-day cutover horizon; do not invent arbitrary historical occurrences. For a ward with more than one possible operator, require an explicit migration mapping; never select the first UUID. Record the cutover boundary and fail preflight on unresolved conflicts or invalid historical assignments.

Implement CollectionApi.listCollectionSchedules, listMyAssignedStreets and recordCollection. Validate OPERATOR plus ward assignment, scheduled date and no future completion. Repeated completion returns the original record without rewriting its timestamp; use atomic insert-on-conflict. No FAILED state in the new completion API and no correction workflow. Derive SCHEDULED/DONE/MISSED at read time using injected Clock and Africa/Lagos; DISPUTED comes in Step 6. Date ranges are inclusive, maximum 31 days. Enforce MISSED_PICKUP report dates reference an elapsed dated occurrence, including the DB FK. Update obsolete tests that expected duplicate errors or FAILED writes to assert the new documented behaviour; retain legacy preservation tests.
```

**Done when:** Duplicate completion races create one row; wrong ward/unscheduled/future writes fail; overdue reads work without jobs; historical data is retained.

## Step 5 Make confirmations repeat safe and implement flood freshness

**Goal:** Preserve unique resident counts while permitting fresh evidence after expiry.

```text
Apply the common instructions. Implement Step 5 only. Add a forward migration renaming confirmations to report_confirmations, user_id to resident_id and created_at to confirmed_at. Rename the unique constraint to uq_report_confirmation_resident and update ReportConfirmation, SQL, fixtures and tests. Preserve confirmation IDs and timestamps. Rename ConfirmationApi.confirm to confirmReport, returning the authoritative ReportView after commit-visible work. Use SELF_CONFIRMATION_NOT_ALLOWED for self-confirmation; use canonical error codes.

Require RESIDENT and a different reporter. Lock the report row consistently before evidence writes; preserve module boundaries through a report.api collaboration method. First confirmation inserts one unique resident row. Repeating while current is a no-op returning current data. For an already-confirmed resident, update confirmed_at only when that flood is expired at transaction start. Counts never increase on reconfirmation. Non-flood repeats are always no-ops. Do not add removal, notifications or an evidence-history table.

Use injected Clock and ekowatch.flood-freshness-hours default 6. lastEvidenceAt is max(report.createdAt, confirmation timestamps); CURRENT iff server time is strictly before expiresAt, otherwise EXPIRED. Non-flood floodStatus/expiresAt are null. GET never writes. Verify two different residents racing count twice; same resident racing counts once; immediate repeats preserve timestamps; expired-flood reconfirmation renews without increasing people; boundary and one-hour-outage reads work. Document that a new submission after a later expiry is fresh testimony, not perpetual replay of an old request.
```

**Done when:** Unique counts, freshness boundaries and repeat semantics are proven against PostgreSQL.

## Step 6 Derive disputes hazards and authority metrics

**Goal:** Deliver the required read models without adding resolution or dispatch flows.

```text
Apply the common instructions. Implement Step 6 only. Replace the empty dashboard scaffold with authority and include authority/photo in ModuleBoundaryTest. Add AuthorityApi.getWardDashboard and ReportApi.listWardHazards, with canonical view types from build.md. Enforce AUTHORITY for performance queries. Normal report, schedule and hazard views must not expose operator performance or private reporter identity/location.

Derive DISPUTED when a completion and MISSED_PICKUP report exist for the exact street/date. Keep both facts intact. An overdue uncompleted occurrence is MISSED; a resident report does not make an unelapsed occurrence MISSED. Authority missedPickupCount excludes disputes; disputedPickupCount is separate; completedPickupCount includes completed but disputed occurrences. Attribute performance to the operator_id recorded on the dated schedule, not today's arbitrary ward assignee.

Pickup metrics use the requested inclusive date range, maximum 31 days. Hazard counts are a snapshot at generatedAt. Count reports, not distinct roads: blockedDrainCount covers recorded blocked-drain reports; currentFloodCount excludes expired floods. Hazards contain CURRENT floods plus blocked drains, generatedAt and nullable newestReportAt. Do not silently truncate the one-ward hazard list. Use deterministic ordering and real records, no stored dashboard counters. Add PostgreSQL tests for completion/report races in both orders, exact-date matching, count definitions, freshness, empty ward and wrong-role refusal. Do not add dispute adjudication, report closure, map UI or background tasks.
```

**Done when:** Query totals match fixture records and no read mutates state or exposes private data.

## Step 7 Protect repeated report creation

**Goal:** Implement the full-team plan's existing repeat-submission contract, not an additional feature.

```text
Apply the common instructions. Implement Step 7 only. Add idempotency_records through the next forward migration. Store user_id, operation, idempotency_key, canonical request hash, report_id, original response payload, created_at and expires_at with unique (user_id,operation,idempotency_key), appropriate FKs and 24-hour retention semantics. Use a typed internal createReport argument for the UUID idempotency key; Esther maps Idempotency-Key from HTTP. No header parsing/controllers here.

Make report insertion and completed response persistence atomic in one transaction. Concurrent same-user/same-key/same-payload creation returns the original ReportView and creates one report; different payload returns IDEMPOTENCY_KEY_REUSED. The first result is a snapshot and must not silently become a later report view on replay. Build canonical hashing from validated request values, including photoId/location/date; do not hash object identity or unordered map output. Scope keys by user and operation. Missing key uses IDEMPOTENCY_KEY_REQUIRED. After expiry, reuse starts a new intent atomically under the same unique-key protection; document that replay protection ends after 24 hours. No cleanup scheduler is required.

Test same-key concurrent calls, mismatch, different users, rollback without partial report/record, expiry boundary and lost-response retry. Use PostgreSQL conflict handling without continuing in an aborted transaction. Keep existing report APIs/callers and tests consistent with the required key. Do not add generic idempotency infrastructure for unrelated operations.
```

**Done when:** A stalled client can repeat report creation safely within the documented window.

## Step 8 Complete reproducible demo data

**Goal:** Meet the brief's exact dataset requirement and hand off credentials to Esther.

```text
Apply the common instructions. Implement Step 8 only. Extend shared/DemoSeed.java and db/demo/demo.sql for the canonical schema. Retain explicit local-demo plus ekowatch.demo.enabled opt-in and existing prod/production protections. Do not automatically seed a deployed database or weaken the guard. Supply Esther with a documented deliberate demo provisioning procedure; actual deployed credential wiring remains hers.

Seed one fictional ward, at least eight streets, thirty consecutive calendar days of dated schedule coverage according to each street's collection pattern, two assigned operators, four residents, one authority and an unassigned operator for refusal checks. Use fixed seed UUIDs and explicit stable date/time anchors. Cover all four report types, zero/multiple confirmations, current/expired flood, scheduled/done/missed/disputed collection cases, a long street name, no-report street, no-photo/location-denied report and a photo reference only when a genuinely sanitised demo asset has been provisioned. Never invent an asset URL or mark unsanitised bytes safe.

Rerun must not duplicate records, reset user actions or refresh flood timestamps. Document fresh disposable demo-database provisioning for a new presentation date; do not add an automatic reset or truncate command against shared data. Keep login disabled until Esther supplies password-encoder-compatible demo hashes; do not assume an encoder absent from this ZIP or claim those accounts can log in. Add seed assertions for volumes, relationships, every required derived case and repeat stability. Label names, activity and evidence fictional.
```

**Done when:** Dataset coverage is tested; the missing photo/credential integration, if any, is explicitly handed off rather than faked.

## Step 9 Finish rule evidence migration checks and handoff

**Goal:** Deliver verifiable database work and a precise integration handoff.

```text
Apply the common instructions. Implement Step 9 only. Run mvn -B verify with Docker and Java 25 available. Check all forward migrations on empty PostgreSQL and a populated V1 fixture, stable UUID/history preservation, Hibernate validation, module boundaries and seed repeatability. Update tests for the canonical API rather than keeping old aliases. Add focused indexes for the actual report/date/authority queries; document query plans against representative seed data without inventing timing or row counts. Do not add a load-test project.

Replace the old temporary-table uniqueness demonstration with evidence tied to the real canonical rule: run the actual invariant test against a disposable migrated test database with protection present, then show that the same assertion detects duplicate resident rows when the constraint is removed in an isolated mutation run. A named ON CONFLICT statement merely failing because the constraint is missing is not proof of the business invariant. Keep production migrations unchanged and keep deliberately failing mutation evidence outside the normal passing verification path. Provide precise commands and actual outputs. Do the equivalent controlled removed-role-check evidence for authority access; Esther still owes the HTTP wrong-role test.

Update docs/engineer-a-handoff.md, docs/engineer-a-verification.md, docs/data-contract.md, docs/decisions.md and two ADRs for confirmation/freshness and disputed pickups. ADRs include rejected alternative, accepted cost and reversal observation. Document safe migration recovery and legacy-data limitations; do not claim Flyway Community automatically undoes migrations. Retain requestId logging, add concise operation/outcome context without notes, identities, coordinates or secrets. List the exact methods/views Esther must wire, the photo metadata-stripping requirement, password provisioning and remaining frontend/deployment checks. No fabricated HTTP/live-demo success, no commit or deployment.
```

**Done when:** All runnable checks pass, blockers are named, and Esther can implement the agreed HTTP contract without guessing data names or meanings.

## Final completion checklist

- [ ] V1 preserved; forward migrations cover fresh and populated paths.
- [ ] Single canonical names used in SQL, JPA, service APIs, DTOs and seed/tests.
- [ ] Four report types and ward/street integrity enforced.
- [ ] Public report views omit reporter identity and precise submitted location.
- [ ] Assigned operators only; one completion per scheduled street/date.
- [ ] Two residents count twice; one resident counts once; invariant failure evidence retained.
- [ ] Flood expiry and missed pickups computed on reads with deterministic clock tests.
- [ ] Disputes preserve both claims; authority metrics derive from real records.
- [ ] Report retries use the agreed 24-hour idempotency policy.
- [ ] One ward, eight streets minimum, thirty-day schedule coverage and awkward cases verified.
- [ ] Photo sanitisation, HTTP authentication/authorisation, credentials and live integration clearly handed to Esther.
- [ ] No optional feature, extra workflow, controller or frontend added by these prompts.
