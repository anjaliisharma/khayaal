# ADR 0006: Transactional dose actions and an append-only stock ledger

## Context

Two caregivers may confirm the same dose concurrently. A mutable stock counter could be decremented twice, lose history, or drift from individual stock lots.

## Decision

Dose actions lock the dose row and execute the status transition, FEFO lot allocation, ledger writes, audit event, and idempotency result in one database transaction. The first valid confirmation owns the transition; later confirmations return the persisted result without another deduction. Idempotency responses are snapshotted so replay remains stable after later actions.

Stock is derived from immutable ledger deltas. Purchases and positive adjustments create lots. Taken doses, discards, and negative adjustments consume locked lots in first-expiring-first-out order. Undo and purchase voiding append compensating entries rather than editing history. Lot balances are maintained as allocation state, while the ledger remains the financial source of truth.

## Alternatives

- Optimistic retries: simpler reads, but more complex multi-lot conflict recovery.
- A mutable balance column: faster reads, but weak auditability and reconciliation.
- Distributed locks: unnecessary while PostgreSQL is the consistency boundary.

## Consequences

Actions serialize per dose and lot set, remain explainable, and can be audited. Transactions are slightly longer and indexes must support the locking order.
