# ADR 001: Unique resident confirmations and derived flood freshness

Date: 2026-10-31. Status: implemented data-layer decision; Esther's integration/sign-off remains outstanding. This does not record approval from an absent reviewer.

A confirmation represents one resident, not a count of requests. `uq_report_confirmation_resident(report_id,resident_id)` is the durable invariant. ReportApi provides the internal row-lock collaboration; ConfirmationService takes the report lock before evidence writes, checks RESIDENT and rejects the reporter's self-confirmation. READ COMMITTED permits a waiting writer to see evidence committed by the preceding writer. First attestation inserts once; repeats are no-ops except an expired flood can deliberately renew that resident's confirmed_at. Capture transaction-entry time so a request that began while current does not turn into a renewal merely by waiting.

Flood freshness is read-derived: lastEvidenceAt is max(report.createdAt, confirmation timestamps), expiresAt adds the configured freshness duration (default six hours), and CURRENT requires server time strictly before expiry. Equality is EXPIRED. Non-flood views have null floodStatus/expiresAt. GET never updates evidence. One-hour outages need no catch-up worker. A later post-expiry confirmation is new testimony; clients must not endlessly replay a past action across expiry windows.

Rejected alternatives: count every request; reject all duplicate retries; store mutable CURRENT/EXPIRED lifecycle state; refresh evidence on every GET or immediate retry; run a scheduler to expire reports. These either inflate people, break retry safety, create stale state or fabricate testimony. A separate evidence-history table is not part of the agreed MVP.

Accepted cost: confirmations for a popular report serialize behind a row lock, reads aggregate actual rows, and only each resident's latest testimony time is stored. This is not a full evidence audit history. The index on report_id/confirmed_at helps evidence reads but adds write/storage cost. No load capacity is claimed from the small fixture.

Reversal observation: if measured contention becomes material, preserve the unique-person and deliberate-renewal invariant while evaluating a narrower locking strategy. If an approved audit/moderation requirement needs every attestation, introduce an explicit history model and retention policy in a forward migration. Do not silently reinterpret retries or keep a flood current without new testimony.

Evidence: ConfirmationFreshnessTest, canonical concurrent service tests, and InvariantEvidenceTest's same real-table assertion with/without uniqueness. See engineer-a-verification.md for actual commands/outcomes; the negative mutation is outside normal verify and touches only its own disposable container.
