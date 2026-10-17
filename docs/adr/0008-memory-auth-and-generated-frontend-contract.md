# ADR 0008: Memory-only access tokens and generated frontend contracts

- Status: Accepted
- Date: 2026-10-05

## Context

The PWA needs authenticated reloads without making bearer credentials durable or duplicating backend response contracts by hand.

## Decision

Keep the short-lived access token only in JavaScript memory. Restore a session through the rotating httpOnly refresh cookie and coalesce concurrent refresh attempts. Generate TypeScript schemas from Springdoc OpenAPI and derive UI response types from that output. TanStack Query owns remote state and optimistic dose transitions.

## Alternatives

- Local or session storage: rejected because injected scripts could recover tokens after reload.
- Cookie-only API authentication: rejected because it would require a separate CSRF design for all state-changing requests.
- Handwritten API types: rejected because backend changes could silently drift from the UI.

## Consequences

Reloads make one refresh request and access tokens disappear when the page process ends. The refresh cookie remains inaccessible to JavaScript. Contract generation must run whenever API DTOs change; small frontend refinements remain necessary where generated schemas cannot express Java nullability or required record fields accurately.
