# Step 9 final verification - 31 October 2026

Only Step 9 indexes, evidence, operation/outcome logging and final documentation were added. All pre-existing Step 7/8 working-tree changes and ekobridge-project.zip were preserved. No frontend, controller, login, refresh token, optional workflow, deployed/shared database access, .env value output, commit, push or deployment. UserAccount was reviewed and left unchanged; entities remain separate from response DTOs.

## Actual commands and results

All Maven runs used approved access to the existing dependency cache and Docker. Database evidence is exclusively from test-owned disposable postgres:17-alpine / PostgreSQL 17.11 containers with dynamic loopback ports and cleanup. Java 25.0.4.1, Spring Boot 3.5.7, Testcontainers 1.21.3 and Docker 29.8.0 remain unchanged.

1. `mvn -B '-Dtest=InvariantEvidenceTest,ReportMigrationTest' test`: **exit 0, BUILD SUCCESS, 7 tests, 0 failures/errors/skips**. InvariantEvidenceTest 3 and ReportMigrationTest 4. Elapsed 03:45 min; finished 2026-10-31T20:02:29+01:00. Normal actual real-table uniqueness, authority refusal, logging privacy and populated V1–V6 preservation all passed. Log: target/step9-baseline.log.
2. `mvn -B '-Dtest=InvariantEvidenceTest#oneResidentCountsOnceInTheRealCanonicalTable' '-Dekowatch.evidence.mutation=confirmation' test`: **exit 1, expected BUILD FAILURE, 1 test / 1 assertion failure / 0 errors / 0 skipped**. Elapsed 01:44 min; finished 20:04:52 +01:00. Actual `EVIDENCE invariant=oneResidentCountsOnce mutation=confirmation actualRows=2`; unchanged business assertion: `expected: <1> but was: <2>`. Retained exact excerpts: [confirmation mutation](evidence/step9-confirmation-mutation.txt). Full generated log: target/step9-confirmation-mutation.log.
3. `mvn -B '-Dtest=InvariantEvidenceTest#performanceQueriesRefuseResidentRole' '-Dekowatch.evidence.mutation=authority' test`: **exit 1, expected BUILD FAILURE, 1 test / 1 assertion failure / 0 errors / 0 skipped**. Elapsed 56.123 seconds; finished 20:06:07 +01:00. Actual unchanged refusal assertion: `Expected com.tesa.ekowatch.shared.RuleViolation to be thrown, but nothing was thrown`. Retained exact excerpts: [authority mutation](evidence/step9-authority-mutation.txt). Full generated log: target/step9-authority-mutation.log.
4. `mvn -B verify`: **exit 0, BUILD SUCCESS, 95 tests, 0 failures, 0 errors, 0 skipped**. Elapsed 07:34 min; finished 2026-10-31T20:14:20+01:00. Recompiled 96 production and 22 test source files at release 25, packaged and Spring Boot repackaged the JAR. This is the requested full verify, not a clean-build claim. All mutations defaulted to none. Log: target/step9-verify.log; generated Surefire reports: target/surefire-reports. Retained [protected-run excerpts](evidence/step9-protected-invariants.txt) include `actualRows=1` and `outcome=FORBIDDEN` from the same invariant methods.
5. `git diff --check`: exit 0. SHA-256 comparison of the five pre-existing migration files before/after Step 9: all unchanged, including the untracked pending Step 7 V5. [Hashes](evidence/step9-migration-hashes.txt) also identify the new V6. No applied migration was edited.

Expected negative preflight logs are passing rollback assertions. Nonfatal native-access/Mockito dynamic-agent warnings and cached Hikari connection warnings after previous test containers stopped occurred. They did not cause test failures. No unavailable tool/dependency blocker or hidden skipped PostgreSQL test is reported. No HTTP or deployed-environment success is inferred from these results.

## What the evidence proves

The old temporary LIKE-table demonstration is removed. InvariantEvidenceTest runs two concurrent plain INSERT attempts on actual report_confirmations using independent auto-commit JDBC connections. A duplicate SQLSTATE 23505 is handled only after that independent attempt; there is no named ON CONFLICT target or SQL continuation in an aborted transaction. The same final persisted-row assertion expects exactly one resident row and the public confirmation count of one. Normal protection passes. The explicit confirmation mutation drops uq_report_confirmation_resident only in its own disposable migrated database; both INSERTs then succeed and the business row-count assertion fails at two. This is not a missing-constraint syntax failure masquerading as invariant evidence.

The authority assertion calls the real AuthorityApi/AuthorityService, first proving authority success and then requiring resident FORBIDDEN. Its test-only ActorAccess decorator normally delegates to the real database role checks. In the explicit authority mutation it omits only AUTHORITY enforcement; the real service and dashboard SQL execute and the same resident refusal assertion fails because data was returned. No production source switch or migration edit is used. These are service-level mutation experiments; Esther still owes HTTP wrong-role evidence. The opt-in negative commands are separate from normal verify; each container is destroyed afterward, so no restoration against shared data is involved.

The full suite migrates empty PostgreSQL through V1–V6 and initializes Hibernate ddl-auto=validate. ReportMigrationTest upgrades a populated V1 fixture with every original table represented, validates V2 normalization, then applies V3–V6 and compares every legacy/current original value after canonical name normalization: UUIDs, notes/status/history, timestamps, weekly rows, attempts and confirmation residents/times. The COMPLETED collection retains its original UUID/time, FAILED remains legacy history, no past idempotency records are fabricated, and a rerun executes zero migrations. Existing preflight tests cover incompatible notes, ambiguous operators and mapping conflicts without truncation/discard. V5 idempotency preservation, current constraints, all API/privacy/role rules, seed repeatability, expiry/disputes and module boundaries also pass. Existing canonical request/view allowlists and the separate items hazard contract remain intact.

ReadProjectionPlanTest migrates a fresh V5 fixture then V6, compares all rows across 13 current/legacy/audit tables, verifies all five indexes and zero-migration rerun, and captures normal PostgreSQL plans before/after on actual production SQL blocks. Real row counts, SQL, fictional parameters, buffers/estimates and observed timings are retained in [query plans](evidence/step9-query-plans.txt); interpretation/limits are in [query-plans.md](query-plans.md). Counts are 9 reports, 5 confirmations, 172 schedules, 3 completions, 8 streets/users. Actual returned rows before/after are 3 report-list items, 5 hazards, 172 schedules, 4 assigned streets, 2 operator aggregates and 1 hazard aggregate. The planner chose sequential scans on these tiny relations, also after V6. No index selection/speedup, larger workload, load capacity or invented timing is claimed. The plan test does not measure HTTP/photo-resolver/frontend overhead.

OperationLog adds fixed operation/outcome context for createReport, confirmReport, recordCollection and getWardDashboard. Real transaction completion produces committed/rolled_back/unknown, rather than calling an intermediate return a commit. The nontransactional fallback explicitly uses returned/rejected/failed. The logging test captures real committed/refused authority calls, proves requestId remains attached/restored and excludes actor/report UUIDs, note/coordinate/hash sentinels and exception text. Existing RequestIdFilter behaviour tests still pass. No payload, private location, identity or credentials are logged by these new events.

## Migration and handoff implications

V6__read_projection_indexes.sql is additive DDL only: ward/type/newest report ordering, partial exact street/date missed-report lookup, schedule date/street and operator/date/street reads, and latest confirmation evidence. No row/UUID/history backfill or stored counters. Index build/write/storage costs and transactional write blocking are documented; measure the intended workload before claiming capacity. Recovery guidance explicitly requires backup/rehearsal, inspection and reviewed forward recovery or restore, not automatic undo or blind Flyway repair/history deletion. Flyway Community does not automatically undo migrations. Legacy FAILED/weekly/cutover and explicit-mapping limitations remain documented.

Step 9 files: new V6__read_projection_indexes.sql; new shared/OperationLog.java; logging wrappers in ReportService, ConfirmationService, CollectionService and AuthorityService; PostgresRulesTest (old temporary demonstration removed); ReportMigrationTest (full-chain stable data/history checks); new InvariantEvidenceTest and ReadProjectionPlanTest; docs/data-contract.md, docs/engineer-a-handoff.md, docs/engineer-a-verification.md, docs/decisions.md; new docs/query-plans.md, docs/adr/001-confirmation-and-flood-freshness.md, docs/adr/002-derived-disputed-pickups.md and docs/evidence/step9-*.txt. Other dirty/untracked Step 7/8 files were retained, not attributed to Step 9. No seed, DTO, controller or UserAccount change was needed in this step.

Esther's exact method signatures, response fields, nullability/enums, canonical errors and principal rules are in the new top section of engineer-a-handoff.md. Her remaining work includes actual HTTP wrong-role/authentication tests, header/UUID/date parsing and canonical error mapping, the currently null controller Idempotency-Key integration, real metadata stripping/verified sanitised photo ownership/storage/URL wiring, privately provisioned encoder-compatible hashes for disabled demo users, final OpenAPI, frontend and deployment/live walkthroughs. No sanitised asset, login, shared/deployed migration or HTTP success was fabricated. Both ADRs state rejected alternatives, accepted costs and reversal observations; Esther's sign-off is explicitly outstanding rather than invented. Historical verification below is retained.

---
# Step 8 verification - 31 October 2026

Implemented only fixed fictional demo data, guard hardening and provisioning handoff. Existing Step 7 working-tree changes and untracked ekobridge-project.zip were preserved. No Step 9 work, shared/live database, automatic reset/truncate, .env output, commit, push or deployment. Existing test cleanup remains confined to disposable Testcontainers; no application database was reset.

Actual commands (approved existing Maven cache/Docker access):

1. `mvn -B '-Dtest=DemoSeedTest,DemoSeedGuardTest,PostgresRulesTest,ReportIdempotencyMigrationTest' test`: exit 0, BUILD SUCCESS; **17 tests, 0 failures, 0 errors, 0 skipped**. DemoSeedGuardTest 2, DemoSeedTest 3, PostgresRulesTest 10, ReportIdempotencyMigrationTest 2. Compiled 95 production and 20 test source files at Java release 25. Elapsed 04:05 min; finished 2026-10-31T19:37:55+01:00. Log: target/step8-targeted.log.
2. `mvn -B '-Dtest=ModuleBoundaryTest' verify`: exit 0, BUILD SUCCESS; **1 test, 0 failures, 0 errors, 0 skipped**. JAR packaging and Spring Boot 3.5.7 repackaging passed. Elapsed 32.433 seconds; finished 2026-10-31T19:38:47+01:00. Log: target/step8-boundary-verify.log.
3. `git diff --check`: exit 0. `git diff --exit-code -- pom.xml src/main/resources/db/migration`: exit 0. No applied migration was edited and the pending Step 7 V5 file was left untouched. No Step 8 DDL or backfill is needed.

These are targeted runs, not a fresh claim of full clean-suite verification. PostgreSQL evidence used only disposable postgres:17-alpine containers (PostgreSQL 17.11), Testcontainers 1.21.3 and Docker 29.8.0. Java 25.0.4.1, Spring Boot 3.5.7, Flyway V1–V5 and Hibernate ddl-auto=validate initialized. Nonfatal JVM/Mockito warnings and cached-pool warnings after prior container teardown occurred. Final checks had no errors/skips; generated Surefire reports are under target/surefire-reports.

DemoSeedTest starts the actual runner through both explicit opt-ins on an empty disposable migrated database. At fixed presentation 2026-10-31T12:00:00Z it asserts: one fictional ward, eight streets, eight disabled-login-marker users (four residents, two assigned operators, one authority, one unassigned operator), matching resident preferences, a >100-character street name, street 8 with no reports, 172 distinct dated occurrences across the 30 consecutive calendar dates 16 October–14 November, every street's exact daily/weekday pattern and dated operator assignment, fixed original seven schedule UUIDs and explicit creation anchors. Nine reports cover all four types; original drains retain 0/1/2 confirmation counts; five confirmations include newer evidence on an older flood. Two floods are CURRENT and one EXPIRED at the anchor. The no-photo/location-denied report has null submitted coordinates and photo reference; no photos or historical idempotency records are invented.

Derived cases are verified through the canonical APIs against actual rows: 86 SCHEDULED, 2 DONE, 83 MISSED, 1 DISPUTED; exact disputed street/date and missed-report count; authority missed/disputed 83/1, drains/floods 3/2, two operator rows with 86 scheduled each and completed 2/1 (including the dispute), correct newest submission and five hazard items. Advancing Clock expires both current floods without changing any stored row. Unassigned completion is refused with WARD_ASSIGNMENT_REQUIRED and resident performance access with FORBIDDEN.

Repeat tests compare every column/row in all ten seed-related current tables before/after unchanged reruns. After changing resident preferences/hash sentinel, editing evidence, renewing confirmation time, recording a completion and creating a report with a real idempotency snapshot, two reruns with a Clock ten days later preserve the entire changed snapshot and original/new UUIDs. No timestamp is renewed, user action reset, credential replaced or record duplicated. Test transactions roll back their own synthetic actions. Guard tests prove registration needs both opt-ins, production/mixed production profiles always exclude the runner, and direct invocation without opt-in or with production cannot touch the datasource. Existing seed repeatability assertion now expects all five confirmations while retaining the original exact 0/1/2 assertion; its date is explicitly the new fixed anchor rather than migration cutover. V4 seed -> V5 migration preservation still passes unchanged.

Step 8 files: src/main/java/com/tesa/ekowatch/shared/DemoSeed.java; src/main/resources/db/demo/demo.sql; src/test/java/com/tesa/ekowatch/PostgresRulesTest.java (seed expectations only, prior Step 7 caller edits preserved); new DemoSeedTest.java and DemoSeedGuardTest.java; new docs/demo-provisioning.md; docs/data-contract.md; docs/engineer-a-handoff.md; this verification document. No unrelated service, HTTP, auth or migration edits were made for Step 8.

Esther handoff: docs/demo-provisioning.md documents deliberate fresh local provisioning, exact fixed IDs/patterns/anchor expectations and a separately reviewed fresh-fixture procedure for a new presentation date without resetting old data. Freshness naturally ages; no claim of CURRENT floods at arbitrary later wall-clock time. Passwords stay !DEMO_LOGIN_DISABLED: SecurityConfig currently defines BCryptPasswordEncoder, but Esther must verify her runtime encoder and privately provision compatible hashes; no account login is claimed. No sanitised asset was provided, so no photo URL/storage metadata is fabricated. Esther must sanitise/verify and deliberately provision a genuine photo reference before demonstrating photos. Deployed-demo provisioning, credentials, photo sanitisation, Step 7 HTTP header/error wiring and live HTTP/frontend walkthroughs remain hers and unverified. Historical results below remain unchanged.

---
# Step 7 verification - 31 October 2026

Implemented report-creation idempotency only. No Step 8 seed expansion or Step 9 work. Existing untracked ekobridge-project.zip remains untouched. No applied migration changes, shared/live database, database reset, .env output, commit, push or deployment. No header parsing, new controllers/routes, login, HTTP error mapping, scheduler or unrelated idempotency framework was added.

Actual commands, using approved access to the existing Maven cache and Docker:

1. `mvn -B '-DskipTests' compile`: exit 0, BUILD SUCCESS; 95 production source files compiled at release 25; 25.873 seconds, finished 19:14:17 +01:00. This compile preceded the final hash-encoding refinement; the clean run below recompiles everything.
2. `mvn -B '-Dtest=ReportIdempotencyTest' test`: exit 0, BUILD SUCCESS; initial 7 tests, 0 failures/errors/skips; 02:00 min, finished 19:17:24 +01:00. After this run, hashing was refined to length-prefixed UTF-8 independent of ObjectMapper settings and the waiting-across-expiry case was added. The final run below covers both. The intermediate target log was removed by clean; this record is historical command evidence, not a claim that the intermediate sources were final.
3. `mvn -B clean verify`: exit 0, BUILD SUCCESS; **87 tests, 0 failures, 0 errors, 0 skipped**. Clean compilation: 95 production and 18 test source files at release 25. Full suite, Hibernate ddl-auto=validate including IdempotencyRecord, Flyway V1–V5, JAR packaging and Spring Boot 3.5.7 repackaging passed. Total time 06:33 min; finished 2026-10-31T19:25:05+01:00. Log: target/step7-clean-verify.log; generated Surefire reports: target/surefire-reports.
4. `git diff --check`: exit 0. `git diff --exit-code -- pom.xml src/main/resources/db/migration/V1__mvp_foundation.sql src/main/resources/db/migration/V2__report_and_location_contract.sql src/main/resources/db/migration/V3__dated_collections.sql src/main/resources/db/migration/V4__report_confirmations.sql src/main/resources/db/demo/demo.sql`: exit 0.

All database evidence used disposable postgres:17-alpine / PostgreSQL 17.11 Testcontainers (dynamic local ports), Docker 29.8.0 and Testcontainers 1.21.3. Java 25.0.4.1 and Spring Boot 3.5.7 are unchanged. Expected negative preflight logs and nonfatal native-access/Mockito/Hikari warnings after stopped prior containers occurred; final tests had no failures, errors or skips. No application datasource was substituted.

New ReportIdempotencyTest: 8 tests passing. Evidence: simultaneous same-user/key canonical payloads return one identical result and one completed record/report; note trimming and signed-zero normalization; stored response round-trip with original microsecond timestamps; every input field including photo/location/date changes the hash; mismatch races produce one success plus IDEMPOTENCY_KEY_REUSED; another user can independently use the same key; lost-response replay remains identical despite later confirmation, expired freshness, street rename/coordinates and photo adapter changes/failure; missing-key/wrong-role/invalid values leave no records; projection and response serialization failures leave neither partial report nor record; rollback after a completed write rolls back both; retry after failure succeeds; failed expired replacement preserves the old row; one microsecond before expiry replays, exact expiry atomically replaces the intent even under concurrent calls, retains the record ID and historical report, and creates one new report. An independently held PostgreSQL transaction advisory lock proves a waiting retry samples expiry after acquiring the intent lock; the fixture always releases its lock before closing workers.

New ReportIdempotencyMigrationTest: 2 tests passing. Fresh V1–V5 migration and zero-migration rerun; real unique violation (23505), required fields (23502), user/report FKs (23503), JSON object/hash/operation/24-hour interval checks (23514). Populated V4 upgrade compares every row in all 12 existing current/legacy/audit tables using the stable insert-only demo fixtures, and proves V5 fabricates no historical records. No existing table constraint was removed or weakened. Existing migration preservation, role/ward/photo/privacy, freshness, collection/dispute, seed-repeatability and boundary assertions remain passing.

V5__report_creation_idempotency.sql is additive: idempotency_records only, with required complete JSON response snapshots and unique user/operation/key scope. No historical report/UUID/time changes or historical key backfill. Expiry ends replay protection at 24 hours; a later reuse creates a new intent and replaces the record atomically while preserving old report rows and the idempotency record ID. Expired records are retained until reused; no cleanup scheduler or automatic deletion promise.

Changed files: new V5 migration; report/domain/IdempotencyRecord.java; report/api/ReportApi.java; report/service/ReportService.java; existing report/web/ReportController.java (signature compatibility only); new ReportIdempotencyTest.java and ReportIdempotencyMigrationTest.java; existing ResidentReportApiTest.java, PostgresRulesTest.java, CollectionRulesTest.java, ConfirmationFreshnessTest.java and report/web/ReportControllerTest.java; docs/data-contract.md, docs/engineer-a-handoff.md and this document. Existing service fixtures now pass explicit UUID keys per independent intent; seed SQL stays unchanged. No valid assertion was weakened. The controller test adds proof that missing-key refusal propagates rather than inventing a key; its prior mock-based assertions remain and are not represented as a real successful create endpoint.

Esther's handoff: `ReportApi.createReport(UUID actorId, UUID idempotencyKey, CreateReportRequest request)` is the only signature. Supply actorId from the trusted principal and parse Idempotency-Key into the separate UUID argument; map IDEMPOTENCY_KEY_REQUIRED and IDEMPOTENCY_KEY_REUSED. The existing controller currently passes null to fail closed until Esther integrates the header; real create calls are refused. No random server-generated retry key, key JSON field or keyless overload exists. Header-integrated HTTP creation and new error status mapping are not claimed as complete. Full contracts and the 24-hour replay-window limitations are documented in data-contract.md and engineer-a-handoff.md. Historical evidence follows.

---
# Step 6 items contract correction - 31 October 2026

Renamed WardHazardsView's list component and accessor from reports/reports() to items/items(), matching the full-team plan. No alias remains. Updated all callers in CollectionRulesTest and the data-contract/handoff documents. Added an exact serialized-field allowlist (wardId, generatedAt, newestReportAt, items) and asserted the 105 returned entries are under items. Existing Step 6 behaviour and valid assertions are preserved.

Actual command: `mvn -B clean verify`, with approved Maven cache/Docker access, exited 0. BUILD SUCCESS; 76 tests, 0 failures, 0 errors, 0 skipped. Clean compilation used Java release 25 for 94 production and 16 test source files. Full suite, disposable PostgreSQL 17.11 Testcontainers, Flyway V1–V4, JPA validation, JAR packaging and Spring Boot 3.5.7 repackaging passed. Total time 07:50 min; finished 2026-10-31T19:08:04+01:00. Expected negative migration-test logs, nonfatal JVM/Mockito warnings and cached-pool warnings after container teardown occurred; no test failures. Actual log: target/step6-contract-verify.log; Surefire reports: target/surefire-reports.

`git diff --check` exited 0. `git diff --exit-code -- src/main/resources/db/migration src/main/resources/db/demo/demo.sql pom.xml` exited 0. No migrations, seeds, Step 7, commit or push. Existing unrelated working-tree changes preserved. No database backfill is needed. Esther must consume items in the hazard response; reports is not a supported alias.

Files changed by this correction: src/main/java/com/tesa/ekowatch/report/api/WardHazardsView.java; src/test/java/com/tesa/ekowatch/CollectionRulesTest.java; docs/data-contract.md; docs/engineer-a-handoff.md; docs/engineer-a-verification.md. Historical evidence follows.

---
# Step 6 verification - 31 October 2026

Implemented only authority/hazard read models and derived disputes. Existing untracked ekobridge-project.zip was preserved. No controllers, authentication, idempotency, seed expansion, background tasks or later steps were added. No shared/live database, reset, .env output, commit, push or deployment was used.

Actual commands/results:

- Initial sandboxed `mvn -B '-Dtest=CollectionRulesTest,PostgresRulesTest,ModuleBoundaryTest' test` could not access C:\.m2\repository: `Access is denied` / `Could not create local repository`. No tests ran on this attempt.
- Approved-access retry of that command: Maven BUILD SUCCESS, 20 tests, 0 failures/errors/skips (CollectionRulesTest 9, ModuleBoundaryTest 1, PostgresRulesTest 10); 04:00 min, finished 18:49:32 +01:00. Production and test compilation used release 25. The PowerShell redirection invocation returned shell exit 1 due native stderr handling of nonfatal JVM warnings despite Maven's BUILD SUCCESS; the final command below explicitly preserves Maven's exit code.
- Final `mvn -B '-Dtest=CollectionRulesTest,ResidentReportApiTest,ConfirmationFreshnessTest' verify`: Maven exit 0, BUILD SUCCESS, 29 tests, 0 failures/errors/skips; 02:30 min, finished 18:52:33 +01:00. This recompiles the final added unelapsed-report and independent-range assertions. CollectionRulesTest 9, resident/report privacy tests 10, confirmation/freshness tests 10. JAR packaging and Spring Boot repackaging completed. This is a targeted verify, not a full or clean suite run.
- `git diff --check`: exit 0. `git diff --exit-code -- pom.xml src/main/resources/db/migration src/main/resources/db/demo/demo.sql`: exit 0. Java 25 and Spring Boot 3.5.7 preserved; V1–V4 unchanged. No DDL/backfill or seed compatibility edits needed.

Database runs used only dynamic disposable postgres:17-alpine Testcontainers (PostgreSQL 17.11), Docker 29.8.0, Testcontainers 1.21.3, Java 25.0.4.1. Flyway V1–V4 and Hibernate ddl-auto=validate initialized. Nonfatal JVM native-access/Mockito dynamic-agent warnings occurred. Logs: target/step6-tests.log and target/step6-final.log; generated Surefire reports under target/surefire-reports. These generated files are not committed artifacts.

New assertions prove overlapping independent transactions in both fact/commit orders, original first-fact IDs preserved, post-commit DISPUTED reads, repeat completion preserves records, exact-date separation, multiple witness reports counting as one disputed occurrence, missed excluding disputes, completed including disputes, historical operator attribution after removing present ward assignment, inclusive 31-day range and invalid ranges/roles/wards, unelapsed occurrences remaining SCHEDULED despite a report, snapshot hazards independent of pickup range, two drains on one street counting twice, flood expiry equality and an hour-later read, no freshness mutation, empty ward zeros/nulls, 105 hazards without truncation, deterministic repeated reads and no private identity/location/performance fields in hazard JSON. Existing schedule/report field allowlists, resident role/privacy, unique confirmations, commit visibility, seed repeatability and module boundaries remain passing. No valid assertion was weakened; old DONE-with-report becomes DISPUTED and the former transitional authority guard assertions now exercise AuthorityApi.

Changed files: authority/api/{AuthorityApi,OperatorPerformanceView,WardDashboardView,package-info}.java; authority/{domain,repository,service,web}/package-info.java and authority/service/AuthorityService.java (replacing dashboard scaffold); report/api/{ReportApi,WardHazardsView}.java; report/service/ReportService.java; collection/service/CollectionService.java; tests CollectionRulesTest, PostgresRulesTest, ModuleBoundaryTest; docs/data-contract.md, docs/engineer-a-handoff.md and this verification document.

Esther must wire canonical APIs through her trusted principal and HTTP layer; actorId is never client JSON. See the Step 6 contract for view fields, ordering, nullable newestReportAt (latest submission across all ward reports), snapshot semantics and canonical rule codes. No HTTP/live-demo, deployment or later-step verification is claimed. Historical verification follows.

---
# Step 5 verification - 31 October 2026

Only repeat-safe confirmations, canonical confirmation persistence/callers and configurable flood freshness were implemented. Existing uncommitted Step 4 work was preserved; no Step 6 behaviour, shared/live database access, .env value output, database reset, commit, push or deployment. Java 25.0.4.1, Spring Boot 3.5.7, Maven 3.9.16, Testcontainers 1.21.3 and disposable postgres:17-alpine / PostgreSQL 17.11 with Docker 29.8.0. Approved Maven cache/Docker access was used.

## Actual command history

1. `mvn -B '-Dtest=PostgresRulesTest' test`: exit 1, BUILD FAILURE, 10 tests, 0 assertion failures, 1 error, 0 skips; 02:06 min; finished 2026-10-31T14:28:45+01:00. V4/Flyway and JPA validation initialized successfully and the real-table duplicate assertion returned SQLSTATE 23505. The temporary LIKE-table demonstration guessed a cloned constraint name after the rename. Actual error: constraint "constraint_evidence_report_id_resident_id_key" of relation "constraint_evidence" does not exist. Fixed the test to query pg_constraint for its temporary relation, assert exactly one UNIQUE(report_id,resident_id), then quote/drop that specific temporary constraint. The duplicate count, restored uniqueness and SQLSTATE assertions were retained; no production constraint was removed or weakened.
2. `mvn -B '-Dtest=PostgresRulesTest,ConfirmationFreshnessTest,ConfirmationMigrationTest,ConfirmationControllerTest' test`: exit 0, BUILD SUCCESS, 25 tests, 0 failures/errors/skips; 02:32 min; finished 2026-10-31T14:34:34+01:00. Existing rules 10, freshness/transaction tests 10, migration tests 2, controller tests 3 all passed.
3. `mvn -B clean verify`: exit 0, BUILD SUCCESS, 73 tests, 0 failures/errors/skips; 04:42 min; finished 2026-10-31T14:39:56+01:00. Cleanly compiled 89 production and 16 test source files with release 25, built and repackaged the JAR. Actual final output:

```text
Tests run: 73, Failures: 0, Errors: 0, Skipped: 0
spring-boot:3.5.7:repackage (repackage)
BUILD SUCCESS
Total time: 04:42 min
Finished at: 2026-10-31T14:39:56+01:00
```

4. `git diff --check`: passed. `git diff --exit-code -- src/main/resources/db/migration/V1__mvp_foundation.sql src/main/resources/db/migration/V2__report_and_location_contract.sql src/main/resources/db/migration/V3__dated_collections.sql pom.xml src/main/java/com/tesa/ekowatch/auth` returned 0. V1 SHA256 49DF002EDAB7F243C4815774375699809E87336454E0854313B6ECDECA764175; V2 8ECDC97077E460D007B83A433B241070F831E8630049F2E40985DB93B20878C8; V3 1EEA2CCC85FA0CABE86AF166259D2A33AB9B8EF8F33E36081D40531ACE7FA780 unchanged. New V4 SHA256 52AB05F12D911C4B15F205B2FFD580E78C49F51D4A367D3E923E08E8239F026D.

## Evidence and limitations

ConfirmationMigrationTest exercised empty V1->V4 and populated-V1->V4 migrations in fresh isolated schemas. It asserts exact old row/UUID/microsecond timestamp preservation after column-name normalization, canonical table/columns/unique constraint/resident index, retained FK/null constraints (23503/23502), real duplicate rejection (23505), zero migration rerun, and unchanged report note. The new V4 is metadata-only; new service writes may subsequently update confirmed_at only as explicitly allowed for expired flood testimony. Hibernate initialized with ddl-auto=validate and the new ReportConfirmation mapping. Historical V1/older-target fixtures intentionally retain confirmations/user_id/created_at; all active SQL/seed/entity members use report_confirmations/resident_id/confirmed_at. No old confirm or requireConfirmable service aliases remain.

ConfirmationFreshnessTest uses a two-hour property override with injected Clock and real PostgreSQL. Existing ResidentReportApiTest still verifies the default six-hour projection/filter behaviour. The focused tests prove: first evidence committed/visible through a separate connection before API return; authoritative confirmedByMe/count; immediate repeats preserve row ID/timestamp and report history; different residents racing return counts 1 and 2; same resident racing returns one identical view/person; expired reconfirmation at the exact boundary preserves ID and people, leaves another resident's timestamp untouched, and can give fresh testimony again after a later expiry; a new resident after expiry inserts one new person; all three non-flood types always preserve timestamps on repeat; configured expiry/filter boundaries immediately before/at expiry and one-hour-later reads with no DB mutation; a transaction started current and waiting past expiry stays a no-op; concurrent expired repeats with different captured entry times return one renewal; role/self/missing/null refusal without evidence; and a synthetic photo-adapter projection failure after INSERT rolls back the row before returning an error, allowing a clean retry.

Lock-wait fixtures release their independent JDBC lock before closing worker executors even on test failure. Writers capture Clock time at transaction entry, take the report FOR UPDATE through report.api in the same mandatory transaction, and use READ_COMMITTED to observe a renewal committed ahead of them. No confirmation evidence history, removal, notification, background job or generic idempotency infrastructure was added. The existing temporary constraint demonstration remains only compatibility evidence, not the real-table mutation-invariant experiment reserved for Step 9.

Existing valid resident/self/authority/unique-person/commit-visibility assertions remain. The obsolete same-resident duplicate-error expectation was changed to two successful repeat-safe results with one actual resident row. The old OPEN-only eligibility assertion was superseded by the canonical resident/self contract and strengthened into a successful confirmation plus exact preservation of legacy status/updated_at; no lifecycle transitions were added. Report/get/list field privacy, filtering and six-hour defaults remain covered. Legacy collection preservation/assignment/completion/date rules and existing auth/controller tests passed unchanged in behaviour. Step 4 DONE-with-report remains DONE; no Step 6 dispute logic was introduced.

Controller tests verify existing route/principal delegation, canonical private response JSON, absent old confirm alias and SELF_CONFIRMATION_NOT_ALLOWED through existing advice. These MockMvc/module-mock checks are not a new claim of complete HTTP Basic authorization coverage or live integration. Esther retains security, final HTTP status/OpenAPI and client testimony/retry UX responsibilities. A later post-expiry submission is new testimony, not an unattended perpetual replay of the original request; no request-idempotency feature was added for confirmations or report creation.

Expected negative V2/V3 preflight rollback logs occurred in existing preservation tests. Non-fatal Mockito/JVM dynamic-agent and cached Hikari-pool warnings after earlier container teardown occurred; the final run had no errors or skips. Generated actual reports are in target/surefire-reports. No timings, shared-data inspection or production success beyond these commands are claimed. Seed IDs/volumes remain stable and insert-only; no renewal on seed rerun and no Step 8 expansion. Old verification sections below remain historical.

## Files changed in Step 5

- docs/data-contract.md
- docs/engineer-a-handoff.md
- docs/engineer-a-verification.md
- src/main/resources/db/migration/V4__report_confirmations.sql (new)
- src/main/java/com/tesa/ekowatch/confirmation/domain/Confirmation.java (removed)
- src/main/java/com/tesa/ekowatch/confirmation/domain/ReportConfirmation.java (new)
- src/main/java/com/tesa/ekowatch/confirmation/api/ConfirmationApi.java
- src/main/java/com/tesa/ekowatch/confirmation/service/ConfirmationService.java
- src/main/java/com/tesa/ekowatch/confirmation/web/ConfirmationController.java (existing caller adaptation only)
- src/main/java/com/tesa/ekowatch/report/api/ReportApi.java
- src/main/java/com/tesa/ekowatch/report/service/ReportService.java
- src/main/java/com/tesa/ekowatch/shared/ApiExceptionHandler.java
- src/main/resources/application.yml (freshness property; prior cutover settings preserved)
- src/main/resources/db/demo/demo.sql (canonical confirmation names only)
- src/test/java/com/tesa/ekowatch/PostgresRulesTest.java
- src/test/java/com/tesa/ekowatch/ResidentReportApiTest.java
- src/test/java/com/tesa/ekowatch/ReportMigrationTest.java
- src/test/java/com/tesa/ekowatch/ConfirmationFreshnessTest.java (new)
- src/test/java/com/tesa/ekowatch/ConfirmationMigrationTest.java (new)
- src/test/java/com/tesa/ekowatch/confirmation/web/ConfirmationControllerTest.java (new test, not controller)

Other working-tree changes belong to the already completed Step 4 and were retained; ekobridge-project.zip remains untouched. No commit/push/deployment.

---

# Step 4 verification - 31 October 2026

Implemented only dated collections/cutover and necessary caller/fixture compatibility. Java 25.0.4.1, Spring Boot 3.5.7, Maven 3.9.16, Testcontainers 1.21.3, postgres:17-alpine / PostgreSQL 17.11 and Docker 29.8.0. Approved Maven cache/Docker access was used. All database tests ran against disposable containers; no shared/live database, .env values, reset, commit, push or deployment.

## Actual commands/results

1. `mvn -B '-Dtest=PostgresRulesTest' test`: BUILD SUCCESS, exit 0, 10 tests, no failures/errors/skips, 02:46 min, finished 2026-10-31T14:02:06+01:00. Compiled 89 production sources under release 25; V1/V2/V3 applied on empty PostgreSQL and Hibernate initialized with ddl-auto=validate. This was an intermediate run before the final additional mapping checks.
2. `mvn -B verify`: preliminary BUILD SUCCESS, exit 0, 55 tests, no failures/errors/skips, 05:09 min, finished 2026-10-31T14:08:40+01:00. Included eight migration and six collection-rule tests. Review added three migration cases (effective date override, invalid active assignment, unused dated mapping), explicit completion READ_COMMITTED and a DB future-date check after this version had been compiled; this is not the final-source evidence.
3. `mvn -B clean verify`: BUILD SUCCESS, exit 0, 58 tests, no failures/errors/skips, 05:08 min, finished 2026-10-31T14:14:19+01:00. Cleanly compiled all 89 production and 13 test sources, exercised the final V3/production code, and packaged/repackaged successfully. Relevant actual output:

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- CollectionMigrationTest
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- CollectionRulesTest
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 05:08 min
Finished at: 2026-10-31T14:14:19+01:00
```

4. `mvn -B '-Dtest=CollectionRulesTest' test`: BUILD SUCCESS, exit 0, six tests, no failures/errors/skips, 01:45 min, finished 2026-10-31T14:16:19+01:00. After the clean run, only two raw-database test-date fixtures were made relative to PostgreSQL's actual Lagos date, to keep database-boundary assertions valid after the fixed-clock anchor date passes. All six were rerun; production/migration code did not change after the clean verification.
5. `git diff --check`: passed. `git diff --exit-code -- src/main/resources/db/migration/V1__mvp_foundation.sql src/main/resources/db/migration/V2__report_and_location_contract.sql pom.xml src/main/java/com/tesa/ekowatch/auth` returned 0. V1 SHA256 49DF002EDAB7F243C4815774375699809E87336454E0854313B6ECDECA764175; V2 8ECDC97077E460D007B83A433B241070F831E8630049F2E40985DB93B20878C8; new V3 1EEA2CCC85FA0CABE86AF166259D2A33AB9B8EF8F33E36081D40531ACE7FA780.

Full clean suite: ApiUserDetailsServiceTest 2, AuthenticatedActorTest 1, SecurityConfigTest 3, CollectionControllerTest 1, CollectionMigrationTest 11, CollectionRulesTest 6, ModuleBoundaryTest 1, PostgresRulesTest 10, ReportControllerTest 7, ReportMigrationTest 4, RequestIdFilterTest 2, ResidentReportApiTest 10. Total 58. Reports are generated under target/surefire-reports; the collection-rule XML/text is from the later six-test rerun.

## Evidence and limits

CollectionMigrationTest proves fresh migration/cutover record, all COMPLETED/FAILED attempts and inactive/active weekly rows unchanged in explicit legacy tables, original completion UUID/time/actor backfill, exact historical prerequisite dates, exactly 30 expanded days, overlap handling, deterministic rerun without extra rows, canonical unique/composite FK rejection, ambiguous/no operator failure, explicit highest-UUID choice (no arbitrary first candidate), invalid historical role/assignment failure, conflicting effective mapping failure, date-specific override success, invalid active assignment refusal, unused dated mapping refusal, explicit historical report-occurrence mapping and future-data failure. Negative preflight tests assert old V2 structures/history remain after rollback, with no successful V3 row; no Flyway repair/reset is used.

CollectionRulesTest proves Lagos midnight SCHEDULED/MISSED boundary without jobs/writes, DONE reads with retained missed reports (DISPUTED deliberately pending Step 6), actual missedReportCount, two concurrent same-actor completions returning one ID/time/row, repeats preserving timestamp after clock advance, refusal after ward assignment revocation, roles/ward/scheduled-operator checks, unscheduled/future/null/unknown refusals, 31-day inclusive range and invalid ranges, ward scope, distinct assigned streets, public schedule field allowlist with no operator performance, service rejection of nonelapsed/unscheduled missed reports and DB elapsed/FK checks. Direct future-completion database assertion supplies a matching future schedule, so it proves the date constraint rather than merely a missing FK.

Existing rule tests retain self-confirmation/authority/resident checks, confirmation races/uniqueness/commit visibility and seed 0/1/2 counts. The obsolete duplicate-error/FAILED-write expectations were replaced by repeat-safe completion and retained-FAILED-history assertions as explicitly requested in Step 4. Existing collection-controller principal/delegation verifications were retained via incremental signature edits. A proposed whole-file test rewrite was rejected by automatic review before execution; the safe incremental edit preserved all existing principal/delegation checks. No valid assertion was weakened or test skipped.

ResidentReportApiTest now provisions a dated occurrence for its valid MISSED_PICKUP fixture. ReportMigrationTest still tests V1->V2 note/history constraints, then applies V3 before starting the current JPA context. Application/report/collection tests confirm JPA validation and cross-module API boundaries with the new entity (Pickup.class is absent after compilation). Runtime services query only canonical dated tables; only migrations, historical fixtures and retained existing HTTP path strings use legacy pickup vocabulary. Authentication/security and previous report/HTTP tests remain passing. Controller tests are principal/delegation contract tests, not a new claim of full HTTP authorization or live integration.

Expected preflight-failure/rollback logs occurred in negative tests. Non-fatal Mockito/JVM dynamic-agent and cached Hikari-pool warnings following old test-container teardown occurred; all final tests passed with zero skips/errors. No performance numbers or shared-data inspection results are claimed. DemoSeed guards remain untouched; seven compatible stable-anchor dated rows are insert-only and tested for repeatability, with final Step 8 expansion still pending. Photo adapter/credentials remain Esther's integration work.

Cutover inputs and review/recovery procedure are in engineer-a-handoff.md; exact methods/views/columns are in data-contract.md. Ambiguous historical data requires an explicit verified mapping/remediation, never guessing, truncation or disposal. No Step 5 reconfirmation, Step 6 disputes/dashboard, Step 7 idempotency or Step 8 expansion was implemented.

Changed file inventory (pre-existing untracked ekobridge-project.zip excluded):

- docs/engineer-a-verification.md- docs/data-contract.md
- docs/engineer-a-handoff.md
- src/main/java/com/tesa/ekowatch/collection/api/CollectionApi.java
- src/main/java/com/tesa/ekowatch/collection/api/CollectionScheduleView.java
- src/main/java/com/tesa/ekowatch/collection/api/CollectionStatus.java
- src/main/java/com/tesa/ekowatch/collection/api/CollectionView.java
- src/main/java/com/tesa/ekowatch/collection/domain/CollectionRecord.java
- src/main/java/com/tesa/ekowatch/collection/domain/CollectionSchedule.java
- src/main/java/com/tesa/ekowatch/collection/domain/Pickup.java
- src/main/java/com/tesa/ekowatch/collection/service/CollectionService.java
- src/main/java/com/tesa/ekowatch/collection/web/CollectionController.java
- src/main/java/com/tesa/ekowatch/report/service/ReportService.java
- src/main/java/com/tesa/ekowatch/shared/ApiExceptionHandler.java
- src/main/resources/application.yml
- src/main/resources/db/demo/demo.sql
- src/main/resources/db/migration/V3__dated_collections.sql
- src/test/java/com/tesa/ekowatch/collection/web/CollectionControllerTest.java
- src/test/java/com/tesa/ekowatch/CollectionMigrationTest.java
- src/test/java/com/tesa/ekowatch/CollectionRulesTest.java
- src/test/java/com/tesa/ekowatch/PostgresRulesTest.java
- src/test/java/com/tesa/ekowatch/ReportMigrationTest.java
- src/test/java/com/tesa/ekowatch/ResidentReportApiTest.java

---

# Merged integration verification - 31 October 2026

Cause: dev/branch had V1-era Report.description, old ReportApi create/get/list methods, old ReportService description SQL, DTOs and demo SQL. The completed Steps 2-3 files (including source V2, build.md and data-contract.md) were absent, while target/classes/db/migration still contained V2 renaming description to note. Hibernate consequently expected a column that the migrated schema did not have. A clean build was required to prove the fix does not depend on stale generated resources.

Compared against dayo/data-rules at 6b9c51bd4db453d927f028e45c6eac907ca76f6f. Restored the completed canonical data implementation, supporting DTO/API/services and JPA mappings, original V2, demo SQL and PostgreSQL tests from that reference. Report.note explicitly uses @Column(name="note"); no description column or old service aliases were added. Current application source uses note; description remains intentionally in V1, V2 preflight/rename, V1 migration fixtures and a negative JSON-field assertion.

Preserved Esther's auth/security classes, ActorAccess.id(Authentication), DatabaseActorAccess, auth tests and confirmation/collection controllers. Adapted ReportController's existing routes to the shared CreateReportRequest, createReport/getReport/listReports, typed filters and Page response with principal-derived actorId. Added only the necessary canonical validation/not-found mappings to existing HTTP advice. ReportControllerTest now tests canonical delegation, blank-note refusal, JSON privacy/principal source, pagination defaults and HTTP error translation. Restored data tests retain valid authority/role/uniqueness/concurrency/history assertions. No Step 4 or other feature was implemented.

Actual requested command results, in order (approved Maven cache/Docker access):

```powershell
mvn -B '-Dtest=PostgresRulesTest' test
mvn -B clean verify
git diff --check
```

Targeted command: exit 0, BUILD SUCCESS; 10 tests, 0 failures/errors/skips. Total time 03:00 min; finished 2026-10-31T11:48:22+01:00. Flyway applied V1/V2 to empty disposable PostgreSQL 17.11; Hibernate validated note and initialized EntityManagerFactory. Esther's user-details/authentication configuration initialized alongside the canonical data services.

Clean command: exit 0, BUILD SUCCESS; Maven removed only generated target output, recompiled 86 production sources and 11 test sources under release 25, executed all suites, built/repackaged the JAR. Actual final output:
```text
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 04:23 min
Finished at: 2026-10-31T11:53:11+01:00
```

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| ApiUserDetailsServiceTest | 2 | 0 | 0 | 0 |
| AuthenticatedActorTest | 1 | 0 | 0 | 0 |
| SecurityConfigTest | 3 | 0 | 0 | 0 |
| CollectionControllerTest | 1 | 0 | 0 | 0 |
| ModuleBoundaryTest | 1 | 0 | 0 | 0 |
| PostgresRulesTest | 10 | 0 | 0 | 0 |
| ReportControllerTest | 7 | 0 | 0 | 0 |
| ReportMigrationTest | 4 | 0 | 0 | 0 |
| RequestIdFilterTest | 2 | 0 | 0 | 0 |
| ResidentReportApiTest | 10 | 0 | 0 | 0 |
| Total | 41 | 0 | 0 | 0 |

The controller HTTP tests use MockMvc and mocked module/principal interfaces; these are contract/binding checks, not a claim of full HTTP Basic authorization coverage. Real PostgreSQL tests cover private projections, preferences, photo ownership, filtering/pagination/freshness and constraints; migration tests cover both empty and populated-V1 paths plus preservation/preflight rejection. Negative-preflight migration-failure logs are expected passing assertions. Non-fatal Mockito/JVM agent and cached Hikari-pool warnings after test-container teardown were observed.

V1 remains exactly as received on dev/branch (SHA256 49DF002EDAB7F243C4815774375699809E87336454E0854313B6ECDECA764175). V2 was restored byte-for-byte from dayo/data-rules: Git blob 664e384729264dd322ea38440467bedc1d65f6bd; SHA256 8ECDC97077E460D007B83A433B241070F831E8630049F2E40985DB93B20878C8. Existing applied migrations were not edited. No V3/new migration, duplicate column, schema-validation bypass, shared/live database access/reset or test skip was used. PostgreSQL databases were disposable postgres:17-alpine Testcontainers with test-only sslmode=disable. Java 25.0.4.1, Spring Boot 3.5.7, Maven 3.9.16 and Testcontainers 1.21.3 retained.

The original uncommitted pom.xml merge-marker resolution and untracked ekobridge-project.zip were preserved. No dependency change was made by this repair. No commit, push or deployment. Earlier verification sections below are historical records from the data branch, not evidence for this merged revision.

Changed/restored file inventory (excludes the pre-existing pom.xml and archive changes):
- build.md
- docs/data-contract.md
- docs/engineer-a-handoff.md
- docs/engineer-a-verification.md
- src/main/java/com/tesa/ekowatch/photo/api/PhotoApi.java
- src/main/java/com/tesa/ekowatch/photo/api/PhotoUrlResolver.java
- src/main/java/com/tesa/ekowatch/photo/api/PhotoView.java
- src/main/java/com/tesa/ekowatch/photo/domain/Photo.java
- src/main/java/com/tesa/ekowatch/photo/service/PhotoService.java
- src/main/java/com/tesa/ekowatch/report/api/CreateReportRequest.java
- src/main/java/com/tesa/ekowatch/report/api/FloodStatus.java
- src/main/java/com/tesa/ekowatch/report/api/ReportApi.java
- src/main/java/com/tesa/ekowatch/report/api/ReportSort.java
- src/main/java/com/tesa/ekowatch/report/api/ReportType.java
- src/main/java/com/tesa/ekowatch/report/api/ReportView.java
- src/main/java/com/tesa/ekowatch/report/domain/Report.java
- src/main/java/com/tesa/ekowatch/report/service/ReportService.java
- src/main/java/com/tesa/ekowatch/report/web/ReportController.java
- src/main/java/com/tesa/ekowatch/shared/ApiExceptionHandler.java
- src/main/java/com/tesa/ekowatch/shared/Location.java
- src/main/java/com/tesa/ekowatch/shared/Page.java
- src/main/java/com/tesa/ekowatch/shared/TimeConfiguration.java
- src/main/java/com/tesa/ekowatch/user/api/CurrentUserView.java
- src/main/java/com/tesa/ekowatch/user/api/Role.java
- src/main/java/com/tesa/ekowatch/user/api/UserApi.java
- src/main/java/com/tesa/ekowatch/user/domain/UserAccount.java
- src/main/java/com/tesa/ekowatch/user/service/UserService.java
- src/main/java/com/tesa/ekowatch/ward/api/StreetView.java
- src/main/java/com/tesa/ekowatch/ward/api/WardApi.java
- src/main/java/com/tesa/ekowatch/ward/api/WardView.java
- src/main/java/com/tesa/ekowatch/ward/domain/Street.java
- src/main/java/com/tesa/ekowatch/ward/service/WardService.java
- src/main/resources/db/demo/demo.sql
- src/main/resources/db/migration/V2__report_and_location_contract.sql
- src/test/java/com/tesa/ekowatch/ModuleBoundaryTest.java
- src/test/java/com/tesa/ekowatch/PostgresRulesTest.java
- src/test/java/com/tesa/ekowatch/report/web/ReportControllerTest.java
- src/test/java/com/tesa/ekowatch/ReportMigrationTest.java
- src/test/java/com/tesa/ekowatch/ResidentReportApiTest.java

---

# Steps 2-3 test startup repair - 31 October 2026

Read the supplied failing target/surefire-reports/com.tesa.ekowatch.PostgresRulesTest.txt before rerunning. It reported 10 errors in that class. The first failing case, differentResidentsConcurrently, could not load the context; the underlying chain was:

```text
BeanCreationException: entityManagerFactory -> flywayInitializer
FlywaySqlException: Unable to obtain connection from database
SQL State: 08001
PSQLException: The connection attempt failed.
Caused by: java.net.SocketTimeoutException: Read timed out
  ... PGStream.receiveChar(PGStream.java:477)
  ... ConnectionFactoryImpl.enableSSL(ConnectionFactoryImpl.java:627)
```

The JDBC driver timed out waiting for the SSL negotiation response while Flyway tried to obtain its startup connection. The later ApplicationContext failure-threshold messages were cascading errors from that first failure, not independent causes. The stack does not establish the exact Docker/host reason for the stalled response; it does establish the failed SSL negotiation phase. The supplied 27-test/10-error run is retained here as failed evidence; it is not replaced by the earlier historical successful run below.

Fix: configure sslmode=disable using withUrlParam on the three disposable postgres:17-alpine containers in PostgresRulesTest, ResidentReportApiTest and ReportMigrationTest. These test servers use non-TLS connections. Container readiness, Spring datasource/Flyway and direct migration JDBC URLs now consistently use that setting. No production TLS or datasource setting changed. No retries, timeout increases, failure-threshold changes, skipped tests or weakened assertions were introduced. No application/service/schema changes or Step 4 work.

Executed in the requested order, with approved Maven cache/Docker access:

1. `mvn -B '-Dtest=PostgresRulesTest' test`: exit 0, BUILD SUCCESS, 10 tests, 0 failures/errors/skips, 01:43 min, finished 2026-10-31T10:48:59+01:00. The logged disposable JDBC URL included sslmode=disable; Hikari obtained a connection, Flyway applied V1/V2, Hibernate validated the schema, and all 10 existing assertions/test methods ran.
2. `mvn -B verify`: exit 0, BUILD SUCCESS, 27 tests, 0 failures/errors/skips, 03:39 min, finished 2026-10-31T10:53:00+01:00. PostgresRulesTest 10, ResidentReportApiTest 10, ReportMigrationTest 4, ModuleBoundaryTest 1 and RequestIdFilterTest 2 all passed. Packaging/repackaging completed.

Actual final output excerpt:
```text
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
spring-boot:3.5.7:repackage (repackage)
BUILD SUCCESS
Total time: 03:39 min
Finished at: 2026-10-31T10:53:00+01:00
```

The full run again logged sslmode=disable on all disposable JDBC URLs, successfully initialized Flyway/Hibernate, and exercised both empty and populated-V1 migration paths. Expected negative-preflight rollback logs, non-fatal Mockito/JVM agent warnings and cached-pool warnings after earlier test containers stopped were observed; no tests failed or were skipped. Surefire XML/text reports now describe the successful rerun; the original failing cause is preserved above. No test assertion changed in this repair. git diff --check passed.

Java 25.0.4.1, Boot 3.5.7, PostgreSQL 17.11, Testcontainers 1.21.3 and Docker 29.8.0 retained. No clean build was requested; test sources were recompiled for the targeted run, and unchanged production classes were reused. No .env values or shared/live database were used.

Changed in this repair: the three test-container definitions above and verification/handoff notes. V1 and V2 hashes remain respectively 32CB585EA991A568E593576DA2BEA97DEFDF7D34E9E1384AD748B5C800E85F4F and 8ECDC97077E460D007B83A433B241070F831E8630049F2E40985DB93B20878C8. No migration/backfill, DTO/contract, seed, database-constraint, password or role-rule changes. Existing working-tree changes remain. No commit/push/deployment. Esther's integration handoff remains the Step 3 contract; this is test-only transport configuration.

---

# Step 3 verification - 31 October 2026

Current result: full `mvn -B verify` exited 0, BUILD SUCCESS, finished 2026-10-31T10:02:21+01:00 (Africa/Lagos), total time 03:09 min. Java 25.0.4.1, Spring Boot 3.5.7, Maven 3.9.16, Testcontainers 1.21.3, Docker 29.8.0 and disposable postgres:17-alpine / PostgreSQL 17.11. Approved cache/Docker access was used. No .env values or application database connections were used. No production/shared database, reset, commit, push or deployment.

Actual command sequence/results:

1. `mvn -B '-Dtest=PostgresRulesTest,ModuleBoundaryTest' test`: BUILD SUCCESS, 10 tests, 0 failures/errors/skips, 03:23 min, finished 2026-10-31T09:57:17+01:00. This was an intermediate run before the final added API/adapter tests and clock precision alignment. It compiled 77 production sources with release 25 and preserved all valid authority/rule assertions.
2. `mvn -B verify`: BUILD FAILURE at testCompile, 26.595 s, finished 2026-10-31T09:58:28+01:00. The new ResidentReportApiTest had an ambiguous Role import (Spring annotation versus user.api.Role). No tests ran in that attempt. Fixed by replacing the wildcard Spring annotation import with explicit Bean/Import/Primary imports; no assertion or application behaviour changed for that correction.
3. `mvn -B verify`: final full BUILD SUCCESS. Relevant actual output:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- ModuleBoundaryTest
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- PostgresRulesTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- ReportMigrationTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- RequestIdFilterTest
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- ResidentReportApiTest
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
spring-boot:3.5.7:repackage (repackage)
BUILD SUCCESS
Total time: 03:09 min
Finished at: 2026-10-31T10:02:21+01:00
```

Final production classes were already up to date from the preceding compile; the fixed five-source test set was recompiled under release 25. This was not a clean build. JAR and Spring Boot repackaging completed. Actual generated evidence is in target/surefire-reports. Docker containers used dynamic localhost ports and Ryuk cleanup, not a configured application database.

New API test evidence: all four report types, server-derived ward/reporter/initial timestamps, note normalization, date presence rules, finite complete private coordinate pairs and public street map locations; role/existence/invalid-input refusal without partial inserts; resident-only matching preferences with old values preserved on failure; safe current-user and ward/street views; owned photos only even when another resident's photo is already attached; synthetic URL resolver wiring, unresolved URL null and no raw keys/owner IDs; exact ReportView/request/Page serialized field allowlists and absence of submitted coordinates/reporter UUID/email/hash/lifecycle; no old create/get/list aliases; actual confirmation counts and actor-specific confirmedByMe; max evidence time, six-hour expiry, immediately-before/equal expiry and one-hour-later reads, unchanged stored evidence after reads; filtered totals/items, ward isolation, street/type filters, invalid flood filters, pagination defaults/bounds/empty/out-of-range/large offsets, CONFIRMATIONS and NEWEST ordering, and UUID ties across pages. Full-team-plan ordering source was unavailable; the explicit choice is recorded in data-contract.md, not represented as verified against that missing document.

Existing PostgresRulesTest authority-role, self-confirmation/lifecycle, concurrent distinct/same resident counts, committed-row visibility, direct unique constraint and collection rules remain passing. Its request/result callers were migrated to canonical methods without aliases. New absent-adapter test confirms persisted photo IDs do not expose storage keys in photoUrl. Existing seed assertions still prove repeatability with 0/1/2 counts; demo SQL was already compatible after Step 2 and was not expanded or reset. ModuleBoundaryTest now also covers photo, including the new API-only report/photo dependency. No imports of another module's domain/service/repository were introduced.

ReportMigrationTest again passed both empty-schema and populated-V1 paths, original row/UUID/time/history preservation, incompatible-note preflight rollback and target constraints. Hibernate initialized with unchanged ddl-auto=validate, including the ReportType enum mapping. No V3 migration exists or is needed for Step 3. Migration-failure messages in the negative preflight cases are expected passing assertions. Non-fatal Mockito/JVM dynamic-agent warnings and cached Hikari pool warnings after a prior test container stopped were observed; the final suite had no errors or skips.

Automatic review rejected an earlier combined command that proposed removing the valid authority-role assertions. That command did not execute. The safer implementation retains the pre-existing authorityReports guard and both assertions until Step 6 replaces the operation. No rejected test removal was worked around, and no valid assertion was weakened. The guarded transitional read returns the new private ReportView and is not a canonical HTTP handoff target. All requested create/get/list renames have no aliases.

Static checks: no old reports.create/get/list calls or ReportView.description accessors remain under src; no cross-module internal-package imports were found. `git diff --exit-code -- pom.xml src/main/resources/db/migration/V1__mvp_foundation.sql` returned 0. V1 SHA256: 32CB585EA991A568E593576DA2BEA97DEFDF7D34E9E1384AD748B5C800E85F4F; V2 SHA256: 8ECDC97077E460D007B83A433B241070F831E8630049F2E40985DB93B20878C8 (no Step 3 migration edits).

Esther still owes HTTP security/parsing/error integration and the sanitised photo URL/storage adapter. No authentication/session/upload/dashboard/schedule-date/idempotency/reconfirmation implementation or HTTP/live-demo verification is claimed. Earlier Step 1/2 sections below are historical evidence.

---

# Step 2 verification - 31 October 2026

Actual commands (approved Maven dependency/cache and Docker access; disposable PostgreSQL only):

```powershell
mvn -B '-Dtest=ReportMigrationTest,PostgresRulesTest' test
mvn -B '-Dtest=ModuleBoundaryTest,RequestIdFilterTest' verify
```

First command exited 0. Relevant actual output:
```text
Compiling 58 source files with javac [debug parameters release 25]
Compiling 4 source files with javac [debug parameters release 25]
Database: ... (PostgreSQL 17.11)
Successfully applied 2 migrations to schema "public", now at version v2
Initialized JPA EntityManagerFactory for persistence unit 'default'
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- PostgresRulesTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- ReportMigrationTest
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 01:55 min
Finished at: 2026-10-31T09:40:33+01:00
```
`Database: ...` abbreviates the dynamic disposable Testcontainers JDBC URL; no application database URL was used. postgres:17-alpine, Testcontainers 1.21.3, Docker 29.8.0, Java 25.0.4.1, Maven 3.9.16 and Spring Boot 3.5.7. Expected migration-failure logs occurred in the two preflight rejection tests, which asserted transactional rollback and unchanged original data. These are passing negative tests, not hidden failures. Non-fatal Mockito dynamic-agent/JVM sharing warnings occurred.

Coverage: V1-only fixture with all eight tables populated, including RESOLVED lifecycle history, original timestamps, confirmations, inactive weekly schedules and COMPLETED/FAILED pickup history; V2 upgrade preserves all original column values/IDs across all tables and leaves new optional values null. Migration rerun executes zero migrations. Upgraded-schema Spring context initializes with Hibernate validate. Fresh-schema application context also applies V1/V2 and validates all entities, including Photo. Independent empty-schema constraint tests cover all four report types, unknown types, note 1000/1001 boundaries, blank/untrimmed notes, required/forbidden collection dates, paired preferences, cross-ward/missing street and photo FKs, photo owner FK/required metadata/positive size, paired coordinate bounds/NaN/infinity, and the retained report street/ward FK. The photo metadata fixture is a synthetic test sentinel; no real asset or sanitisation claim is made.

Preflight tests cover 1001-character and untrimmed V1 descriptions; they verify original description/status preserved, V1 success retained, no V2 success/history row, no renamed note column or photos table. Service tests cover trimmed 1000-character input, null/blank/1001 rejection, 1000 supplementary Unicode characters, retained roles/location rules and seed repeatability. No assertion was weakened. No clean/reset was used by the new migration tests; each uses a fresh unique schema in a disposable container. Existing rule-test truncation remains confined to its disposable container.

Second command exited 0. It ran only the remaining regression tests, then packaged/repackaged:
```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- ModuleBoundaryTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- RequestIdFilterTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
spring-boot:3.5.7:repackage (repackage)
BUILD SUCCESS
Total time: 32.726 s
Finished at: 2026-10-31T09:41:21+01:00
```
All 16 current test methods passed across these two commands. This was not an unfiltered `mvn -B verify` run. No checks are claimed for later canonical API, upload sanitisation, HTTP or future migrations. Generated Surefire XML/text reports are in target/surefire-reports. `git diff --exit-code -- pom.xml src/main/resources/db/migration/V1__mvp_foundation.sql` exited 0, confirming no changes to either file. No shared/live connection, commit, push, deployment or Step 3 work.

The earlier Step 1 records below remain historical and are superseded only as current implementation status by this Step 2 record.

---

# Step 1 baseline rerun - Docker available

Current result: `mvn -B verify` exited 0 with approved Maven cache/Docker access, finished 2026-10-31T09:27:05+01:00 (Africa/Lagos). Java 25.0.4.1, Maven 3.9.16, Spring Boot 3.5.7; no pom or dependency changes.

Actual output excerpts:
```text
Found Docker environment with local Npipe socket (npipe:////./pipe/docker_engine)
Server Version: 29.8.0
Database: jdbc:postgresql://localhost:54459/test?loggerLevel=OFF (PostgreSQL 17.11)
Successfully validated 1 migration
Current version of schema "public": << Empty Schema >>
Successfully applied 1 migration to schema "public", now at version v1
Initialized JPA EntityManagerFactory for persistence unit 'default'
Restored constraint SQLSTATE=23505
Same-resident unique constraint SQLSTATE=23505
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- ModuleBoundaryTest
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- PostgresRulesTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- RequestIdFilterTest
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
Building jar: target/ekowatch-0.0.1-SNAPSHOT.jar
spring-boot:3.5.7:repackage (repackage)
BUILD SUCCESS
Total time: 02:01 min
Finished at: 2026-10-31T09:27:05+01:00
```
The jar path above is shortened from the absolute workspace path. Testcontainers 1.21.3 started a disposable postgres:17-alpine container and Ryuk cleanup monitor. Flyway applied V1 on empty PostgreSQL and Hibernate initialized with the unchanged ddl-auto=validate configuration. All eight existing PostgreSQL tests ran, including concurrency, roles, collection rules, committed-row visibility and seed repeatability. No shared/live application database was used.

Maven reported existing classes up to date for compile/testCompile; this was not a clean build. Packaging and Spring Boot repackaging succeeded. Non-fatal Mockito dynamic-agent/JVM class-sharing warnings occurred. Generated reports are in target/surefire-reports and now describe this successful run.

This establishes the current V1 baseline only: no populated upgrade migration or later canonical API exists yet. The temporary-table mutation demonstration is not the Step 9 real-table invariant evidence. No application, migration, tests, seed or pom changes were made; Esther's integration responsibilities remain unchanged. Earlier failures and supplied historical records follow, explicitly retained as history.

---

# Step 1 baseline verification — 31 October 2026

This section records the earlier failed attempt, superseded by the successful rerun above. The 25 October record below is retained as historical reported evidence, not re-established by this run. In particular, its datasource-default/file-status statements are historical; current application.yml requires environment datasource configuration, and initial git status showed only untracked build.md and ekobridge-project.zip.

Commands actually run: `java -version`, `mvn -version`, `docker version`, `mvn -B verify`; then the same Maven verify command with approved access to its existing cache. Java 25.0.4.1 and Maven 3.9.16 are available. No dependency/JDK change was made.

Initial verify exited 1 before compilation:
```text
Access is denied.
[ERROR] Could not create local repository at C:\.m2\repository -> [Help 1]
```
Docker probe exited 1; client 29.8.0, API 1.56. It reported config-file access denied under the sandbox and:
```text
failed to connect to the docker API at npipe:////./pipe/docker_engine;
open //./pipe/docker_engine: The system cannot find the file specified.
```
Approved-access Maven retry exited 1. Actual relevant output:
```text
compiler:3.14.1:compile — Nothing to compile - all classes are up to date.
compiler:3.14.1:testCompile — Nothing to compile - all classes are up to date.
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- ModuleBoundaryTest
Testcontainers version: 1.21.3
Could not find a valid Docker environment. Please check configuration.
PostgresRulesTest -- java.lang.IllegalStateException: Could not find a valid Docker environment.
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- RequestIdFilterTest
Tests run: 4, Failures: 0, Errors: 1, Skipped: 0
BUILD FAILURE
Total time: 27.188 s
Finished at: 2026-10-31T08:54:29+01:00
Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.4:test
```
Surefire counts the failing PostgreSQL class initialization as one error; none of its eight test methods executed. No PostgreSQL container started. Flyway execution, Hibernate schema validation, PostgreSQL rules/concurrency and seed repeatability did not run; packaging/repackaging was not reached. Existing compiled classes were reused, so this is not a clean-compilation claim. Generated details are in target/surefire-reports. No shared/local application database was substituted. The subsequent successful rerun above establishes the passing V1 baseline.

The temporary LIKE-table uniqueness demonstration below tests V1's SQL uniqueness, not the later required canonical real-table mutation invariant. It is not final Step 9 evidence. Canonical authority access and HTTP authorization evidence also remain future work. No behaviour, migration, fixture or valid assertion was changed in Step 1.

---

## Historical record supplied with the repository (25 October 2026)

# Engineer A verification evidence

Date: 2026-10-25 (Africa/Lagos).
Branch: dayo/data-rules. No commits, pushes or merges made.

## Successful full run

Command: `mvn -B verify`.
Final code verification completed 2026-10-25 04:31:05 +01:00, BUILD SUCCESS, elapsed 2m05s.
This includes explicit READ COMMITTED confirmation isolation and highest-precedence request-ID filtering.
The preceding full run also passed at 04:28:43 +01:00 (2m56s).
JDK 25.0.4.1, Maven 3.9.16, Spring Boot 3.5.7, PostgreSQL 17.11 in postgres:17-alpine.
Flyway applied V1 to an empty disposable database. Hibernate EntityManagerFactory initialized with ddl-auto=validate.
Packaging/repackaging completed.

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| ModuleBoundaryTest | 1 | 0 | 0 | 0 |
| PostgresRulesTest | 8 | 0 | 0 | 0 |
| RequestIdFilterTest | 2 | 0 | 0 | 0 |
| Total | 11 | 0 | 0 | 0 |

## Concurrency and SQL evidence

- differentResidentsConcurrently: two futures returned OK; final count exactly 2.
- sameResidentConcurrently: one OK and one DUPLICATE_CONFIRMATION; final count exactly 1.
- A direct duplicate insert into confirmations failed with actual SQLSTATE **23505**.
- isolatedConstraintEvidence: temporary LIKE-confirmations table, cloned unique constraint removed; inserted two rows with the same report/user and distinct IDs, asserted count 2.
- Temporary table cleared; UNIQUE(report_id,user_id) restored; second duplicate insert rejected with actual SQLSTATE **23505**.
- countOnlyIncludesCommittedRows: independent uncommitted insert invisible (count 0), then visible after commit (count 1).
- Constraints on the real confirmations table were never dropped. All destructive setup/DDL was restricted to the disposable Testcontainers database.

Other assertions: self-confirmation/closed report/missing report/role rejection, authority list, invalid location/description, operator assignment, duplicate pickup, completed pickup clears missed result, failed pickup stays missed, repeat seed retains 0/1/2 report counts, yesterday's missed schedule, MDC cleanup/restoration on success and exceptions.

## Earlier failures and resolution

1. Default Testcontainers Docker client could not initialize against Docker Engine 29.8.0 (minimum API 1.44).
   Confirmed API 1.44 worked, then added test-only src/test/resources/docker-java.properties with api.version=1.44.
   The successful plain mvn -B verify run required no extra command flags.
2. Initial real PostgreSQL run caught an invalid unquoted day alias in missed-pickup SQL.
   Replaced it with scheduled_date. All three affected tests passed on the successful full run.

Tests were executed with approved access to Maven cache/Docker because the workspace sandbox denies Docker named-pipe access.
Local ekowatch-db, FoodBridge, PayBridge and production databases were not used.
Surefire XML/text reports are under target/surefire-reports (generated, untracked).

## File inventory

Created:
- docs/engineer-a-handoff.md
- docs/engineer-a-verification.md
- src/main/java/com/tesa/ekowatch/collection/api/CollectionApi.java
- src/main/java/com/tesa/ekowatch/collection/domain/CollectionSchedule.java
- src/main/java/com/tesa/ekowatch/collection/domain/Pickup.java
- src/main/java/com/tesa/ekowatch/collection/service/CollectionService.java
- src/main/java/com/tesa/ekowatch/confirmation/api/ConfirmationApi.java
- src/main/java/com/tesa/ekowatch/confirmation/domain/Confirmation.java
- src/main/java/com/tesa/ekowatch/confirmation/service/ConfirmationService.java
- src/main/java/com/tesa/ekowatch/report/api/ReportApi.java
- src/main/java/com/tesa/ekowatch/report/api/ReportView.java
- src/main/java/com/tesa/ekowatch/report/domain/Report.java
- src/main/java/com/tesa/ekowatch/report/service/ReportService.java
- src/main/java/com/tesa/ekowatch/shared/DemoSeed.java
- src/main/java/com/tesa/ekowatch/shared/RequestIdFilter.java
- src/main/java/com/tesa/ekowatch/shared/RuleViolation.java
- src/main/java/com/tesa/ekowatch/user/api/ActorAccess.java
- src/main/java/com/tesa/ekowatch/user/domain/UserAccount.java
- src/main/java/com/tesa/ekowatch/user/service/DatabaseActorAccess.java
- src/main/java/com/tesa/ekowatch/ward/domain/OperatorWard.java
- src/main/java/com/tesa/ekowatch/ward/domain/Street.java
- src/main/java/com/tesa/ekowatch/ward/domain/Ward.java
- src/main/resources/db/demo/demo.sql
- src/main/resources/db/migration/V1__mvp_foundation.sql
- src/test/java/com/tesa/ekowatch/PostgresRulesTest.java
- src/test/java/com/tesa/ekowatch/RequestIdFilterTest.java
- src/test/resources/docker-java.properties

Modified by this task:
- .env.example (local port/user)
- README.md (implementation status)
- docs/architecture.md (implementation status)
- src/main/resources/application.yml (only added request-ID logging; preserved existing user datasource edits)
- src/test/java/com/tesa/ekowatch/ModuleBoundaryTest.java (enforced architecture rules)

Pre-existing user modification preserved: pom.xml. All requested dependencies already existed; no dependency changes were needed.
