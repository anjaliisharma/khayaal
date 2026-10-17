# ADR 0002: Keep deterministic calculations in pure domain engines

- Status: Accepted
- Date: 2026-10-04

## Context

Schedule expansion, stock allocation, forecasting, and adherence rules contain the product's highest-risk calculations. Coupling them to Spring, persistence, HTTP, or the system clock would make edge cases slower to test and harder to explain.

## Decision

Represent inputs and outputs with immutable Java records and implement calculations in stateless, framework-free services. Time, timezone, ranges, and stock are explicit inputs. ArchUnit prevents dependencies from the pure packages to Spring or JPA. Strategy-specific behavior—recurrence matching and FEFO allocation—remains behind focused classes rather than a generic framework.

## Alternatives

- Put calculations in application services: rejected because orchestration and deterministic rules would become entangled.
- Use persistence entities directly: rejected because lazy state and mutable lifecycle concerns would leak into calculations.

## Consequences

Tests are fast and deterministic, and application services can compose the engines transactionally later. Mapping is required at package boundaries, and callers must supply an explicit clock-derived instant.

