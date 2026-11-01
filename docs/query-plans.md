# Step 9 query-plan evidence

Actual PostgreSQL 17.11 output is retained in [evidence/step9-query-plans.txt](evidence/step9-query-plans.txt). It was captured by ReadProjectionPlanTest during `mvn -B verify`, on a test-owned disposable postgres:17-alpine instance. It is not a production benchmark or load test.

The fixed fictional fixture has 9 reports, 5 confirmations, 172 dated schedules, 3 completions, 8 streets and 8 users. The presentation parameters are 31 October 2026 at 12:00Z, default six-hour freshness, and the inclusive 16 October–14 November schedule range. VACUUM ANALYZE runs only on these disposable fixture tables before capture. No planner settings are forced and no real/shared data is queried.

The test extracts the SQL text blocks from the actual ReportService, CollectionService and AuthorityService source. For listReports it appends the actual selected ward/type/NEWEST/page branch. Parameters, exact SQL, real returned-row counts, buffers, estimates and EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) output are present for both V5 and V6. Each query also executes normally and asserts the returned row count; every stored row across all 13 current/legacy/audit tables is compared before/after V6. The new index definitions are checked and a repeated V6 migration executes zero migrations.

| Production query branch | Actual returned rows, before and after V6 | Observed access on this fixture |
| --- | ---: | --- |
| listReports: FLOODED_ROAD, NEWEST, page 0 size 20 | 3 | Sequential scans of reports/streets and lateral confirmation rows; sort/limit |
| listWardHazards: fixed current snapshot | 5 | Sequential scans and lateral confirmation aggregate |
| listCollectionSchedules: full period | 172 | Sequential scans/joins and exact-date missed-report subquery |
| listMyAssignedStreets: operator 4 | 4 | Sequential scans and distinct streets |
| getWardDashboard: performance | 2 | Sequential scans/joins and grouped real occurrences |
| getWardDashboard: hazard aggregate | 1 | Sequential scan of reports and correlated latest evidence |

The planner chose sequential scans for these very small, mostly single-page relations, including after V6. The candidate indexes are not presented as selected paths or a demonstrated speedup here. Measured timings in the raw output include cached fixture reads and instrumentation; they are observations from one run, not capacity estimates. No fabricated timings or larger row counts are supplied.

V6's focused candidates match actual predicates: ix_reports_ward_type_created supports ward/type/newest/id ordering; ix_reports_missed_street_date is partial for exact MISSED_PICKUP street/date existence/counts; ix_schedule_date_street supports dated range reads with recorded operator available; ix_schedule_operator_date_street supports assigned-operator date ranges; ix_confirmation_report_evidence covers report/latest-evidence access with resident available. Existing unique street/date and report/resident constraints remain. No index pretends to precompute confirmation ordering or moving freshness. Each adds write/storage and transactional index-build locking cost. Assess actual workload selectivity before claiming benefit or dropping a redundant candidate in a later forward migration.

These SQL plans do not measure photo resolution, HTTP security/transport, rendering or frontend work. Role refusal is proved separately by the normal and controlled mutation tests. No deployment, live query plan or HTTP success is claimed.
