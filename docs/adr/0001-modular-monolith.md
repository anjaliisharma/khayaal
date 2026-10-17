# ADR 0001: Modular monolith with a separate PWA

- Status: Accepted
- Date: 2026-10-03

## Context

Khayaal has several business areas with strict correctness requirements, but a portfolio-sized team and deployment. Independent services would add network failure modes, duplicated security, distributed transactions, and operational cost before those trade-offs are justified.

## Decision

Build the Java backend as one Spring Boot deployable split into explicit domain packages. Keep schedule, forecast, and adherence calculations pure and framework-free, enforced by ArchUnit. Build the React PWA as a separate deployable client against an OpenAPI contract. PostgreSQL is the single durable store.

## Alternatives

- Microservices: rejected for v1 because operational and consistency costs outweigh independent scaling benefits.
- One undivided package: rejected because domain boundaries and pure engines would erode.
- Server-rendered UI: rejected because the installable, responsive offline shell is a core product requirement.

## Consequences

Transactions and local development stay simple, while package boundaries leave room for later extraction. The team must actively enforce boundaries; package structure alone is insufficient. Backend and frontend still require explicit API compatibility checks.

