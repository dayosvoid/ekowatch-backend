# Step 9 final handoff to Esther

This section supersedes historical current-versus-target summaries below. Data-layer rules, canonical DTOs and service tests are implemented; HTTP/live integration is not represented as complete. All actorId values below are private UUIDs from a verified principal, never request JSON. UUIDs become strings at HTTP boundaries; instants are UTC; calendar dates use Africa/Lagos. JPA entities, including UserAccount/password_hash, must never be response objects.

## Exact service methods to expose

| Method (parameter types/order) | Return |
| --- | --- |
| WardApi.listWards(UUID actorId) | List<WardView> |
| WardApi.listStreets(UUID actorId, UUID wardId) | List<StreetView> |
| UserApi.getCurrentUser(UUID actorId) | CurrentUserView |
| UserApi.updateMyPreferences(UUID actorId, UUID wardId, UUID streetId) | CurrentUserView |
| ReportApi.createReport(UUID actorId, UUID idempotencyKey, CreateReportRequest request) | ReportView |
| ReportApi.getReport(UUID actorId, UUID reportId) | ReportView |
| ReportApi.listReports(UUID actorId, UUID wardId, UUID streetId, ReportType reportType, FloodStatus floodStatus, Integer page, Integer size, ReportSort sort) | Page<ReportView> |
| ReportApi.listWardHazards(UUID actorId, UUID wardId) | WardHazardsView |
| ConfirmationApi.confirmReport(UUID actorId, UUID reportId) | ReportView |
| CollectionApi.listCollectionSchedules(UUID actorId, UUID wardId, LocalDate fromDate, LocalDate toDate) | List<CollectionScheduleView> |
| CollectionApi.listMyAssignedStreets(UUID actorId, LocalDate fromDate, LocalDate toDate) | List<StreetView> |
| CollectionApi.recordCollection(UUID actorId, UUID streetId, LocalDate collectionDate) | CollectionView |
| AuthorityApi.getWardDashboard(UUID actorId, UUID wardId, LocalDate fromDate, LocalDate toDate) | WardDashboardView |
| PhotoApi.getPhoto(UUID actorId, UUID photoId) | PhotoView |

ReportApi.lockForConfirmation(UUID actorId, UUID reportId, Instant transactionStartedAt) is mandatory-transaction internal module collaboration, NOT an HTTP operation. No old create/get/list/confirm/authorityReports aliases remain in these APIs. No upload orchestration API was added.

## Exact view/request fields

| Type | Fields |
| --- | --- |
| CreateReportRequest | reportType, streetId, note, photoId, location, collectionDate. No actor/reporter/ward/key JSON field. |
| ReportView | reportId, reportType, wardId, streetId, streetName, note, photoUrl, createdAt, collectionDate, confirmationCount, confirmedByMe, lastEvidenceAt, floodStatus, expiresAt, mapLocation, locationPrecision |
| CollectionScheduleView | scheduleId, wardId, streetId, streetName, collectionDate, collectionStatus, completedAt, missedReportCount |
| CollectionView | collectionId, streetId, collectionDate, completedAt, collectionStatus |
| WardHazardsView | wardId, generatedAt, newestReportAt, items (List<ReportView>). No reports alias. |
| WardDashboardView | wardId, fromDate, toDate, generatedAt, missedPickupCount, disputedPickupCount, blockedDrainCount, currentFloodCount, newestReportAt, operatorPerformance |
| OperatorPerformanceView | operatorId, operatorName, scheduledPickupCount, completedPickupCount, missedPickupCount, disputedPickupCount |
| CurrentUserView | userId, email, displayName, role, wardId, streetId (current user's own details only) |
| WardView | wardId, wardName |
| StreetView | streetId, wardId, streetName, mapLocation |
| PhotoView | photoId, photoUrl, contentType, sizeBytes, createdAt |
| Page<T> | items, page, size, totalElements, totalPages |
| Location | latitude, longitude; submitted location is private, mapLocation is public street coordinates only |

Counts are long, page indices/sizes/totals of pages are int, IDs are UUID, dates LocalDate and times Instant internally. photoUrl, collectionDate, floodStatus, expiresAt, mapLocation, newestReportAt and uncompleted completedAt can be null where applicable. Public report/schedule/hazard views expose neither private reporter identity/coordinates nor operator performance. locationPrecision is STREET. Role is RESIDENT/OPERATOR/AUTHORITY; ReportType is MISSED_PICKUP/ILLEGAL_DUMPING/BLOCKED_DRAIN/FLOODED_ROAD; FloodStatus is CURRENT/EXPIRED; CollectionStatus is SCHEDULED/DONE/MISSED/DISPUTED; ReportSort is CONFIRMATIONS/NEWEST.

## Integration still required

Esther must verify HTTP authentication, trusted principal extraction, role/ward refusal, malformed UUID/date/header handling and error statuses, including an actual resident/operator HTTP refusal for authority performance. The service-level positive and removed-role-check evidence does not establish HTTP authorization. Map the canonical codes: VALIDATION_FAILED, UNAUTHENTICATED, FORBIDDEN, RESOURCE_NOT_FOUND, WARD_ASSIGNMENT_REQUIRED, SELF_CONFIRMATION_NOT_ALLOWED, IDEMPOTENCY_KEY_REQUIRED, IDEMPOTENCY_KEY_REUSED, PHOTO_TOO_LARGE, UNSUPPORTED_PHOTO_TYPE, INTERNAL_ERROR. Existing advice contains older mappings and is not claimed to implement this final mapping.

Parse Idempotency-Key as a UUID outside CreateReportRequest and preserve the same key for the same intended retry. The current controller passes null pending Esther's integration and therefore refuses real creation; do not replace it with a random server key. Protected retries return the original snapshot, not live confirmation/freshness state; after 24 hours reuse is a new intent. Flood confirmations after expiry are deliberate new testimony, not perpetual unattended retries. Service role/ward checks remain necessary even after HTTP checks. Pickup date ranges are inclusive, maximum 31 days; report pagination defaults 0/20, size 1–100; floodStatus requires FLOODED_ROAD; report ordering has stable UUID ties. The one-ward hazard list is complete and untruncated.

The photo adapter must strip metadata, including EXIF/GPS, verify resulting content/size and ownership, and persist only a trusted sanitised storage reference. It must resolve a safe URL without exposing raw keys or private coordinates. No real sanitised demo asset was supplied; all seed photo references remain null and no upload/sanitisation/provider success is claimed. Photo reads are owner-accessible or attached public report evidence according to the service rules.

Demo account markers are not usable credentials. SecurityConfig currently defines BCryptPasswordEncoder; verify the actual runtime configuration and privately provision compatible hashes. No account login or deployed credential wiring has been verified. See demo-provisioning.md for both local opt-ins/production exclusion, explicit fictional anchors, stable UUIDs, preserved user actions and deliberate fresh-database procedure for a new presentation date. Do not reset or automatically seed shared/deployed databases. Frontend empty/error/privacy/freshness/dispute states, final OpenAPI, upload integration, deployed configuration/migrations, end-to-end demo and deployment checks remain the team's work; none was executed here.

## Migration recovery and legacy limits

V1–V5 are preserved; V6 adds five read indexes only, with no data/UUID/status backfill. Regular CREATE INDEX runs transactionally in PostgreSQL and can block writes while building; assess a suitable migration window on the intended environment rather than treating this tiny fixture's timings as production capacity. Never change applied migration checksums, run reset/clean against shared data, or delete Flyway history to force a rerun.

Before a real upgrade, take and verify a restorable backup; rehearse the full chain on a disposable copy with the intended cutover date and explicit operator mapping. V2 preflights incompatible long/untrimmed notes instead of truncating them. V3 requires explicit resolution of ambiguous operator assignments, conflicts and existing missed-report dates. Do not guess the first UUID, discard incompatible notes or reinterpret FAILED history. Coordinate any approved source-data remediation/mapping with the owners before retrying.

On failure, stop dependent writes, retain the exact migration/error evidence and inspect actual schema plus Flyway history. PostgreSQL transactional migrations used here roll back on failure, as the negative preflight tests prove. Verify rollback rather than assume it: deployment interruptions, manual/nontransactional DDL or other engines can leave different states. Flyway Community does NOT automatically undo migrations. Do not run repair automatically; it adjusts metadata, not the database contents. Resolve a diagnosed issue, restore a verified backup when appropriate, or supply a reviewed forward recovery migration; rerun validation and the disposable upgrade rehearsal before a deliberate real retry. No recovery/deployment command was run on shared data in this work.

Legacy weekly schedules and all pickup attempts remain in legacy_collection_schedules/legacy_pickups; FAILED remains history, not a current completion. Completed historical rows keep IDs/times and get only justified prerequisite occurrences. Active weekly expansion covers the documented 30-day cutover horizon, not arbitrary past history; ambiguous data must be mapped explicitly. The fixed Step 8 fictional presentation has its own documented anchors; it is not evidence about real residents or historical operations. Idempotency cannot be backfilled for unknown past client requests. No dispute adjudication, report closure, evidence-history retention workflow or background reconciliation was introduced.

See ADR 001/002 for rejected alternatives, accepted costs and reversal observations, and engineer-a-verification.md for actual commands, plans, migration/mutation evidence and limitations. Prior sections below remain historical.

---
# Step 8 handoff - deliberate fictional demo provisioning

See [demo-provisioning.md](demo-provisioning.md) for the fixed 31 October presentation, eight-street patterns/identities, counts, guarded local startup, fresh disposable database procedure for a new presentation date, and credential/photo gaps. Both local-demo and ekowatch.demo.enabled=true remain necessary; prod and production always exclude seeding. No guard was weakened and nothing was deployed or seeded against a shared database.

All seed writes are insert-only, with explicit stable dates/times and existing UUIDs retained. They never reset user actions, provision usable passwords or refresh floods. The current repository has a BCryptPasswordEncoder bean; Esther must verify her actual runtime encoder and privately provision compatible demo hashes. Existing disabled markers are not login credentials. No sanitised asset was supplied, so no photo metadata/URL was invented; Esther must sanitise/verify a real asset and deliberately provision/attach its trusted photo reference. These integrations remain explicitly unverified. No migration/backfill or Step 9 work was added. Step 7 principal/Idempotency-Key/error integration remains Esther's responsibility.

---
# Step 7 handoff - report creation retries

Use `ReportApi.createReport(UUID actorId, UUID idempotencyKey, CreateReportRequest request)`; no keyless overload remains. Supply actorId only from the trusted principal. Parse the Idempotency-Key header into the separate UUID argument; never generate a new key per retry or accept actorId/key in report JSON. The client should retain one UUID for the same intended submission and payload. Missing keys raise IDEMPOTENCY_KEY_REQUIRED; mismatched protected payloads raise IDEMPOTENCY_KEY_REUSED. Esther owns header parsing/validation and final HTTP status/error mapping; none was added here.

The existing ReportController's only change is compile-time adaptation to this signature with null until Esther wires the parsed header. Real create calls currently fail closed with IDEMPOTENCY_KEY_REQUIRED; do not treat mock-based controller tests as header integration evidence. Do not replace null with a server-generated random UUID. Existing GET/list routes are unaffected.

V5 adds idempotency_records only. Existing reports, seed records and UUIDs remain unchanged; no backfill is possible or required for historical requests. Persisted ReportView snapshots are replayed unchanged within 24 hours, including original confirmation/freshness/photo/street fields. New reads can return newer data. At exact expiry reuse is a new intent and can create another report; prior reports remain. The old record is replaced atomically under the same unique user/operation/key tuple. There is no scheduler or automatic deletion promise, and no Step 8 seed expansion. See docs/data-contract.md for schema, canonical hashing, transaction and error semantics.

---
# Step 6 handoff

AuthorityApi.getWardDashboard and ReportApi.listWardHazards are ready for Esther's HTTP integration. WardHazardsView uses items (List<ReportView>) and the items() accessor, as specified by the full-team plan; there is no reports alias. See docs/data-contract.md for signatures, exact views, role/range rules, snapshot semantics and ordering. Supply internal actorId from the trusted principal only. No new controllers/routes were added. Transitional authorityReports was removed. No migration/backfill, seed expansion or stored counters; V1–V4 and stable records remain unchanged. Steps 7–9 remain unimplemented by this change.

---

# Step 5 handoff - confirmations and flood testimony

Verified: focused 25 tests passed; mvn -B clean verify passed all 73 tests with zero failures/errors/skips and packaging at 2026-10-31T14:39:56+01:00. Initial temporary-constraint-name test failure, its correction, exact commands and Step 5 file inventory are preserved in engineer-a-verification.md.

The current exact contract is the Step 5 section of data-contract.md. V4 renames the confirmation table/columns/unique constraint and associated PK/FK/index names without rewriting data. V1-V3 and the dated-collection cutover remain untouched. Confirmation IDs/timestamps survive migration. New confirmations use report_confirmations / ReportConfirmation / residentId / confirmedAt and the named uq_report_confirmation_resident constraint. Historical migration fixtures retain old names intentionally.

Wire ConfirmationApi.confirmReport(actorId,reportId), returning ReportView; the old confirm method is gone. actorId is private trusted-principal input, never client JSON. The existing controller path is retained, resolves principal through ActorAccess.id, and returns the view after the transaction commits. Self-confirmation uses SELF_CONFIRMATION_NOT_ALLOWED; existing advice maps it to 409. Other service errors are VALIDATION_FAILED, RESOURCE_NOT_FOUND and FORBIDDEN. No controllers/auth/session/login flow or paths were added, and Esther's principal/security implementation is preserved. She still owns final HTTP status/OpenAPI/security integration; CREATED remains the existing transport status for her review.

Each write locks the report FOR UPDATE via a mandatory-transaction report.api collaboration before reading/writing evidence. At READ_COMMITTED, the service captures injected Clock time at entry before waiting. First confirmation inserts a unique resident; current repeats and every non-flood repeat are no-ops. An already-confirmed resident may renew only an expired flood, retaining the original UUID and count and updating only confirmed_at. A concurrent renewal already committed under that report lock prevents a second renewal; a call that began current cannot become fresh testimony simply by waiting past expiry. Legacy status values remain private stored history, do not gate the canonical confirmation operation, and are never transitioned by it.

ekowatch.flood-freshness-hours defaults to 6 (EKOWATCH_FLOOD_FRESHNESS_HOURS override), positive whole hours. Projection/filter/renewal decisions share the same duration. lastEvidenceAt is max(report time, real confirmation timestamps), with strict CURRENT-before/exact-EXPIRED boundary. Non-flood freshness fields are null. Reads do not write, even after a one-hour outage. Response construction/commit failure rolls back evidence; no successful DTO is returned for an aborted operation. A response may become outdated immediately after another resident commits, so it is not a stored perpetual replay snapshot.

A resident submitting again after a later expiry gives fresh testimony, not perpetual replay of an old request. Do not make the client automatically renew expired floods by indefinitely replaying a stale action; get deliberate user testimony. Immediate/lost-response retries while current are safe no-ops, but later expiry is a new evidence window. No confirmation removal, notification, evidence-history table or generic idempotency mechanism was added. Report creation retry protection remains Step 7; disputes and authority projections remain Step 6.

Demo SQL now uses canonical confirmation names and retains fixed IDs, insert-only conflict behaviour, disabled-login hashes and opt-in guards; reruns do not refresh evidence. No final Step 8 expansion. New populated-V1/fresh migration tests verify exact timestamp/UUID preservation, uniqueness/FKs and renamed metadata. Focused PostgreSQL tests cover commit visibility, rollback after a projection failure, configured duration, first/new/renewed evidence, all non-flood repeats, distinct/same-resident races, wait-across-expiry, exact boundaries/outage reads and no GET writes. Existing valid role/authority/unique-person assertions remain; obsolete duplicate-error/OPEN-only workflow expectations are replaced with the requested repeat-safe/history-preservation semantics. Actual commands/results are recorded in engineer-a-verification.md.

No shared/live database was used. An eventual authorised real migration still needs the prior rehearsal/recovery procedure; successful Flyway Community migrations do not auto-undo. No commit/push/deployment. Earlier handoff sections below are historical where superseded.

---

# Step 4 handoff - dated collection cutover

Verified: mvn -B clean verify passed all 58 tests, zero failures/errors/skips, with packaging at 2026-10-31T14:14:19+01:00. The final date-fixture adjustment passed a separate six-test CollectionRulesTest rerun at 14:16:19+01:00. See engineer-a-verification.md for the full command history, file inventory and evidence limits.

V3__dated_collections.sql is the next forward migration; V1/V2 are unchanged. Canonical fields/methods/views are in the current Step 4 section of data-contract.md. Internal actor IDs still come from Esther's verified principal. No Step 5/6 work, new controller, auth flow, schedule-management interface, job, correction workflow, commit or deployment.

## One-time cutover inputs

Flyway placeholders are collectionCutoverDate (default AUTO) and collectionMigrationMapping (default []). Spring configuration maps them from EKOWATCH_COLLECTION_CUTOVER_DATE and EKOWATCH_COLLECTION_MIGRATION_MAPPING. AUTO uses the database transaction's current Africa/Lagos date; an explicit ISO date selects the reviewed cutover boundary. collection_migration_cutover permanently records the chosen date, inclusive horizon end (date+29) and migration timestamp. These are one-time migration inputs, not a way to change schedules after migration. Do not print actual environment values or sensitive mapping data in logs.

Mapping is a JSON array of trusted, operator-approved decisions. A row has streetId and operatorId; optionally collectionDate for an exact occurrence. Example shape (replace placeholders with reviewed UUIDs/dates, not literal values):

```json
[
  {"streetId":"<street UUID>","operatorId":"<default operator UUID>"},
  {"streetId":"<street UUID>","collectionDate":"YYYY-MM-DD","operatorId":"<operator for this date UUID>"}
]
```

A street default applies only to active weekly-pattern expansion within the 30-day horizon. An exact date overrides that default. Every chosen operator must have role OPERATOR and a stored assignment to that street's ward. If there is exactly one eligible operator, unmapped weekly occurrences use that unambiguous candidate. Zero/multiple eligible operators without a complete mapping fail preflight; no first UUID is selected. Invalid active ward assignments also fail instead of being silently ignored. Duplicate mappings fail uniqueness checks. Dated mappings outside cutover patterns/completions may create only prerequisite dates for actual existing MISSED_PICKUP reports; unused dated mappings fail. Such historical occurrences must be explicitly verified by the data owner, not inferred from a report's assertion alone.

## Preservation and preflight

The migration locks source tables to prevent concurrent data changes during the transactional cutover. It retains all weekly rows (including inactive) as legacy_collection_schedules and all pickup attempts (including FAILED) as legacy_pickups with their original columns, UUIDs, values and timestamps. The old JPA mappings are removed and current services never query those tables. No history is truncated/discarded.

Only actual COMPLETED attempts backfill collections, keeping their IDs and created_at -> completed_at/recorded_by -> completed_by. Their exact dates get prerequisite schedule rows, using the recorded operator only if current stored role and ward assignment are valid. No other arbitrary historical dates are expanded. Reconstructed schedule IDs are deterministic md5('historical-completion:' + pickup UUID)::uuid, with created_at equal to the legacy completion timestamp; this is not a claim that the original schedule creation time is known. Weekly occurrence IDs are deterministic md5('weekly-occurrence:' + weekly UUID + ':' + ISO date)::uuid, and mapped report occurrences use 'mapped-occurrence:' + street UUID + ':' + ISO date. Every original weekly UUID remains intact in legacy storage.

Active weekly schedules expand only cutover_date through cutover_date+29. If expansion and historical completion overlap, the historical prerequisite keeps its ID/time, and the effective mapped operator must agree. A per-date override can explicitly resolve a differing weekly default; no recorded operator or completion is rewritten. Invalid/future completion dates/roles/ward assignments, conflicting mappings and non-elapsed/missing report occurrences all fail preflight. FAILED attempts remain legacy evidence and never become completions. Current operator_wards has no historical effective dates: validating it cannot prove past assignment history, so any suspected historical changes still need explicit owner-reviewed resolution, not invented history.

Before any later authorised real migration, rehearse on an isolated populated copy: inventory weekly active/inactive patterns, all attempts and timestamps, completed operators' valid roles/wards, wards with multiple/no candidates, existing missed-report dates, explicit mapping coverage and conflicts. Review the cutover date/horizon, verify every mapped historical occurrence, and retain a recovery copy. Ambiguous data must not be guessed, truncated or discarded. We have not inspected or changed a shared/live database. A failed V3 transaction leaves V2/data intact (tested); successful migrations have no automatic Flyway Community undo. An eventual successful-cutover problem requires a reviewed forward fix or separately authorised recovery, not a reset by this code.

## Service and Esther integration

Wire CollectionApi.listCollectionSchedules(actorId,wardId,fromDate,toDate), listMyAssignedStreets(actorId,fromDate,toDate) and recordCollection(actorId,streetId,collectionDate). All ranges are inclusive and at most 31 days. Writes require the scheduled operator plus current ward assignment; repeats return the original record/time. SCHEDULED/DONE/MISSED derive from real rows and injected Clock/Lagos dates. DISPUTED remains Step 6. Report creation now requires an elapsed exact dated occurrence for MISSED_PICKUP, enforced by service and FK/check. Confirmation/flood freshness remains Step 3/5 scope.

The pre-existing collection controller paths remain for Esther to finalise. Its request is now streetId/collectionDate with no status or actor JSON; it returns CollectionView. The authority-only missed read retains its guard and uses ward-scoped canonical schedule views; no new routes/performance query were added. Existing HTTP advice maps WARD_ASSIGNMENT_REQUIRED to 403. Auth/security/principal resolution and login remain Esther's work and were preserved.

DemoSeed guards are unchanged. demo.sql now inserts seven dated occurrences with original reserved UUIDs and an explicit recorded-cutover anchor, without overwriting schedules/completions or refreshing evidence on repeat. The seed's volumes are deliberately not Step 8's final dataset. Existing tests/fixtures now use dated occurrences and elapsed MISSED_PICKUP dates; V1/weekly/FAILED preservation fixtures remain intentionally legacy SQL. Actual test commands/results are in engineer-a-verification.md. Prior handoff sections below are historical where superseded.

---

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
