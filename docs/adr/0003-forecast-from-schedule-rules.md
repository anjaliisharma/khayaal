# ADR 0003: Forecast from schedule rules

- Status: Accepted
- Date: 2026-10-04

## Context

Materialized dose events cover a rolling operational horizon and may be regenerated. A stock forecast may need up to 365 days and must remain correct beyond that horizon.

## Decision

The forecast engine expands the active schedule rule directly from an explicit start instant, consumes usable lots in first-expiring-first-out order, and stops at the first uncovered dose or after 365 days. Planned run-out drives alerts. A separate adherence-adjusted simulation supplies the clearly labelled likely date.

## Alternatives

- Forecast from materialized events: rejected because results would depend on event-generation horizon and job timing.
- Divide balance by average daily use: rejected because irregular schedules, course endings, and lot expiry would be inaccurate.

## Consequences

Forecasts are deterministic and independent of scheduler state. The recurrence logic is shared with dose generation, so its timezone policy is critical and heavily tested. Long simulations are bounded.

