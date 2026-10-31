# Step 9 final data-layer contract

V6__read_projection_indexes.sql is the next forward migration. It adds predicate-aligned indexes only: reports by ward/type/newest order, MISSED_PICKUP by exact street/date, schedules by date/street and operator/date/street, and confirmations by report/latest evidence. No row, UUID, constraint, API field or applied V1–V5 migration is rewritten. There is no backfill. Query-plan evidence uses the fixed fictional seed and PostgreSQL's normal planner settings, with actual output retained in docs/evidence/; tiny-table sequential scans are not reported as a failure or speedup.

OperationLog wraps createReport, confirmReport, recordCollection and getWardDashboard. Fixed operation/outcome events are emitted after transaction completion (committed/rolled_back/unknown); a nontransactional fallback says returned/rejected/failed rather than claiming commit. Existing requestId MDC is retained/restored. No arguments, identities, notes, submitted coordinates, credentials or exception messages enter those events. Existing requestId filtering/log pattern is unchanged.

The former temporary LIKE-table demonstration is replaced by a same-assertion test on actual report_confirmations rows under concurrent direct writes. Normal verification retains production uniqueness/authority role checks. Deliberately failing, opt-in mutations are test-source-only and own fresh disposable PostgreSQL containers; see the verification record for precise commands and actual outcomes. No runtime bypass or production migration weakening is supplied.

The earlier Step 3/4/5 current-versus-target sections below are historical. The Step 6–8 contracts, the method/view inventory and migration recovery in engineer-a-handoff.md, and the two ADRs are the final integration baseline. HTTP authentication/authorisation, Idempotency-Key parsing/error mapping, credentials, photo sanitisation, frontend and deployment remain integration handoffs, not claimed successes.

---
# Step 8 contract - fixed fictional demo dataset

The local insert-only fixture now supplies one fictional ward, eight streets, four residents, two assigned operators, one authority and one deliberately unassigned operator. It preserves original seed UUIDs. Explicit presentation anchor: 2026-10-31T12:00:00Z. Coverage: 2026-10-16 through 2026-11-14, with daily and weekday patterns, 172 dated occurrences, three completions, all four report types (nine reports) and five confirmations. Runtime status/freshness is derived from rows and Clock; timestamps never renew on a rerun. No new migration is needed; V1–V5 are unchanged.

Both local-demo and ekowatch.demo.enabled=true remain required, and prod/production exclude the runner. The runner also checks these conditions on invocation. All names, activity and evidence are fictional. All photo references remain null because no sanitised asset was supplied; credentials remain deliberately unusable until Esther provisions compatible hashes. Existing rows/actions, including preferences, hashes, report edits and confirmation times, are never overwritten. Fresh-database counts and the deliberate new-presentation provisioning procedure are documented in docs/demo-provisioning.md. Historical current-status statements below are superseded only for Step 8 seed scope.

---
# Step 7 contract - report creation idempotency

This section supersedes earlier current-status statements for report creation. The only signature is `ReportView ReportApi.createReport(UUID actorId, UUID idempotencyKey, CreateReportRequest request)`. actorId comes from the trusted principal. The UUID key is a separate internal argument, never a CreateReportRequest/JSON field. No keyless overload or alias exists. All direct service callers now supply an explicit key per new intent.

V5__report_creation_idempotency.sql adds report-owned idempotency_records and its JPA validation mapping. Columns: id (UUID primary key), user_id (UUID FK users), operation (varchar(40), currently only createReport), idempotency_key (UUID), request_hash (64 lowercase SHA-256 hex characters), report_id (UUID FK reports), response_payload (JSONB object containing the original ReportView), created_at and expires_at (UTC instants). All are required. uq_idempotency_user_operation_key uniquely protects (user_id,operation,idempotency_key); expires_at must equal created_at plus 24 hours. No historical reports/UUIDs are rewritten and no historical keys are fabricated. Existing insert-only seeds need no change; Step 8 expansion remains outstanding.

RESIDENT role and required key are checked in the service; missing key raises IDEMPOTENCY_KEY_REQUIRED. Pure request validation precedes hashing. Hash encoding is fixed and versioned: ordered fields createReport:v1, reportType.name, canonical UUID streetId, trimmed note, nullable UUID photoId, nullable latitude, nullable longitude, nullable ISO collectionDate. Each non-null field is UTF-8 with a four-byte big-endian byte-length prefix; null is length -1. Finite doubles use Double.toString with signed zero normalized to 0.0. SHA-256 covers those bytes, independent of object identity, map iteration or JSON mapper configuration. Raw submitted coordinates never enter the stored public response. New intents still enforce stored street/ward, photo ownership and elapsed dated-schedule rules.

Within READ COMMITTED, a report-specific PostgreSQL transaction advisory lock serializes the user/createReport/key tuple (hash collisions can only serialize extra work). The unique tuple remains the persistent identity check. The service reads the locked record, sampling Clock after advisory-lock acquisition. While now is strictly before expires_at, equal validated hashes return the original persisted ReportView; different hashes raise IDEMPOTENCY_KEY_REUSED. A replay does not resolve photos or requery live report projections, and never silently replaces the original snapshot with updated confirmations, expiry status, street coordinates/name or photo URL. Current role is still checked on every call.

A new intent inserts the report, projects and serializes its completed response, and inserts the record in one transaction. INSERT ON CONFLICT only replaces an expired record; a protected conflict rolls back the new report. No uniqueness exception is caught and followed by SQL in an aborted transaction. Projection, serialization, persistence or outer-transaction failure rolls back all work. Callers receive the response only after the service transaction commits.

At now >= expires_at the key begins a new intent, even with a different payload, under the same lock/unique constraint. The record's ID and unique tuple remain stable while hash/report/response/timestamps are replaced. Earlier report rows and UUIDs remain intact. Replay protection ends after 24 hours; a retry after that window can create another report. Expired rows are retained until reused; there is no cleanup scheduler or promise of automatic data deletion at 24 hours. Idempotency applies only to report creation.

Esther owns HTTP Idempotency-Key parsing/UUID validation and error mapping (IDEMPOTENCY_KEY_REQUIRED and IDEMPOTENCY_KEY_REUSED). No header parsing, routes, security or HTTP exception mapping was added. The existing ReportController call is adapted to the required signature with an explicit null key until Esther supplies her parsed header; it therefore refuses real create calls with IDEMPOTENCY_KEY_REQUIRED instead of inventing a random key. Existing controller tests remain adapter/mock checks, not a claim of a working header-integrated create endpoint. Esther must finish that integration before enabling report creation through HTTP.

---
# Step 6 contract — authority, hazards and derived disputes

This section supersedes earlier current-status statements for Step 6. No schema migration is required; V1–V4 and existing records/UUIDs are unchanged. Demo fixtures remain compatible and insert-only; expansion remains Step 8.

`AuthorityApi.getWardDashboard(UUID actorId, UUID wardId, LocalDate fromDate, LocalDate toDate)` returns `WardDashboardView`, with exactly the build.md fields and long counts. AUTHORITY is enforced in the service. The inclusive range must contain 1–31 days. `operatorPerformance` contains only operators with dated occurrences in that range, ordered by display name then UUID. Attribution uses the occurrence's operator_id regardless of later ward assignment changes. Completed counts include disputed completions; missed counts include only elapsed uncompleted occurrences; disputed counts count occurrences, not witness reports. Today is Africa/Lagos.

`ReportApi.listWardHazards(UUID actorId, UUID wardId)` requires an existing user and ward. `WardHazardsView` fields are wardId, generatedAt, newestReportAt and items (`List<ReportView>`). The list is unpaginated and untruncated, contains blocked-drain reports and CURRENT flood reports, ordered by createdAt descending then reportId ascending. It uses the existing private ReportView projection and street coordinates only. No performance fields appear in normal views.

For both reads, newestReportAt is max(created_at) across **all reports in the ward**, nullable for no reports. It is a submission timestamp, not lastEvidenceAt. Hazard counts/list are a snapshot at generatedAt, independent of the pickup date range; count reports, not streets. Freshness uses the same configured hours and strict expiry boundary as report reads. A repeatable-read transaction and one captured Clock instant keep the response internally consistent. No read writes counters or changes facts.

DISPUTED is derived only from completion plus MISSED_PICKUP for the exact street/date. Schedule reads and repeat completion responses reflect it. An uncompleted occurrence stays SCHEDULED until elapsed even when a report exists. Both original facts and timestamps survive. Concurrent responses may precede the other transaction's commit; subsequent reads derive the combined committed facts. No dispute resolution workflow exists.

The transitional ReportApi.authorityReports operation is removed; its valid authority-role tests now exercise AuthorityApi. Esther must expose the new service methods through her principal, parsing, authorization and HTTP mapping layer; actorId never comes from JSON. No controllers or HTTP routes were added.

---
# Step 5 contract - repeat-safe confirmation and flood freshness

Current schema is V4__report_confirmations.sql. Earlier sections below are historical where superseded. V1/V2/V3 are unchanged, including the Step 4 cutover. V4 performs metadata-only renames, with no row insert/delete/update: confirmations -> report_confirmations; user_id -> resident_id; created_at -> confirmed_at; unique constraint -> uq_report_confirmation_resident(report_id,resident_id). PK/FK constraint names and the resident lookup index are renamed consistently. All confirmation UUIDs, values, timestamps, defaults, nullability, uniqueness and FKs survive.

report_confirmations maps to confirmation.domain.ReportConfirmation: id -> id UUID; report_id -> reportId UUID; resident_id -> residentId UUID; confirmed_at -> confirmedAt Instant. The old Confirmation entity is removed. Services, demo SQL and active fixtures use only the canonical names. Intentional pre-V4 migration fixtures and old migration scripts retain the historical names.

```java
ReportView ConfirmationApi.confirmReport(UUID actorId, UUID reportId)
// report.api collaboration only, requires an existing transaction; never an HTTP operation:
ReportView ReportApi.lockForConfirmation(UUID actorId, UUID reportId, Instant transactionStartedAt)
```

actorId is private verified-principal input, never client JSON. transactionStartedAt is private server Clock input supplied by ConfirmationService. No confirm/requireConfirmable aliases remain. Stored RESIDENT role and a different reporter are required. Null report IDs fail VALIDATION_FAILED, unknown reports RESOURCE_NOT_FOUND, invalid actor/role FORBIDDEN, and self-confirmation SELF_CONFIRMATION_NOT_ALLOWED. Legacy report status/updated_at remain stored and unchanged; the old OPEN-only confirmation workflow no longer governs the canonical MVP operation. No resolution transitions are added or exposed.

ConfirmationService enters a READ_COMMITTED transaction and captures its Clock instant before role/lock waits, truncated to PostgreSQL microseconds. It obtains the report row FOR UPDATE through report.api, then reads committed evidence under that lock. The collaboration method uses MANDATORY transaction propagation so the lock cannot be acquired/released in a separate transaction before the writer. Every confirmation service evidence write takes the same report lock first. Different residents' writes serialize on one report, trading per-report write parallelism for a consistent renewal decision. No module imports another module's internal packages.

First evidence inserts one UUID row using the named unique constraint and explicit confirmedAt from the captured instant. If that resident already has a row, non-flood repeats always do nothing; flood repeats do nothing while current at transaction entry. Only a FLOODED_ROAD already expired at that entry instant can update that resident's confirmed_at. UUID and resident count do not change. A renewal committed by a writer ahead of a waiting transaction is visible after the lock and prevents another renewal. If a call began current and waited until after expiry, it remains a no-op, though its returned read can correctly be EXPIRED. New residents can insert their first evidence after expiry and increase the distinct-person count normally.

The response is the authoritative full canonical ReportView, read from real rows after the operation's writes under the same lock. The Spring transaction interceptor commits before the caller receives the DTO; commit/projection failure propagates instead of returning success. This is a snapshot at that operation's serialization point, not a promise that another resident cannot commit immediately afterward. The API does not return an entity, reporter identity, legacy lifecycle state or precise submitted location.

Config property ekowatch.flood-freshness-hours defaults to 6, optionally supplied by EKOWATCH_FLOOD_FRESHNESS_HOURS; values must be positive whole hours. ReportService uses one configured Duration for both projection expiry and SQL filter cutoffs, and the locked projection gives ConfirmationService the same policy. lastEvidenceAt = max(report.created_at, report_confirmations.confirmed_at). FLOODED_ROAD expiresAt = lastEvidenceAt + duration; CURRENT iff operation time is strictly before expiresAt, otherwise EXPIRED. Non-flood floodStatus/expiresAt are null, while lastEvidenceAt still uses actual stored evidence. Ordinary GET/list never write or refresh timestamps, including during/after an outage. No jobs/counters/evidence-history table are added.

A later submission after a later expiry is fresh testimony, not perpetual replay of the original request. The same resident still counts once, but deliberately attests again with a new evidence time. The server does not replay an old confirmation response forever or renew on reads. Esther's client integration must treat a post-expiry confirmation as a deliberate new attestation, rather than an unattended loop retrying an old action across repeated expiries. Report-creation idempotency remains Step 7 and is not introduced here.

Existing ConfirmationController path /api/reports/{reportId}/confirm is retained and still obtains actorId from ActorAccess.id(Authentication). It now calls confirmReport and returns ReportView after commit. Existing CREATED status is retained for Esther's HTTP review; self-confirmation is mapped through existing advice to 409 with the canonical code. No new controller, route, auth flow or OpenAPI paths were added. Seed volumes/IDs stay the same and insert-only reruns do not refresh confirmed_at; final seed expansion remains Step 8. No Step 6 disputes/metrics were implemented.

---

# Step 4 contract - dated schedules and completion facts

Current data schema is V3__dated_collections.sql after unchanged V1/V2. Earlier sections below are historical where superseded. No Step 5 confirmations or Step 6 disputes/metrics have been implemented.

| Storage/entity | Exact columns -> Java members |
| --- | --- |
| collection_schedules / CollectionSchedule | id -> id UUID; street_id -> streetId UUID FK streets; operator_id -> operatorId UUID FK users; collection_date -> collectionDate LocalDate; created_at -> createdAt Instant |
| collections / CollectionRecord | id -> id UUID; street_id -> streetId UUID FK streets; collection_date -> collectionDate LocalDate; completed_by -> completedBy UUID FK users; completed_at -> completedAt Instant |
| legacy_collection_schedules | Original weekly rows, IDs, active flags, weekday values and constraints retained; no JPA mapping/current application queries |
| legacy_pickups | Every original COMPLETED/FAILED attempt, ID, recorded_by and created_at retained; no JPA mapping/current application queries |
| collection_migration_cutover | Internal migration audit: id=1, cutover_date, horizon_end_date, recorded_at; not a product DTO/API |

Named uniqueness: uq_schedule_street_date(street_id,collection_date), uq_collection_street_date(street_id,collection_date). collections and reports have composite street/date FKs to collection_schedules. Report street/ward integrity remains. Database checks forbid future completion dates and require MISSED_PICKUP dates to be strictly before the database's Lagos today; V2 still requires dates exactly for MISSED_PICKUP. No lifecycle/history column was removed. Pickup entity and old weekly mapping were removed; no status is stored in collections.

```java
List<CollectionScheduleView> CollectionApi.listCollectionSchedules(
    UUID actorId, UUID wardId, LocalDate fromDate, LocalDate toDate)
List<StreetView> CollectionApi.listMyAssignedStreets(
    UUID actorId, LocalDate fromDate, LocalDate toDate)
CollectionView CollectionApi.recordCollection(
    UUID actorId, UUID streetId, LocalDate collectionDate)
```

actorId is private verified-principal input, never client JSON. listCollectionSchedules requires a stored user and existing explicit ward, scopes every row to it, and orders collectionDate, streetName, streetId, scheduleId. Dates are inclusive, required, ordered, at most 31 days. listMyAssignedStreets requires OPERATOR, returns distinct streets from that operator's dated assignments in range and still-valid ward assignments, ordered name/UUID; no assignments yields an empty list. Neither read exposes operator performance or submitted report coordinates.

recordCollection requires OPERATOR, current ward assignment, the actor recorded as operator_id on that street/date occurrence, and a date no later than injected-Clock Lagos today. Invalid/null/future/unscheduled dates use VALIDATION_FAILED, unknown street RESOURCE_NOT_FOUND, missing ward assignment WARD_ASSIGNMENT_REQUIRED, and wrong role/scheduled operator FORBIDDEN. Insert-on-conflict runs at READ_COMMITTED and returns the committed original ID/timestamp on repeat without rewriting. completedBy is the trusted actor; completedAt is injected Clock time at PostgreSQL microsecond precision. There is no FAILED input, correction flow or old recordPickup/missed service alias.

| View | Exact members |
| --- | --- |
| CollectionScheduleView | UUID scheduleId, UUID wardId, UUID streetId, String streetName, LocalDate collectionDate, CollectionStatus collectionStatus, Instant completedAt, long missedReportCount |
| CollectionView | UUID collectionId, UUID streetId, LocalDate collectionDate, Instant completedAt, CollectionStatus collectionStatus |

CollectionStatus has only SCHEDULED, DONE, MISSED, DISPUTED. This step returns SCHEDULED for today/future without completion, MISSED for elapsed dates without completion, DONE when completion exists. DISPUTED is reserved for Step 6; a missed report does not change these states yet. completedAt is null on uncompleted schedule views; recordCollection returns a completed CollectionView with a non-null ID/time. missedReportCount counts actual MISSED_PICKUP reports for the exact street/date, independent of confirmations. Reads use injected Clock and Africa/Lagos; GET never writes state or counters.

ReportService validates MISSED_PICKUP through collection.api.listCollectionSchedules for the exact street/date and requires it elapsed; the database FK/check protects direct writes too. Reports can still disagree with an existing completion: both facts are kept for Step 6. No photo, confirmation or report-freshness semantics changed.

Existing CollectionController was adapted in place, retaining Esther's /api/pickups and /api/authority/missed-pickups paths for her integration review. The completion request now has streetId and collectionDate only, returns CollectionView and obtains actorId via ActorAccess.id(Authentication). The existing authority read keeps its AUTHORITY guard, requires wardId, and filters canonical schedule views to MISSED; no dashboard/performance query was added. These transport paths are not old service aliases. No new controller/routes were introduced; Esther owns final path/status/OpenAPI decisions and wiring of the new listing APIs. WARD_ASSIGNMENT_REQUIRED is mapped to 403 by the existing advice.

Migration inputs, history preservation, cutover and explicit-mapping procedure are documented in engineer-a-handoff.md. Seed compatibility remains seven stable dated occurrences anchored at cutover_date-1 through cutover_date+5, fixed original demo UUIDs, insert-only. No final Step 8 expansion or credential/photo provisioning was added.

---

# Merged HTTP integration note

The Steps 2-3 contract below has been restored from dayo/data-rules without old service aliases. Esther's existing ReportController now consumes the shared CreateReportRequest.note and calls createReport/getReport/listReports using ActorAccess.id(Authentication). Its existing paths remain; listing uses required wardId, optional streetId/reportType/floodStatus, page/size defaults 0/20 and CONFIRMATIONS default sorting, returning Page<ReportView>. The current handler recognizes the canonical validation/not-found service codes. Authentication and principal-resolution implementation are preserved. The no-controller statements in earlier historical data-only steps describe their original scope, not the merged application's current state.

V1 remains historical description; restored V2 renames it to note unchanged from the data branch. New report SQL, JPA, HTTP DTOs, demo statements and active fixtures use note. Historical V1 fixtures intentionally retain description. No Step 4 work or shared/live database migration/reset was performed.

---

# Step 3 implemented contract - 31 October 2026

This section is the current service/DTO contract. Earlier Step 1/2 sections below are historical baseline and target context. No DDL or migration was added in Step 3; V1/V2 remain unchanged. Java 25, Boot 3.5.7, PostgreSQL, JdbcTemplate and JPA validation remain in place. DTOs are records separate from JPA entities. Report.reportType now maps ReportType with EnumType.STRING.

All actorId arguments below are **private trusted-principal inputs**, never client JSON, headers purporting to identify a user, or authentication credentials. Every service checks the stored user/role. Esther owns HTTP parsing/security, controllers and exception mapping; no paths or OpenAPI are defined here.

```java
List<WardView> WardApi.listWards(UUID actorId)
List<StreetView> WardApi.listStreets(UUID actorId, UUID wardId)
CurrentUserView UserApi.getCurrentUser(UUID actorId)
CurrentUserView UserApi.updateMyPreferences(UUID actorId, UUID wardId, UUID streetId)
ReportView ReportApi.createReport(UUID actorId, CreateReportRequest request)
ReportView ReportApi.getReport(UUID actorId, UUID reportId)
Page<ReportView> ReportApi.listReports(UUID actorId, UUID wardId, UUID streetId,
    ReportType reportType, FloodStatus floodStatus, Integer page, Integer size, ReportSort sort)
PhotoView PhotoApi.getPhoto(UUID actorId, UUID photoId)
```

ReportApi.create/get/list no longer exist; every runtime caller uses canonical methods. The existing authorityReports(actorId,limit,offset) read remains temporarily with its AUTHORITY guard and original valid role assertions until Step 6 supplies the authority module. It is not an alias for a renamed method and is not part of Esther's canonical MVP integration target. Internal requireConfirmable is still confirmation collaboration, not HTTP. ConfirmationApi.confirm, its duplicate rejection/lifecycle eligibility and legacy error codes are unchanged until Step 5. No authority metrics, hazards, dated collection changes, idempotency or upload flow were added.

| Type | Exact Java members |
| --- | --- |
| CreateReportRequest | ReportType reportType, UUID streetId, String note, UUID photoId, Location location, LocalDate collectionDate |
| ReportView | UUID reportId, ReportType reportType, UUID wardId, UUID streetId, String streetName, String note, String photoUrl, Instant createdAt, LocalDate collectionDate, long confirmationCount, boolean confirmedByMe, Instant lastEvidenceAt, FloodStatus floodStatus, Instant expiresAt, Location mapLocation, String locationPrecision |
| Page<T> | List<T> items (immutable copy), int page, int size, long totalElements, int totalPages |
| CurrentUserView | UUID userId, String email, String displayName, Role role, UUID wardId, UUID streetId (only the current user's own details) |
| WardView | UUID wardId, String wardName |
| StreetView | UUID streetId, UUID wardId, String streetName, Location mapLocation |
| PhotoView | UUID photoId, String photoUrl, String contentType, long sizeBytes, Instant createdAt |
| Location | Double latitude, Double longitude |

Enums: ReportType = MISSED_PICKUP, ILLEGAL_DUMPING, BLOCKED_DRAIN, FLOODED_ROAD; FloodStatus = CURRENT, EXPIRED; ReportSort = CONFIRMATIONS, NEWEST; Role = RESIDENT, OPERATOR, AUTHORITY. locationPrecision is always STREET. UUIDs become strings at HTTP boundaries; Instant values use UTC; LocalDate is the Africa/Lagos calendar date.

Resident setup: WardApi reads require a stored user and order names then UUIDs. listStreets requires an existing ward and returns only its streets. CurrentUserView has no password/hash. updateMyPreferences requires RESIDENT and both non-null IDs matching one stored street/ward, then updates only that actor. Existing unset preferences remain null; there is no automatic backfill or clearing flow. Preferences select a resident's default location; they are not a rule forbidding reports in another ward. createReport derives ward from the selected stored street, with no wardId in the request.

Creation validates resident existence/role, trimmed nonblank 1-1000 Unicode-character note, known street, owned existing photo if supplied, and an optional complete finite coordinate pair within geographic ranges. collectionDate is required exactly for MISSED_PICKUP, forbidden otherwise; elapsed dated-schedule validation and its FK remain Step 4. Reporter and both initial timestamps are server-derived. Clock is injected, defaults to UTC, and operation instants are truncated to PostgreSQL microsecond precision to keep persisted timestamps and filter/view expiry boundaries aligned. Ownership is checked even if someone else's photo is already publicly attached. Invalid input fails before report insertion.

Privacy/projections: report reads select only projection fields, not submitted coordinates/reporter identity/lifecycle state. mapLocation is a nullable street coordinate pair; submitted precision is never returned. reporterId, reporter name/email, status and updatedAt are absent from ReportView. Counts come from real confirmations rows. confirmedByMe is row existence for the current actor. lastEvidenceAt is greatest(report.created_at, max(confirmations.created_at)); non-flood views also receive their true evidence time, with null floodStatus/expiresAt. Flood expiry is exactly lastEvidenceAt + 6 hours; CURRENT iff operation time is strictly before expiry. GET/list never write evidence. Freshness configuration and reconfirmation are Step 5.

Photo persistence remains Step 2's trusted sanitised asset reference. PhotoApi returns metadata only to its owner or a stored user reading an attached report's public evidence, never ownerId/storageKey. ReportService uses photo.api only for view retrieval. An optional photo.api.PhotoUrlResolver bean, supplied by Esther's trusted storage adapter, resolves storageKey internally to a retrievable sanitised photoUrl. Without it, or if it returns null, photoUrl remains null; no key is exposed and no URL is fabricated. There is no default asset, provider dependency, upload, metadata processing or sanitisation claim. Test URLs/references are explicitly synthetic and never seeded into demo data.

Filtering: listReports requires an existing explicit wardId; every count and item query is scoped to it. Optional streetId must belong to that ward; invalid/cross-ward filters are rejected. Optional reportType filters exactly; floodStatus requires FLOODED_ROAD and is evaluated with the same evidence and operation timestamp as the views. page defaults 0; size defaults 20 and must be 1-100; page must be nonnegative. Large offsets use long multiplication. Empty/out-of-range pages retain matching totals; no results means totalPages=0. Count and items share a REPEATABLE_READ snapshot. getReport is an authenticated report-by-ID read, not implicitly restricted to a stored home ward.

The full-team plan named by build.md is not present in the repository or supplied ZIP. The explicit implementation choice is default CONFIRMATIONS (count DESC, createdAt DESC, reportId ASC); NEWEST is createdAt DESC, reportId ASC. UUID ordering makes ties stable across pages. This is a documented assumption, not a quotation from an unavailable plan; reconcile with Esther if that source is supplied later.

New operations use VALIDATION_FAILED, RESOURCE_NOT_FOUND and FORBIDDEN RuleViolation codes; malformed enum/UUID/JSON translation and UNAUTHENTICATED belong to Esther's layer. Private principal input never comes from the request record. Existing confirmation/collection codes are not silently renamed in Step 3.

Existing demo.sql already writes canonical note and remains compatible; it was not expanded or rewritten in this step, and existing disabled login/opt-in protections are unchanged. Existing callers and tests were updated in place; the valid authority-role, self-confirmation, role, location, concurrency and uniqueness checks remain. New tests exercise privacy, real PostgreSQL queries, deterministic Clock evidence and adapter availability. See engineer-a-verification.md for actual results.

---

# Step 2 implementation status - 31 October 2026

V2__report_and_location_contract.sql is now the forward migration after untouched V1. The Step 1 inventory below remains a labelled historical baseline and canonical target, not the current schema inventory. Step 3 APIs/views are not implemented.

Implemented storage mappings: reports.description -> reports.note / Report.note (String), varchar(1000); report_type accepts all four canonical values. Reports have nullable photo_id / photoId (UUID), latitude / latitude and longitude / longitude (double precision / nullable Double), collection_date / collectionDate (LocalDate). Streets use the same nullable double precision / Double public coordinate pair. Both coordinate constraints require either two nulls or two values in latitude [-90,90], longitude [-180,180]; NaN and infinities fail. MISSED_PICKUP requires collection_date, other types forbid it. Schedule-date FK remains Step 4.

UserAccount adds nullable ward_id / wardId and street_id / streetId (UUID): both null or both set; ward FK and composite street/ward FK preserve consistency. No preferences are inferred or backfilled.

photos / photo.domain.Photo maps id / id UUID, owner_id / ownerId UUID FK users, storage_key / storageKey String (text, nonblank), content_type / contentType String (varchar(100), nonblank), size_bytes / sizeBytes long (bigint, positive), created_at / createdAt Instant (timestamptz, default now()). All photo fields are required. There is no upload adapter, provider integration, MIME allowlist/size policy, fake demo asset or public storage-key response. Esther supplies a genuinely sanitised asset reference; report ownership enforcement is Step 3.

V2 checks existing description values before DDL: SQL character length 1-1000, nonblank and equal to btrim(description). Incompatible rows fail with an explicit preflight message. No automatic trim/truncation, data deletion, UUID regeneration, lifecycle update or timestamp rewrite. New ReportService writes trim input and count Unicode code points (matching PostgreSQL character lengths), then enforce 1-1000. Existing ReportApi signatures and ReportView.description remain deliberately unchanged until Step 3; SQL maps that old view member from note. Existing role/location checks stay intact. Legacy lifecycle columns/trigger, confirmations, pickups and weekly schedules are retained.

Demo SQL and existing seed assertions now use note; seed volume and UUIDs are unchanged. ReportMigrationTest covers populated-V1 and fresh-schema paths in disposable PostgreSQL, JPA validation after upgrade, unchanged contents of every legacy table, constraints and transactional preflight rejection. PostgresRulesTest verifies the empty migrated application context, existing rules/seed repeatability and note boundaries. See engineer-a-verification.md for actual commands/results.

---

# EkoWatch data contract — Step 1 baseline

Source of truth: repository `build.md`, 31 October 2026. This document records its single canonical target and the inspected V1 implementation; target entries are not claims of implementation. No schema or application behaviour changes in Step 1. Java 25, Spring Boot 3.5.7, PostgreSQL, Flyway, transactional JdbcTemplate services and Hibernate ddl-auto=validate remain required. DTOs are separate from JPA entities; cross-module dependencies use api packages only.

## Storage and Java mappings

Notation: `column -> member: Java type`; UUID maps to PostgreSQL uuid, Instant to timestamptz, LocalDate to date, String to varchar/text, Short to smallint and Boolean to boolean. All eight existing entities use scalar IDs rather than entity associations. Unless explicitly changed below, existing columns/members remain.

| V1 table / entity and exact members | Canonical target / compatibility |
| --- | --- |
| users / UserAccount: id -> id: UUID; email -> email: String; password_hash -> passwordHash: String; display_name -> displayName: String; role -> role: String; created_at -> createdAt: Instant | Add ward_id -> wardId: UUID and street_id -> streetId: UUID, nullable together, composite street/ward FK. Role values RESIDENT, OPERATOR, AUTHORITY; target typed Role. Never expose passwordHash. |
| wards / Ward: id -> id: UUID; name -> name: String; created_at -> createdAt: Instant | Unchanged. |
| streets / Street: id -> id: UUID; ward_id -> wardId: UUID; name -> name: String | Add nullable latitude -> latitude and longitude -> longitude, public street coordinates. |
| operator_wards / OperatorWard: operator_id -> operatorId: UUID; ward_id -> wardId: UUID | Retain composite key (OperatorWard.Key implements Serializable with equals/hashCode). Multiple operators per ward are currently permitted. |
| reports / Report: id -> id: UUID; reporter_id -> reporterId: UUID; ward_id -> wardId: UUID; street_id -> streetId: UUID; report_type -> reportType: String; description -> description: String; status -> status: String; created_at -> createdAt: Instant; updated_at -> updatedAt: Instant | description becomes note -> note: String, trimmed 1–1000 for new writes; reportType becomes ReportType. Add photo_id -> photoId: UUID, latitude -> latitude, longitude -> longitude (private submitted coordinates), collection_date -> collectionDate: LocalDate. Preserve status/updated_at and lifecycle history privately, no public transitions. reporterId is private. |
| confirmations / Confirmation: id -> id: UUID; report_id -> reportId: UUID; user_id -> userId: UUID; created_at -> createdAt: Instant | report_confirmations / ReportConfirmation: id -> id: UUID; report_id -> reportId: UUID; resident_id -> residentId: UUID; confirmed_at -> confirmedAt: Instant. Preserve UUIDs/timestamps. |
| collection_schedules / CollectionSchedule: id -> id: UUID; street_id -> streetId: UUID; scheduled_day -> scheduledDay: Short; active -> active: Boolean | Retain weekly rows under an explicit legacy table name in Step 4 (build.md does not prescribe its spelling). Current collection_schedules / CollectionSchedule becomes id -> id: UUID; street_id -> streetId: UUID; operator_id -> operatorId: UUID; collection_date -> collectionDate: LocalDate; created_at -> createdAt: Instant. |
| pickups / Pickup: id -> id: UUID; street_id -> streetId: UUID; pickup_date -> pickupDate: LocalDate; status -> status: String; recorded_by -> recordedBy: UUID; created_at -> createdAt: Instant | Retain original attempts under an explicit legacy table name in Step 4. collections / CollectionRecord: id -> id: UUID; street_id -> streetId: UUID; collection_date -> collectionDate: LocalDate; completed_by -> completedBy: UUID; completed_at -> completedAt: Instant. Only COMPLETED attempts backfill; no persisted FAILED or derived status in collections. |
| Absent | photos: id -> id: UUID; owner_id -> ownerId: UUID; storage_key -> storageKey: String; content_type -> contentType: String; size_bytes -> sizeBytes: long; created_at -> createdAt: Instant. Storage reference is trusted sanitised input from Esther's adapter. |
| Absent | idempotency_records: user_id -> userId: UUID; operation -> operation: String; idempotency_key -> idempotencyKey: UUID; canonical request hash; report_id -> reportId: UUID; original response payload; created_at -> createdAt: Instant; expires_at -> expiresAt: Instant. build.md does not name the hash/payload SQL columns or prescribe their Java/storage types; resolve explicitly in Step 7 rather than inventing canonical names here. |

build.md does not fix coordinate SQL/Java numeric types, a photo entity class name, or location value-type names. These remain implementation details to settle in their owning step; coordinates require complete pairs and geographic range validation. No unprovided name is presented as canonical.

V1 constraints: users email varchar(254), lowercase and unique; password_hash varchar(255), display_name varchar(100), role varchar(20) checked. wards name varchar(100) unique. streets name varchar(150), unique (ward_id,name) and (id,ward_id). operator_wards PK (operator_id,ward_id). Weekly scheduled_day is 1–7, unique (street_id,scheduled_day), active defaults true. pickups unique uq_pickup_street_date(street_id,pickup_date), checked COMPLETED/FAILED. reports type only BLOCKED_DRAIN, description varchar(2000) nonblank, status OPEN/RESOLVED/REJECTED default OPEN; composite street/ward FK. confirmations unique uq_confirmation_report_user(report_id,user_id). IDs are PKs; stored identity references are FKs. No cascading deletes. Timestamp defaults are now(); reports_updated_at invokes touch_report_updated_at on update.

V1 explicit indexes: ix_streets_ward, ix_operator_wards_ward, ix_reports_ward_created (created_at DESC), ix_reports_street, ix_reports_reporter, ix_confirmations_user, ix_pickups_recorder. Uniqueness supplies additional indexes. Roles are service checks, not cross-table DB role constraints.

Target named uniqueness: uq_report_confirmation_resident(report_id,resident_id), uq_collection_street_date(street_id,collection_date), uq_schedule_street_date(street_id,collection_date). collections references dated schedule (street_id,collection_date); MISSED_PICKUP reports reference an elapsed dated occurrence after Step 4. collectionDate is required only for MISSED_PICKUP and null for other types. Photo ownership and ward/street consistency remain enforced.

## Service inventory and canonical target

All actorId arguments (including today's authorityId) are **private internal inputs** from Esther's trusted principal, never client JSON or arbitrary headers. Services still validate stored role and ward assignment. Public module API means callable between modules, not automatically an HTTP endpoint.

| Current exact signature | Target method / result |
| --- | --- |
| ReportApi.create(UUID actorId, UUID wardId, UUID streetId, String description): ReportView | createReport: ReportView, takes internal actorId, CreateReportRequest and, in Step 7, typed UUID idempotency key; derives ward from street and reporter from principal. |
| ReportApi.get(UUID actorId, UUID reportId): ReportView | getReport: ReportView. |
| ReportApi.list(UUID actorId, int limit, int offset): List<ReportView> | listReports: Page<ReportView>, ward scoped, optional street/type/floodStatus, page/size default 0/20, size 1–100, deterministic CONFIRMATIONS or NEWEST ordering. floodStatus filter requires FLOODED_ROAD; reject cross-ward street. |
| ReportApi.authorityReports(UUID actorId, int limit, int offset): List<ReportView> | Superseded by AuthorityApi.getWardDashboard; no old alias in final contract. |
| ReportApi.requireConfirmable(UUID reportId, UUID actorId): void | Internal report.api collaboration for confirmation eligibility/locking; never client exposed. Lock consistently before evidence writes. |
| ConfirmationApi.confirm(UUID actorId, UUID reportId): void | confirmReport: authoritative ReportView; resident, no self-confirmation; repeat-safe, unique people, expired-flood reconfirmation only updates confirmedAt. |
| CollectionApi.recordPickup(UUID actorId, UUID streetId, LocalDate date, String status): void | recordCollection: CollectionView; operator plus ward assignment, scheduled date, no future date; duplicate returns original completion/time; no status request. |
| CollectionApi.missed(UUID authorityId, LocalDate from, LocalDate through): List<MissedPickup>; MissedPickup(UUID streetId, LocalDate date) | Replaced by listCollectionSchedules: collection schedule views, listMyAssignedStreets: street views, and authority dashboard. Inclusive ranges maximum 31 days. |
| ActorAccess.requireRole(UUID actorId, String role): void; requireUser(UUID actorId): void | Retained internal role/user collaboration; not a response or login API. |
| Absent | WardApi.listWards / listStreets: WardView / StreetView collections. UserApi.getCurrentUser / updateMyPreferences: CurrentUserView; resident preferences must pair matching ward/street. |
| Absent | ReportApi.listWardHazards: WardHazardsView. AuthorityApi.getWardDashboard: WardDashboardView, AUTHORITY only. PhotoApi.getPhoto: PhotoView, ownership rules. |

Only current signatures above are compiled today. build.md fixes target names and semantics, not every parameter order, list container, numeric count width or auxiliary view member. Such details must be agreed in their implementation step, without adding aliases or claiming an existing signature. Empty dashboard scaffold becomes authority in Step 6; photo is new in Step 2. No other module's domain/service/repository imports.

## Request and response field mappings

UUID values are UUID internally and strings at HTTP boundaries. Instants are UTC, dates are LocalDate using Africa/Lagos. Counts are integral (current confirmationCount is long). Enums: ReportType = MISSED_PICKUP, ILLEGAL_DUMPING, BLOCKED_DRAIN, FLOODED_ROAD; FloodStatus = CURRENT, EXPIRED; CollectionStatus = SCHEDULED, DONE, MISSED, DISPUTED. locationPrecision is always STREET. No old lifecycle status in public MVP views.

CreateReportRequest: reportType (ReportType), streetId (UUID), note (String), optional photoId (UUID), location (private coordinate pair), collectionDate (LocalDate). No actorId, reporterId or client wardId. Idempotency key is a separate internal UUID argument mapped by Esther from Idempotency-Key, not JSON. Notes trimmed 1–1000.

| Full canonical ReportView member | Source / type / compatibility |
| --- | --- |
| reportId | reports.id / UUID; replaces current id |
| reportType | reports.report_type / ReportType; replaces String |
| wardId | reports.ward_id / UUID |
| streetId | reports.street_id / UUID |
| streetName | streets.name / String; new join |
| note | reports.note / String; replaces description |
| photoUrl | trusted photo retrieval reference / nullable String; never expose storageKey directly |
| createdAt | reports.created_at / Instant |
| collectionDate | reports.collection_date / nullable LocalDate |
| confirmationCount | actual report_confirmations count / integral; never cached counter |
| confirmedByMe | resident row existence for internal actor / boolean |
| lastEvidenceAt | max(report.createdAt, confirmation timestamps) / Instant |
| floodStatus | nullable FloodStatus, only FLOODED_ROAD |
| expiresAt | nullable Instant, lastEvidenceAt + freshness hours (default 6), flood only |
| mapLocation | nullable street coordinate pair; NEVER submitted report coordinates |
| locationPrecision | STREET |

Remove current reporterId, status and updatedAt from ReportView. Never expose reporter name/email or precise submitted coordinates. CURRENT iff server time strictly before expiresAt, otherwise EXPIRED. Reads never refresh evidence. Reconfirming an expired flood is fresh testimony, not perpetual replay.

| Canonical view | Exact fields from build.md, source/type |
| --- | --- |
| CollectionScheduleView | scheduleId (collection_schedules.id UUID), wardId (streets.ward_id UUID), streetId (street_id UUID), streetName (streets.name String), collectionDate (collection_date LocalDate), collectionStatus (derived CollectionStatus), completedAt (collections.completed_at nullable Instant), missedReportCount (matching street/date MISSED_PICKUP report count). |
| CollectionView | collectionId (collections.id UUID), streetId (street_id UUID), collectionDate (collection_date LocalDate), completedAt (completed_at nullable Instant when no completion), collectionStatus (derived CollectionStatus). build.md does not specify collectionId nullability for an uncompleted occurrence; settle when implementing the view. |
| WardDashboardView | wardId (UUID), fromDate (LocalDate), toDate (LocalDate), generatedAt (Instant), missedPickupCount (integral), disputedPickupCount (integral), blockedDrainCount (integral), currentFloodCount (integral), newestReportAt (nullable Instant), operatorPerformance (OperatorPerformanceView collection). |
| OperatorPerformanceView | operatorId (schedule operator_id UUID), operatorName (users.display_name String), scheduledPickupCount, completedPickupCount, missedPickupCount, disputedPickupCount (all integral). |
| WardHazardsView | CURRENT flood and BLOCKED_DRAIN report views, generatedAt (Instant), nullable newestReportAt (Instant). The full-team plan specifies items (`List<ReportView>`) as the report-list member; no reports alias exists. No silent truncation of one ward. |
| Page<T> | items (T collection), page (int), size (int), totalElements (integral), totalPages (int). |
| CurrentUserView, WardView, StreetView, PhotoView | Required exact type names; build.md does not enumerate full fields. Bind only required stored preference/ward/street and authorised photo information in their owning steps. Never expose passwordHash, raw storage keys, or private reporter identity. |

DISPUTED means a completion and MISSED_PICKUP report for the exact street/date; preserve both. Unelapsed uncompleted occurrence is SCHEDULED, elapsed uncompleted is MISSED, completion is DONE unless disputed. Authority missedPickupCount excludes disputes; completedPickupCount includes disputed completions. Performance uses operator_id on the dated schedule, never an arbitrary current ward assignee. Pickup metrics use inclusive requested dates (max 31 days); hazard counts are report counts at generatedAt, with expired floods excluded. Normal report/schedule/hazard views never expose operator performance.

Canonical RuleViolation codes for Esther: VALIDATION_FAILED, UNAUTHENTICATED, FORBIDDEN, RESOURCE_NOT_FOUND, WARD_ASSIGNMENT_REQUIRED, SELF_CONFIRMATION_NOT_ALLOWED, IDEMPOTENCY_KEY_REQUIRED, IDEMPOTENCY_KEY_REUSED, PHOTO_TOO_LARGE, UNSUPPORTED_PHOTO_TYPE, INTERNAL_ERROR. Existing noncanonical codes are current implementation only; HTTP exception mapping belongs to Esther. No paths/controllers/OpenAPI specified here.

## Current SQL, tests and fixture evidence

ReportService inserts BLOCKED_DRAIN with trimmed description after resident and explicit street/ward checks (raw input length <=2000). SELECT r.* plus correlated count(confirmations) exposes reporterId and lifecycle fields. Reads require a stored user, are unscoped across wards, and sort created_at DESC,id with limit/offset; authorityReports adds a role check. requireConfirmable selects reporter_id/status FOR SHARE, rejects non-OPEN/self.

ConfirmationService uses READ COMMITTED, ReportApi eligibility lock and INSERT ON CONFLICT ON CONSTRAINT uq_confirmation_report_user DO NOTHING; zero inserts throws DUPLICATE_CONFIRMATION. CollectionService checks OPERATOR and operator_wards via streets; inserts COMPLETED or FAILED with uq_pickup_street_date conflict rejection, without requiring a schedule. missed uses generate_series and ISO weekday, active schedules, no COMPLETED attempt, dates before Lagos today, at most 367 inclusive days. DatabaseActorAccess queries users existence/role. No clock injection, photo persistence, freshness, dashboard, idempotency or canonical pagination exists.

PostgresRulesTest has eight tests: distinct/same resident races, isolated temporary-table constraint demonstration, eligibility/authority rules, pickups/missed, seed repeatability, committed-row visibility and FAILED still missed. Uses postgres:17-alpine with dynamic Testcontainers datasource, and truncates only that disposable database. These assertions describe V1, not canonical completion/reconfirmation behaviour. Later steps must update obsolete expectations while preserving valid uniqueness, roles and history assertions. Temporary LIKE-table demonstration is not proof of the final real-table mutation invariant (reserved for Step 9).

ModuleBoundaryTest has one test covering auth/user/ward/collection/report/confirmation/dashboard; authority/photo are not yet included. RequestIdFilterTest has two tests for valid/invalid IDs and MDC restoration. Test-only Docker API version is 1.44. No populated-V1 upgrade test exists yet; Step 2 must add it and empty migration/JPA validation evidence.

DemoSeed requires local-demo AND ekowatch.demo.enabled=true and excludes prod/production. demo.sql uses fixed UUIDs and insert-only ON CONFLICT DO NOTHING: five users (three residents, one operator, one authority) with !DEMO_LOGIN_DISABLED; one ward/street, one ward operator assignment, seven active weekly days, three short BLOCKED_DRAIN reports and three confirmation rows producing 0/1/2 counts. No pickups, long notes, multiple operators, photos, dated schedules or other report types. Timestamps default to now() on first insert, preserved on repeat; no fixed presentation anchor. Step 8 expansion is pending. No fixtures need code changes for this documentation-only step.

## Current-versus-target migration prerequisites

Only V1 exists. Preserve it byte-for-byte; Step 2 is V2__report_and_location_contract.sql only if still unused. No DDL or backfill in Step 1. No shared/live database inspected. Static fixture inspection is not evidence that real legacy data has no hazards.

Run later preflight on an authorised isolated copy of the populated V1 data before migration, without logging notes, identities, coordinates or secrets. Record affected row IDs/counts privately for explicit resolution:

1. Check char_length(description)>1000 and blank/trim compatibility; V1 permits 2000. Do not truncate, discard or silently rewrite incompatible evidence. Require explicit approved mapping/remediation and fail clearly until resolved.
2. Inventory pickups by status and street/date; preserve every FAILED attempt in legacy storage and all IDs/times. Only COMPLETED rows map to collections with original IDs, pickup_date -> collection_date, recorded_by -> completed_by, created_at -> completed_at. Verify user role and ward assignment for historical operators; current assignment is not proof of historical truth. Missing/invalid assignments require explicit resolution.
3. Inventory active/inactive weekly rows, weekday validity, duplicates and historical completion dates. Define explicit legacy table names and cutover boundary/date, Lagos timezone and a documented 30-day horizon. Expand active weekly patterns only within that horizon; historical completions need prerequisite occurrences for their exact dates, not invented arbitrary history. Preserve inactive weekly history too.
4. Group operator_wards by ward and count candidates, verifying OPERATOR roles; zero/multiple eligible operators require explicit street/date/operator mapping for new occurrences. Never choose the first UUID. Conflicts between weekly expansion and historical recorded operators must fail preflight until explicitly mapped.
5. Check orphan/composite-FK integrity, duplicate confirmation and collection/schedule keys, report lifecycle history, existing notes, timestamp/UUID preservation, and target coordinate/preference/photo/date constraints. Define how legacy BLOCKED_DRAIN data maps without inventing MISSED_PICKUP dates. Reject unresolved target FK/unique conflicts before writes.
6. Prove empty and populated migration paths on disposable PostgreSQL: row counts, stable UUIDs, original timestamps/history, invalid-input rejection, Flyway checksums and Hibernate validation. Remove legacy JPA mappings/queries only with the Step 4 switch; retain legacy records. Verify all current callers/tests/seed SQL track each later rename.

Ambiguous data needs an explicit mapping and must not be guessed, truncated or discarded. Backups and a reviewed recovery plan precede any eventual authorised real migration; Flyway Community does not automatically undo it. This step neither connects to nor resets a database.
