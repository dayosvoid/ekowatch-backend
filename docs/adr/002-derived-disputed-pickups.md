# ADR 002: Preserve facts and derive disputed pickups

Date: 2026-10-31. Status: implemented data-layer decision; Esther's integration/sign-off remains outstanding.

A dated schedule occurrence is the unit of pickup accounting. Preserve a completion and every MISSED_PICKUP report as independent facts. Derive DISPUTED only when both exist for the exact street_id/collection_date. A completion without that report is DONE. An elapsed uncompleted occurrence is MISSED; an unelapsed uncompleted occurrence remains SCHEDULED. Calendar boundaries use Africa/Lagos and the injected Clock. No stored status, counters, background reconciliation or dispute-adjudication workflow is introduced.

Authority missedPickupCount excludes disputed completions; disputedPickupCount is separate. Operator completedPickupCount includes disputed completions. Attribute performance to operator_id stored on the dated schedule, not whoever is currently assigned to the ward. Requested pickup ranges are inclusive and contain at most 31 days. Hazard counts are a separate generatedAt snapshot of reports, independent of the pickup date range; blocked drains count reports and expired floods are excluded.

Rejected alternatives: delete or hide a resident report when an operator completes; erase the completion when a resident reports; let the last writer decide truth; count each witness as a separate missed occurrence; attribute historical work to an arbitrary current assignee. Each loses evidence or gives misleading counts. Reports do not make today's unelapsed schedule overdue.

Accepted cost: the product exposes unresolved disagreement, and reads join/aggregate actual facts rather than cheap mutable counters. DISPUTED does not prove either party correct. The exact-date partial report index reduces lookup work at a storage/write cost. Concurrent responses can precede the other transaction's commit; subsequent reads converge on both committed facts without rewriting either.

Reversal observation: change this model only after an explicit approved requirement for adjudication, correction permissions and an audit trail. An operational demand to resolve disputes is evidence for a new workflow, not permission to discard existing testimony. If measured read cost warrants materialisation, define transactional consistency and rebuild semantics before introducing counters.

Evidence: CollectionRulesTest tests overlapping completion/report transactions in both commit orders, exact-date separation and stable facts; DemoSeedTest checks the canonical dashboard count definitions. Authority's removed-role-check experiment is service-layer evidence; Esther still owes HTTP wrong-role tests.
