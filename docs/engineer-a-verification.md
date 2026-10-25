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
