# Architecture and API decisions

Current data-layer decisions, 31 October 2026. Implemented decisions are not represented as approval by Esther; her integration review remains outstanding.

| Decision | Accepted behaviour | Alternative / cost / reversal |
| --- | --- | --- |
| [ADR 001](adr/001-confirmation-and-flood-freshness.md) | One resident once; deliberate renewal after flood expiry; Clock-based read freshness | Reject request counting and scheduler-owned freshness; accept row-lock/aggregate cost and no full attestation history; revisit on measured contention or approved audit needs |
| [ADR 002](adr/002-derived-disputed-pickups.md) | Preserve completion and testimony, derive exact-date DISPUTED, dated operator attribution | Reject last-writer truth/counter mutation; accept unresolved disagreement/read work; revisit only with an explicit adjudication/audit requirement |
| Report retries | Separate internal UUID key, canonical request hash, original ReportView snapshot, 24-hour protection | No key in client JSON, random per-retry server key, live-view replay or generic operation framework; expired reuse is a new intent |
| Demo provisioning | Fixed fictional anchors/UUIDs, insert-only, local-demo plus explicit flag, production exclusion | No automatic resets, credential guesses, fake assets or evidence refresh. Reprovision a fresh disposable database for a reviewed new anchor |
| V6 read indexes | Predicate-aligned report, date/operator and evidence indexes | Added storage/write and index-build locking cost; tiny fixture plans do not establish load capacity. Revisit with measured real workloads, never fabricated timings |
| Service logging | Fixed operation/outcome labels at transaction completion and existing requestId MDC | No arguments, report notes, actor IDs, coordinates, SQL exceptions or secrets in these events; no success label before commit |

See data-contract.md for exact APIs/views; engineer-a-handoff.md for migration recovery and remaining integration; engineer-a-verification.md and evidence/ for actual check and mutation outcomes.
