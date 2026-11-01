# Architecture and API decisions

Current data-layer and API/access decisions, 31 October 2026.

| Decision | Accepted behaviour | Alternative / cost / reversal |
| --- | --- | --- |
| [ADR 001](adr/001-confirmation-and-flood-freshness.md) | One resident once; deliberate renewal after flood expiry; Clock-based read freshness | Reject request counting and scheduler-owned freshness; accept row-lock/aggregate cost and no full attestation history; revisit on measured contention or approved audit needs |
| [ADR 002](adr/002-derived-disputed-pickups.md) | Preserve completion and testimony, derive exact-date DISPUTED, dated operator attribution | Reject last-writer truth/counter mutation; accept unresolved disagreement/read work; revisit only with an explicit adjudication/audit requirement |
| Report retries | Separate internal UUID key (`Idempotency-Key` header), canonical request hash, original `ReportView` snapshot, 24-hour protection | No key in client JSON, random per-retry server key, live-view replay or generic operation framework; expired reuse is a new intent |
| Session & Bearer auth | Stateless HMAC-SHA256 signed Bearer token (`POST /api/v1/auth/sessions`, 8h TTL), principal-resolved `actorId` via `ActorAccess` | No refresh token or token revocation table in MVP; clients re-authenticate after expiry. Revisit if forced session revocation is required |
| Photo privacy & sanitisation | Resident-only `multipart/form-data` upload (`POST /api/v1/photos`), max 5 MiB, magic-byte JPEG/PNG validation, full pixel re-encode stripping EXIF/GPS/XMP metadata before persistence | Rejects WebP/HEIC/RAW in MVP and re-compresses pixels; raw upload bytes are never persisted or logged |
| Problem details errors | Uniform `application/problem+json` (`type`, `title`, `status`, `code`, `detail`, `instance`, `requestId`, optional `fieldErrors`) across all controllers and security filters | Replaces ad-hoc error payloads; never exposes SQL messages, stack traces, tokens, or private coordinates |
| Demo provisioning | Fixed fictional anchors/UUIDs, insert-only, `local-demo` plus explicit flag, production exclusion; optional runtime `ekowatch.demo.password` hashes demo accounts on startup | No automatic resets, credential guesses, fake assets or evidence refresh. Reprovision a fresh disposable database for a reviewed new anchor |
| V6 read indexes | Predicate-aligned report, date/operator and evidence indexes | Added storage/write and index-build locking cost; tiny fixture plans do not establish load capacity. Revisit with measured real workloads, never fabricated timings |
| Service logging | Fixed operation/outcome labels at transaction completion and existing `requestId` MDC | No arguments, report notes, actor IDs, coordinates, SQL exceptions or secrets in these events; no success label before commit |

See `data-contract.md` and `contracts/openapi.yaml` for exact APIs/views; `engineer-a-handoff.md` for migration recovery and integration details; `engineer-a-verification.md` and `evidence/` for actual check and mutation outcomes.
