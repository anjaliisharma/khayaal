# Project progress

## Phase status

| Phase | Status | Notes |
|---|---|---|
| 0 — Foundation | Complete | Frontend checks pass; the user verified the full Docker Compose stack locally. |
| 1 — Pure engines | Complete | All 24 backend tests pass. Line coverage: schedule 93.7%, forecast 97.1%, adherence 100%; Maven now enforces at least 90% per package. |
| 2 — Identity and core domain | Complete | All 26 backend tests pass, including PostgreSQL Testcontainers coverage for JWT refresh rotation, household authorization, schedule versioning, timezone handling, and idempotent dose generation. |
| 3 — Dose actions and stock | Complete | All 28 backend tests pass, including concurrent caregiver confirmation, stable idempotency replay, additive purchases, one-time stock deduction, and compensating undo entries. |
| 4 — Notification outbox | Complete | All 29 backend tests pass. Idempotent planning, PostgreSQL SKIP LOCKED dispatch, retry/dead handling, Mailpit email, Telegram linking, discreet mode, quiet hours, and time-boundary behavior are verified. |
| 5 — Frontend core | Complete | Auth and silent refresh, generated OpenAPI types, role-aware routes, invitation acceptance, Today, Family, Person, and medicine schedule builder/preview are implemented. All 7 frontend tests and all 4 Playwright desktop/mobile journeys pass. |
| 6 — Product UI and PWA | Implementation complete; verification pending | Stock/forecast, first purchase and refill undo, reorder copy/share/buy, adherence, PRN logging, household roles/invites, settings/test notifications/export/delete, printable list, route splitting, installable PWA and offline messaging are implemented. Host Lighthouse and final Playwright evidence remain. |
| 7 — Hardening and performance | Implementation complete; verification pending | Request IDs, Redis-backed distributed sensitive-write rate limiting, secure-cookie configuration, operational metrics, security/deletion/stock tests, set-based Stock overview, 20/150/100k/10k synthetic fixture, k6 and two-worker recovery automation, CI e2e, architecture/scaling docs and 8 ADRs exist. Docker-backed tests and measured performance/reliability evidence remain. |

## Phase 0 decisions

- Standardized the product and all technical identifiers on `Khayaal`/`khayaal` before Phase 1.
- Modular monolith backend with package-level domain boundaries and a separate React PWA.
- PostgreSQL and Flyway own the production schema; H2 PostgreSQL mode supports lightweight context tests.
- Phase 0 initially used route placeholders; all required routes now have real server-backed behavior.
- CORS is configured from one explicit environment-provided origin with credentials support.
- PWA precaching provides the shell only; offline writes are not implemented.

## Phase 1 decisions

- Pure calculations use immutable records and stateless, framework-free engines with every time, zone, range, and balance supplied explicitly.
- Schedule expansion uses an inclusive start/exclusive end instant range. DST gaps move to the first valid local time; overlaps use the earlier offset.
- Actual stock uses quarter-unit validation and FEFO consumption. Adherence-adjusted forecasting uses separate expected-balance state so statistical fractions cannot enter the real inventory model.
- Planned run-out drives reorder dates; the adherence-adjusted result is informational only.
- Skipped doses are reported separately and excluded from adherence percentages and heatmap completion buckets.

## Phase 2 decisions

- Access tokens are short-lived signed JWTs; opaque refresh tokens are hashed at rest, rotated under a row lock, and sent only in an httpOnly SameSite cookie.
- Household membership and role checks are centralized. Non-members receive a not-found response to avoid disclosing tenant existence.
- Schedule edits append a version, close the prior version, and regenerate only future pending events from an explicit effective instant.
- Dose events are materialized immediately and nightly over a rolling 14-day horizon; a database uniqueness constraint makes generation idempotent.
- PostgreSQL Testcontainers covers real migration syntax, token rotation, role/isolation rules, schedule history, timezone preservation, and duplicate generation.

## Phase 3 decisions

- Dose state transitions, idempotency snapshots, audit records, FEFO allocation, and stock ledger entries share one PostgreSQL transaction.
- Row locks serialize confirmations per dose; a concurrent second confirmation returns the existing result and never deducts stock twice.
- Ledger entries are append-only. Undo and purchase void operations append compensating entries, while lot balances are allocation state.
- Positive adjustments create traceable undated lots; negative adjustments and discards consume usable lots in FEFO order so forecasts and ledger balances stay aligned.
- A purchase closes active reorder requests for its medicine. The default purchase-void window is 24 hours and consumed lots cannot be voided.

## Phase 4 decisions

- PostgreSQL is the durable notification queue; deterministic dedupe keys make repeated planning safe.
- Dispatchers claim batches with `FOR UPDATE SKIP LOCKED`; stale processing claims can be recovered after five minutes.
- Channel delivery happens outside the claim transaction. Failures store only an error class/code, use exponential backoff, and become dead after the configured maximum.
- Dose notification relevance is checked again immediately before delivery so confirmation suppresses queued due and follow-up alerts.
- Discreet message bodies are materialized before enqueueing and never include medicine names. High-priority dose and critical-stock alerts bypass quiet hours.

## Phase 5 decisions

- Access tokens remain in memory; the httpOnly refresh cookie restores sessions without exposing refresh credentials to JavaScript.
- OpenAPI-generated schemas are the source for response types, with narrow frontend refinements for required and nullable fields that Spring's schema cannot infer.
- TanStack Query owns server state. Dose actions update only the selected card optimistically, roll back on failure, and reconcile from the server afterward.
- Write routes and controls enforce household roles in the UI, while backend authorization remains the security boundary.
- Schedule entry is explicit user input with a server-generated preview and plain-language summary; the UI never suggests or validates a medical dose.

## Assumptions

- The repository is named `Khayaal` beneath the requested `Switch Preparation` folder.
- pnpm is the frontend package manager so installs are deterministic and efficient.
- Phase 0 may show empty route shells but must not invent medical records or workflow behavior.
- A stock lot remains usable on its expiry date and becomes unusable the following day.

## Phase 6–7 decisions

- Stock overview returns the same schedule-aware forecast used by medicine stock details so urgency sorting does not duplicate forecasting logic in the browser.
- Pack quantities are positive whole units, while prescribed dose quantities may use quarter units. Dose confirmation consumes only available lots and records any unmet quantity as a shortage, so physical stock never becomes negative.
- Data export is authenticated JSON generated from household membership joins; it cannot export another household's records.
- Account deletion purges sole-member owned households and removes channel/membership data while retaining only a non-identifying tombstone for immutable audit and stock actor references.
- Operational rate limiting uses Redis atomic counters with expiring fixed-window keys, providing consistent enforcement across multiple backend instances. Limits, key TTL, and fail-open behavior are environment-configurable.
- Performance thresholds remain requirements, not claims, until k6 and `EXPLAIN ANALYZE` evidence is captured on a named machine and commit.

## Known gaps

- Domain tables and features begin in Phase 1/2 by design.
- Authentication and authorization begin in Phase 2.
- The generated OpenAPI schema is checked in; rerun `pnpm run api:generate` after backend contract changes.
- Phase 2 intentionally does not implement dose actions or stock mutations; those begin in Phase 3.
- Phase 3 notification delivery is intentionally deferred; the missed-dose sweep is exposed as an idempotent service for the Phase 4 planner.

