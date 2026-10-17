# ADR 0004: Materialize dose events on a rolling horizon

- Status: Accepted
- Date: 2026-10-04

## Context

Reminders and confirmations need stable event identities, auditability, and idempotent regeneration. Computing occurrences only when requested would make concurrent actions and notification deduplication fragile.

## Decision

Persist scheduled dose events for a rolling 14-day horizon. Generate immediately when a schedule is created or versioned and extend the horizon nightly. A unique constraint on `(medicine_id, scheduled_local)` plus conflict-safe inserts makes repeated generation idempotent. Schedule edits close the previous version, delete only future pending events from the effective instant, and regenerate from the new version. Past and acted-on events remain unchanged.

## Alternatives

- Compute occurrences on every read: rejected because occurrences would not have durable identities.
- Materialize the entire course: rejected because open-ended schedules would create unbounded data.

## Consequences

Operational queries and notification planning stay simple. A scheduled maintenance job is required, and schedule edits must carefully preserve history.
