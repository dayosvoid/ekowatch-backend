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
