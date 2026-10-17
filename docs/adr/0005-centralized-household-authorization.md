# ADR 0005: Centralize household authorization

- Status: Accepted
- Date: 2026-10-04

## Context

Every household-scoped operation must enforce both tenant isolation and role permissions. Repeating membership queries in controllers invites inconsistent checks and data leaks.

## Decision

Use one `HouseholdAuthorization` component with explicit membership, writer, and owner guards. Missing membership is reported as not found to avoid revealing another household's existence. Viewers can read; caregivers and owners can mutate care data; only owners create invites. Controllers resolve a resource's household before invoking the appropriate guard.

## Alternatives

- Controller-specific checks: rejected as repetitive and error-prone.
- Encode household roles directly in JWTs: rejected because membership changes would remain stale until token expiry.

## Consequences

Authorization rules are reviewable and reusable. Each request performs a current membership lookup; caching can be added later if measurement justifies it.
