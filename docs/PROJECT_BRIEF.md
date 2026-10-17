# Khayaal: Family Medicine and Refill Tracker. Master Prompt for AI-Assisted Development (Full-Stack)

> This is the complete project brief. Read all of it before writing any code. "Khayaal" is the project name.
> Work in phases (Section 14) and follow the Session protocol in Section 15.

---

## 1. Role and working agreement

You are a senior full-stack engineer (Java backend, React frontend) pair-programming with me. I am a backend developer with 4+ years of experience building a portfolio project for SDE2 / backend / full-stack interviews. Rules:

- Build **incrementally by phase**. Never generate the whole project at once.
- Before each phase, restate the plan in a few lines, then implement.
- Prefer **simple, explainable** designs. I must be able to defend every line in an interview.
- Write **tests alongside code**, not after.
- Every non-trivial design choice gets a short entry in `docs/adr/` (context, decision, alternatives, consequences).
- If a requirement is ambiguous, state your assumption in one line and continue.
- Keep responses focused: code, brief trade-off notes, how to run and test.
- Everything must be **free and open source**. No paid APIs or services.
- The **UI is a first-class deliverable**. Users include older family members and stressed caregivers, so it must be simple, large-target, and clear.
- **Safety rule (non-negotiable):** this app is a reminder and inventory tool. It must **never** suggest, calculate, or validate medical doses, never check drug interactions, and never give medical advice. All medicine details (name, strength, instructions) are free text entered by the user. Show a clear disclaimer in the UI and the README.

## 2. Problem statement

In many households one person manages medicines for parents, grandparents, a spouse, or kids. Reminders live in phone alarms with no record of whether a dose was actually taken, and nobody notices a strip running out until the night it's needed. Caregivers living in a different city can't tell whether a parent took their morning tablets.

**Khayaal** lets a household register family members and their medicines, define flexible schedules, and then:

1. **Reminds** the right people at dose time (and follows up if nothing is confirmed).
2. Records each dose as **taken, skipped, or missed**, with who confirmed it.
3. Tracks **stock** with an append-only ledger and **forecasts the run-out date**, alerting the caregiver to reorder *before* it runs out.
4. Shows **adherence** per person and per medicine.
5. Lets multiple caregivers share one household with roles.
6. Lets users record **purchases** that add to existing stock, and helps them reorder.

### Headline requirements (must-haves, traceable)
- **R1: Purchases add to existing stock.** A user can record buying a medicine (quantity, price, pharmacy, batch and expiry optional). The purchased units are **added to the existing stock** of that medicine (never overwriting it), and the run-out forecast updates immediately. A user can also add a **brand-new medicine** together with its first purchase in one step.
- **R2: On-time dose alerts.** Every scheduled dose triggers an alert **at its scheduled time** to the right people, a follow-up if it is not confirmed, and a missed-dose alert to caregivers.
- **R3: Run-out alert 3 days ahead.** When a medicine's stock will run out within **3 days** (default, configurable per medicine), caregivers get a low-stock alert, with further reminders until a purchase is recorded.
- **R4: Reorder help without real ordering.** The app helps the user buy (reorder list, shareable message, a pharmacy link of their choice, an "ordered, arriving by" status) but does **not** place orders or take payments itself (see Section 3).

### Core design principles
- **Schedule expansion, stock forecasting, and adherence calculations are pure, deterministic, unit-tested logic**, separated from web, DB and notification layers.
- **Reminders must be reliable and never duplicated.** Notification delivery is at-least-once with deduplication keys, so the user effectively gets each reminder once.
- **Stock is a ledger, not a number.** The current balance is always derived from entries, so every change is auditable and reversible.

### Primary users
A caregiver managing family medicines (the builder is the first user); a family member who only confirms their own doses; a remote relative with read-only visibility.

## 3. Scope

- **In scope (v1):** auth, households and invites, family members (care recipients), medicines with flexible schedules, dose events, reminders (email + Telegram, optional web push), purchase recording with stock lots (batch and expiry), stock ledger and run-out forecast, 3-day low-stock alerts, on-time dose alerts, missed-dose alerts to caregivers, reorder list and "ordered" tracking, adherence stats, printable medicine list, **complete web UI as an installable PWA**.
- **Out of scope (v1):** dose recommendations, interaction or allergy checking, drug databases, prescription scanning/OCR, **in-app ordering, payments or delivery** (these need licensed pharmacy partners, payment gateways and prescription compliance, and are not free; the app only records purchases the user made elsewhere and helps them reorder), wearable or smart-pill-box integration, native apps, billing.
- **Privacy:** health data is sensitive. Store only what the user enters, support a **discreet notification mode**, keep an audit trail, and provide full account/household deletion.

## 4. Domain model and vocabulary

- **Household:** a group of caregivers and the people they care for. Has a default timezone.
- **Member (user) roles:** `OWNER` (full control, manages members), `CAREGIVER` (manage medicines, confirm doses, log refills), `VIEWER` (read-only).
- **Care recipient:** a person whose medicines are tracked (e.g. "Papa"). May optionally be linked to a user account (so they can confirm their own doses). Has a timezone (defaults to the household's).
- **Medicine:** belongs to a recipient. Fields: name, strength (free text), form (tablet, capsule, syrup, drops, injection, other), `unitsPerDose` (supports quarter steps, e.g. 0.5 tablet), free-text instructions, start date, optional end date (course length), as-needed (`PRN`) flag, active flag, `leadTimeDays` for reorder alerts (default 3), optional expiry date of the current pack.
- **Schedule rule:** when a (non-PRN) medicine is due. Types: `DAILY` (list of local times), `WEEKDAYS` (days + times), `EVERY_N_DAYS` (N + anchor date + times). Rules are **versioned** (`effective_from`/`effective_to`) so edits don't rewrite history. Optional meal labels (breakfast/lunch/dinner/bedtime) map to user-defined local times.
- **Dose event:** one scheduled occurrence: `scheduled_at` (UTC instant), `scheduled_local` (local date-time), status `PENDING`, `TAKEN`, `SKIPPED`, `MISSED`; `acted_at`, `acted_by`, optional note. PRN doses are created on demand when logged.
- **Stock ledger entry:** append-only: `delta` (units), `reason` (`PURCHASE`, `DOSE_TAKEN`, `DOSE_UNDO`, `ADJUSTMENT`, `DISCARD`, `EXPIRED`), optional link to a stock lot and a dose event, timestamp, actor. **Balance = sum of deltas** (the usable balance excludes expired lots).
- **Purchase:** a record of buying a medicine: pack count and units per pack (giving total units), price (optional, `NUMERIC`), pharmacy (free text, optional), purchase date, optional batch number and expiry date, note. Recording a purchase creates a stock lot and a `PURCHASE` ledger entry that **adds to the existing balance**. "Refill" in this document simply means a purchase of an existing medicine.
- **Stock lot:** one purchased batch with units remaining and an optional expiry date. Doses consume lots **first-expiring-first-out (FEFO)**; lots without an expiry are consumed after dated ones, oldest purchase first.
- **Reorder request:** a lightweight record that the user has ordered a medicine elsewhere ("ordered, expected by <date>"). It pauses repeat low-stock reminders until the expected date and closes automatically when a purchase is recorded.
- **Notification:** an outbox row describing a message to send to a user on a channel, with a dedupe key, status and retry state.

**Numeric rule:** never use floating point for unit quantities. Use `NUMERIC(8,2)` / `BigDecimal` with validation to quarter steps (0.25).

## 5. Functional requirements (backend)

### FR-1 Accounts, households, roles
- FR-1.1 Register, log in, refresh, logout (JWT; refresh token as httpOnly SameSite cookie; access token short-lived).
- FR-1.2 Create a household; invite others via a single-use, expiring **invite link/token** with a chosen role.
- FR-1.3 Role enforcement on every endpoint (tested): viewers cannot write; caregivers cannot manage members; users can access only households they belong to.
- FR-1.4 Leave household, remove member, transfer ownership, delete account (deletes personal data; household data handled per documented rule).
- FR-1.5 User notification preferences: channels, discreet mode, quiet hours (non-critical only), which recipients they get reminders for.

### FR-2 Care recipients and medicines
- FR-2.1 CRUD for care recipients (name, optional birth year, timezone, optional linked user, notes).
- FR-2.2 CRUD for medicines per FR section 4. Archive instead of hard delete when history exists.
- FR-2.3 Schedule rule builder supports the three rule types, up to a sensible maximum of times per day (configurable, default 6), start/end dates, and a **course** shortcut ("for 7 days").
- FR-2.4 **Editing a schedule creates a new rule version effective from a chosen moment**; past dose events remain untouched; future PENDING events are regenerated.
- FR-2.5 `GET .../schedule-preview` returns the next N days of expected doses for a rule *without saving* (used by the UI builder).
- FR-2.6 PRN medicines have no scheduled events; users log "taken now" entries. Optional user-entered `maxPer24h` (copied from the label or doctor's note) triggers a **notice** when exceeded. The app does not recommend or validate this number.
- FR-2.7 Optional pack expiry date triggers an alert N days before expiry.

### FR-3 Dose events
- FR-3.1 **Materialize** dose events on a rolling horizon (default 14 days ahead) via a nightly job and immediately on rule create/edit. Generation is **idempotent** (unique on medicine + scheduled_local).
- FR-3.2 Timezone correctness: scheduled local times resolve through the recipient's IANA timezone; DST gaps and overlaps are handled deterministically (documented and tested), even though India has no DST.
- FR-3.3 Actions: `TAKEN` (optionally with an actual time), `SKIPPED` (with optional reason), **undo** (back to PENDING, with a compensating stock entry).
- FR-3.4 Transition rules: `PENDING -> TAKEN | SKIPPED`; `MISSED -> TAKEN` allowed ("took it late"); `TAKEN -> PENDING` via undo within a configurable window; invalid transitions return a clear error.
- FR-3.5 **Idempotent and concurrency-safe:** actions require an `Idempotency-Key`; if two caregivers confirm the same dose at once, the first wins and the second receives the existing result showing who confirmed.
- FR-3.6 After a grace period (default 120 minutes, per-medicine override) a still-PENDING dose becomes `MISSED` automatically. **MISSED doses do not deduct stock.**
- FR-3.7 Every action is written to an **audit log** (who, what, when, from which client).

### FR-4 Stock ledger and forecast
- FR-4.1 **Purchases (R1):** a purchase for an existing medicine creates a stock lot and a `PURCHASE` ledger entry that **adds to the existing balance** (never overwrites it). Also supported: manual adjustment, discard, and automatic `EXPIRED` entries when a lot passes its expiry date. Everything is an append-only ledger entry.
- FR-4.1a **Add a new medicine with its first purchase:** a single request creates the medicine, its schedule and the first purchase atomically, and returns the forecast.
- FR-4.1b Purchases can be voided within a configurable window (void appends a compensating entry and removes the lot if untouched; if doses have already consumed from the lot, only correction through an adjustment is allowed).
- FR-4.1c Optional spend tracking: monthly total spent per person and per medicine from purchase prices (informational only).
- FR-4.2 `TAKEN` doses append negative `DOSE_TAKEN` entries consuming lots in FEFO order (a dose may span two lots, giving one entry per lot; unique on dose event + lot, for idempotency); undo appends compensating `DOSE_UNDO` entries to the same lots. Expired lots are excluded from the usable balance and from the forecast, and an `EXPIRED` entry is recorded.
- FR-4.3 Balance is always computed from the ledger (with a periodically maintained snapshot allowed purely as an optimization, verified by tests to equal the sum).
- FR-4.4 **Run-out forecast** (pure function): given the schedule rules (not the materialized events), the current balance, the start instant, and an end-of-course date, simulate upcoming doses in chronological order and return:
  - `runOutDate`: the date of the first dose that cannot be fully covered (or `null` if stock covers the whole course; cap the simulation at 365 days);
  - `daysLeft`, `lastCoveredDate`;
  - `expectedRunOutDate`: an adherence-adjusted estimate using the trailing 14-day taken ratio (shown as "likely"; reorder alerts use the earlier, planned date).
- FR-4.5 **Run-out alerts (R3):** `reorderBy = runOutDate - leadTimeDays`, with `leadTimeDays` **defaulting to 3** (configurable per medicine and per household). Send a **low-stock alert** the first time `daysLeft <= leadTimeDays`, then a daily reminder until a purchase is recorded, the medicine is archived or its course ends, or a reorder request is active. Send a **critical** alert when stock will not cover tomorrow's first dose and when a dose cannot be fully covered ("runs out today"). Alerts are deduplicated by (medicine, runOutDate, stage), so a changed forecast triggers a fresh alert instead of a repeat. The forecast is re-evaluated after every purchase, adjustment, schedule change, and nightly; a purchase that moves `runOutDate` beyond the lead time closes the alert.
- FR-4.6 **Known-value forecast test:** balance 10, 1 unit per dose at 08:00 and 20:00 daily, as of Jan 1 07:00 gives `lastCoveredDate = Jan 5`, `runOutDate = Jan 6`; with `leadTimeDays = 3`, `reorderBy = Jan 3`.
- FR-4.7 **Reorder support (R4):** the app produces a per-household **reorder list** of medicines at or below their lead time (name, strength, units needed to cover a chosen number of days, days left). Users can copy or share it as text (for example into a WhatsApp message or a call to the pharmacy) and mark an item **"Ordered, expected by <date>"**, which creates a reorder request. The app never places orders or handles payments.
- FR-4.8 **Pharmacy link (optional):** users may save a pharmacy search-URL template (for example `https://example-pharmacy.com/search?q={name}`) in settings. The "Buy" button opens that link in a new tab with the medicine name filled in. No pharmacy is hard-coded, no API keys are needed, and no purchase happens inside the app.
- FR-4.9 **Additional known-value tests:** (a) existing balance 4 plus a purchase of 10 gives 14 and moves `runOutDate` accordingly; (b) two lots (A expires Mar 1 with 3 units, B expires Jun 1 with 10 units): two consecutive 2-unit doses consume A:2, then A:1 + B:1; (c) a lot expiring Feb 1 is excluded from the forecast after that date; (d) with `leadTimeDays = 3` and `runOutDate = Jan 6`, the first alert is generated on Jan 3 (not Jan 2) and then at most once per day until a purchase is recorded.

### FR-5 Reminders and notifications
- FR-5.1 **Notification outbox:** all outgoing messages are rows in a `notifications` table with `dedupe_key` (unique), channel, type, payload, status (`PENDING`, `SENT`, `FAILED`, `DEAD`), `attempts`, `next_attempt_at`.
- FR-5.2 **Planner (scheduler tick, e.g. every 30 s)**, all idempotent through dedupe keys:
  - enqueue `DOSE_DUE` for events whose time has arrived;
  - enqueue one `DOSE_FOLLOWUP` if still PENDING after N minutes (default 15);
  - mark overdue events `MISSED` and enqueue `DOSE_MISSED` to caregivers who opted in;
  - nightly/daily (per household local time): evaluate forecasts and enqueue `LOW_STOCK` and `EXPIRY` alerts; optional `DAILY_SUMMARY`.
- FR-5.3 **Dispatcher:** workers claim due rows with `SELECT ... FOR UPDATE SKIP LOCKED`, send via the channel adapter, and mark SENT; failures retry with exponential backoff and become `DEAD` after a maximum (alerting via metrics/logs). Safe with multiple instances.
- FR-5.4 **Channels** behind a `NotificationChannel` interface: email (SMTP; **Mailpit** in dev), **Telegram Bot API** (link account via a one-time code), and optional **Web Push** (VAPID; verify current browser/OS support and the chosen library's license).
- FR-5.5 **Discreet mode:** messages avoid medicine names ("Time for Papa's 8:00 AM medicines"); full names shown only inside the app.
- FR-5.6 Quiet hours **defer** non-critical alerts (summaries, routine low-stock reminders) until quiet hours end; they never suppress dose alerts or critical run-out alerts.
- FR-5.7 Reminders include a deep link to the Today screen, and Telegram messages offer "Taken" / "Skip" inline buttons mapped to the same idempotent action endpoint (optional stretch).
- FR-5.8 **On-time dose alerts (R2), guarantees:** (a) a `DOSE_DUE` alert is generated at the scheduled local time (p95 delay < 60 s); (b) an optional pre-alert N minutes before (default off); (c) one follow-up if the dose is still PENDING after N minutes (default 15); (d) a `DOSE_MISSED` alert to opted-in caregivers when the grace period ends; (e) alerts go to every user subscribed to that person, on each of their enabled channels, and **stop** once any caregiver confirms the dose; (f) alerts use the recipient's timezone; each alert is deduplicated per dose event, type, user and channel.
- FR-5.9 **Alert types and priority:** `DOSE_DUE`, `DOSE_FOLLOWUP`, `DOSE_MISSED`, `LOW_STOCK` (3-day), `OUT_OF_STOCK_CRITICAL`, `EXPIRY`, `DAILY_SUMMARY`. Dose alerts and critical run-out alerts are high priority (retried faster, never deferred).

### FR-6 Adherence and history
- FR-6.1 Per recipient and per medicine: taken-on-time %, taken-late %, skipped, missed, for the last 7/30/90 days.
- FR-6.2 Calendar heatmap of days fully completed, partially completed, or missed.
- FR-6.3 Current streak of fully-completed days; list of most-missed times of day.
- FR-6.4 History list of dose events with who confirmed each.
- FR-6.5 Counting rules are documented (e.g. SKIPPED is excluded from the "missed" percentage but shown separately).

### FR-7 Export and printable list
- FR-7.1 Export household data as JSON and dose history as CSV.
- FR-7.2 A **printable medicine list** per recipient (name, strength, form, instructions text, schedule in plain words, current stock) for doctor visits. Rendered as a print-friendly page in the UI, with a disclaimer that the information is user-entered.

## 6. Frontend requirements (mandatory)

### 6.1 Stack and conventions
React 18 + TypeScript (strict) + Vite, React Router, **TanStack Query**, React Hook Form + Zod, Tailwind CSS with **shadcn/ui**, **Recharts**, lucide-react. Typed API client generated from the OpenAPI spec (`openapi-typescript`). **PWA** (installable, with a service worker; web push optional). Vitest + React Testing Library + MSW for component tests; **Playwright** for end-to-end tests.

### 6.2 Routes and screens

| Route | Screen | Purpose |
|---|---|---|
| `/login`, `/register`, `/invite/:token` | Auth and invite acceptance | Simple forms |
| `/` | **Today** | Everyone's doses for today, grouped by time |
| `/people` | **Family** | List of care recipients with status at a glance |
| `/people/:id` | **Person Detail** | Their medicines, today's doses, adherence |
| `/medicines/new`, `/medicines/:id` | **Medicine Form / Detail** | Create and edit, schedule builder, stock history |
| `/stock` | **Stock and Refills** | Days-left for every medicine, quick refill logging |
| `/adherence` | **Adherence** | Charts and heatmaps |
| `/people/:id/print` | **Printable list** | Print-friendly medicine list |
| `/household` | **Household** | Members, roles, invites |
| `/settings` | **Settings** | Notifications, discreet mode, timezone, Telegram link, export, delete |

### 6.3 Screen specifications

**Today** (hero screen)
- Timeline grouped by time slot ("8:00 AM"), each with the people and medicines due. Overdue and missed items are visually distinct and appear at the top.
- Each dose card: person avatar/initial, medicine name and strength, units to take, instructions text. **Large "Taken" and "Skip" buttons** (minimum 48 px touch targets); a secondary "Took it at a different time" option.
- Shows who confirmed a dose and when ("Taken by Riya, 8:12 AM"). **Undo** is available briefly after confirming.
- Filter chips by person; "My doses only" toggle for users linked to a recipient.
- Optimistic updates with rollback and a toast on failure; live refresh when returning to the tab.
- A banner at the top lists medicines running out within their lead time (tap to open Stock) and any critical out-of-stock warnings.
- Empty state: "Nothing due right now" with the next upcoming dose time.

**Family / Person Detail**
- Cards per person with: doses done today (e.g. 3/5), next dose time, medicines running low (red/amber chips), and recent adherence.
- Person Detail lists medicines, today's doses, and adherence for the last 7 and 30 days.

**Medicine Form / Schedule Builder**
- Fields per Section 4 with sensible defaults (form type, units step 0.25).
- **Schedule builder with a live preview** of the next 7 days of doses (from `schedule-preview`), plain-language summary ("Every day at 8:00 AM and 8:00 PM for 14 days").
- Editing a schedule asks "Apply from now" or a chosen date and shows what will change.
- PRN toggle replaces the schedule section with a quick "Log a dose now" button.
- **Initial stock step** when creating a medicine: record the first purchase (or skip) so the forecast is available immediately. An "Add purchase" action on the detail page records further purchases that add to the existing balance.
- Detail view shows rule history, stock balance with the ledger (refills, doses, adjustments), and forecast.

**Stock and Refills**
- One list of every active medicine across the household, sorted by urgency. Each row: days left, a progress bar colored by threshold (with icon and text, never color alone), run-out date, "reorder by" date, the likely run-out date when it differs, and an expandable lot breakdown (units and expiry per lot).
- **"Add purchase"** button on each row opens a short form (packs, units per pack, price, pharmacy, optional batch and expiry, date defaulting to today) with a live preview such as "Adds 30 units to your current 4; new run-out date Feb 12" before saving. Undo available.
- **"Buy"** button on low-stock rows: opens the saved pharmacy link or copies a reorder message. **"Mark as ordered"** records the expected delivery date, which pauses repeat reminders.
- **Reorder list** tab: every medicine at or below its lead time with units needed for a chosen number of days (default 30), "Copy / Share list", and bulk "Mark as ordered".
- **"Add new medicine"** shortcut opens the medicine form with an initial purchase step.
- Banner for anything already past its reorder date, plus a distinct critical banner for medicines that will not cover tomorrow's doses.

**Adherence**
- Per-person selector, 7/30/90-day toggle, taken/late/skipped/missed breakdown, calendar heatmap, most-missed times of day, streak.

**Household**
- Member list with roles, change role/remove (owner only), invite creation with expiry and copy-link, pending invites.

**Settings**
- Notification channels with test-message buttons, Telegram linking flow, discreet mode, quiet hours, timezone, text-size option, export, delete account with typed confirmation.

**Printable list**
- Clean black-and-white print CSS, one page per person, with the disclaimer footer.

### 6.4 UX and quality requirements
- **Mobile-first and accessible for older users:** large type, large touch targets, high contrast, a text-size setting, minimal steps, plain language (no jargon), clear success feedback after confirming a dose.
- **Accessible:** semantic HTML, labels, visible focus, keyboard-operable, ARIA for custom controls, charts have text alternatives, color is never the only signal (icons and text accompany red/amber/green).
- Dark and light themes; PWA installable with an app icon; offline shell loads with a clear "you're offline, doses will sync when back online" message (offline *writes* are a stretch; if built, replay with idempotency keys).
- Every data view has loading skeletons, empty states and error states with retry.
- Times display in the **recipient's** timezone with the zone shown when it differs from the viewer's.
- Auth: access token in memory, silent refresh via cookie, protected routes, role-aware UI (viewers see no write controls).
- No direct data fetching in components: use query hooks in an `/api` layer.
- Route-level code splitting; Lighthouse: performance >= 90, accessibility >= 95, PWA checks pass on Today.

## 7. Non-functional requirements

| Area | Requirement |
|---|---|
| **Correctness** | `schedule`, `forecast`, `adherence` packages are pure with >= 90% coverage, known-value tests and property tests (e.g. generating events twice yields identical sets; forecast `runOutDate` is monotonic in balance; ledger sum equals balance after random operation sequences; undo restores balance exactly). |
| **Time handling** | All schedule logic uses IANA time zones; tests cover day boundaries, a zone with DST (gap and overlap), and a household whose members are in different zones. |
| **Reminder timeliness** | p95 delay from `scheduled_at` to the notification being handed to the channel < 60 s under normal load. |
| **Delivery semantics** | At-least-once delivery with dedupe so duplicates are never user-visible; retries with backoff; dead-letter state with metrics. |
| **Alert accuracy** | Run-out alerts fire on the correct day for known-value cases (3 days before `runOutDate`), at most once per day until resolved, and are re-evaluated after every purchase, adjustment, schedule change and nightly. Dose alerts are never skipped, duplicated, or sent for already-confirmed doses (tested). |
| **Concurrency** | Simultaneous confirmations by two users on one dose, and concurrent refill + dose entries, never corrupt state (tested with parallel requests). |
| **Performance** | Seed 20 recipients, 150 medicines, 100k dose events. Today and Stock endpoints p95 < 100 ms. Planner tick scans use indexes (no full scans). Dispatcher handles 10,000 queued notifications. Provide a data generator and k6/load test; record results and optimizations. |
| **Security and privacy** | BCrypt, JWT expiry/refresh, httpOnly cookie, CORS restricted to the frontend origin, CSRF consideration documented, per-household isolation enforced and tested on every query, role checks, rate limiting on auth and invite endpoints, invite tokens single-use and expiring, secrets via env vars, no health data in logs, audit log. |
| **Reliability** | Idempotent writes, idempotent schedulers (safe if a tick runs twice or on two instances), graceful shutdown that doesn't lose claimed notifications. |
| **Observability** | Structured logs with request IDs (no medicine names in logs); Micrometer metrics: queue depth, send latency, failures, dead notifications, planner tick duration, missed-dose count; health endpoint. |
| **Maintainability** | ArchUnit rules (pure packages have no Spring/DB deps); OpenAPI as the contract; ESLint + Prettier; ADRs. |
| **Portability** | `docker compose up` starts frontend, backend, Postgres, Mailpit; one command each for backend tests, frontend tests and e2e; CI on GitHub Actions. |
| **Cost** | Zero. |

## 8. Technology stack (all free)

**Backend:** Java 21 (Temurin), Spring Boot 3.x, Spring Web, Spring Data JPA (plus native SQL for the claim queries), Spring Security + JWT, Bean Validation, springdoc-openapi, Spring Scheduling with **ShedLock** (or DB-level claiming only), Micrometer.
**Data:** PostgreSQL 16 (uses `FOR UPDATE SKIP LOCKED`, partial indexes), Flyway. Redis provides shared atomic counters for distributed sensitive-write rate limiting; PostgreSQL remains the durable source of truth.
**Notifications:** Spring Mail + Mailpit (dev), Telegram Bot API (HTTP, no SDK required), optional Web Push with VAPID via a free library (verify license and maintenance status before adopting).
**Backend testing:** JUnit 5, Mockito, AssertJ, jqwik (property tests), Testcontainers, ArchUnit, Awaitility (async assertions), k6.
**Frontend:** as in 6.1, plus `vite-plugin-pwa`.
**DevOps:** Docker + Docker Compose (frontend served by nginx in the stack; Vite dev server locally), GitHub Actions (public repo), Maven wrapper, npm or pnpm.

## 9. Architecture

Modular monolith backend plus a separate PWA.

| Backend module | Responsibility |
|---|---|
| `identity` | register, login, JWT/refresh cookie, preferences, account deletion |
| `household` | households, members, roles, invites, authorization checks |
| `people` | care recipients |
| `medicines` | medicines, schedule rule versions, schedule preview |
| `schedule` | **pure** recurrence expansion (rules + time zone to occurrences) |
| `doses` | dose event materialization, actions, status transitions, missed-sweep, audit log |
| `stock` | ledger, balance, refills |
| `forecast` | **pure** run-out forecast and reorder-date logic |
| `adherence` | **pure** adherence calculations plus query layer |
| `notify` | planner, outbox, dispatcher, channel adapters, preferences |
| `export` | JSON/CSV export |
| `common` | errors, time helpers, config, idempotency support |

**Key flows**
- *Create/edit medicine:* validate -> save new rule version -> regenerate future PENDING events (idempotent upsert) -> recompute forecast.
- *Confirm dose:* idempotency check -> row lock on the dose event -> validate transition -> update event -> append ledger entry (unique per dose event) -> audit log, all in **one transaction**.
- *Planner tick:* find events needing action by indexed time windows -> enqueue notifications with dedupe keys -> mark overdue events MISSED.
- *Dispatcher:* claim batch with SKIP LOCKED -> send -> mark SENT or schedule retry.
- *Daily forecast job:* per household at local time -> run forecast for active medicines -> enqueue deduplicated low-stock alerts.

**Frontend structure:** `src/api`, `src/features/{today,people,medicines,stock,adherence,household,settings}`, `src/components`, `src/lib`, `src/routes`.

## 10. Data model (initial)

`users`, `user_preferences`, `households`, `household_members` (role), `invites` (token hash, role, expires_at, used_at), `care_recipients`, `medicines`, `schedule_rules` (versioned: effective_from/to, type, days_of_week, interval_days, anchor_date, local_times), `dose_events` (unique on medicine_id + scheduled_local; status, acted_at, acted_by), `purchases` (medicine_id, units, pack info, price, pharmacy, purchased_on, batch, expiry, voided_at), `stock_lots` (medicine_id, purchase_id, expiry), `stock_entries` (append-only; links to lot and dose event; unique on (dose_event_id, lot_id) for `DOSE_TAKEN`), `reorder_requests` (medicine_id, ordered_on, expected_by, closed_at), `pharmacy_links` (household_id, url_template), `notifications` (dedupe_key unique, status, attempts, next_attempt_at), `push_subscriptions`, `telegram_links`, `idempotency_keys`, `audit_log`.

Indexes to plan and justify (comments or ADR): `dose_events (status, scheduled_at)` partial index on PENDING; `dose_events (recipient/medicine, scheduled_at)`; `notifications (status, next_attempt_at)` partial index on PENDING; `stock_entries (medicine_id, created_at)`; `stock_lots (medicine_id, expiry)` for FEFO selection; partial index on open `reorder_requests (medicine_id)`; `household_members (user_id)`.

## 11. API surface (initial; refine in OpenAPI)

```
POST   /api/auth/register | /login | /refresh | /logout
DELETE /api/account
GET/PUT /api/preferences

POST   /api/households                       GET /api/households
POST   /api/households/{id}/invites          POST /api/invites/{token}/accept
GET    /api/households/{id}/members          PUT/DELETE /api/households/{id}/members/{userId}

GET/POST /api/households/{id}/people         GET/PUT/DELETE /api/people/{id}
GET/POST /api/people/{id}/medicines          GET/PUT /api/medicines/{id}
POST   /api/medicines/{id}/schedule          (creates a new rule version)
POST   /api/schedule-preview                 (no persistence)
POST   /api/medicines/{id}/archive

GET    /api/doses/today?person=
GET    /api/doses?from=&to=&person=&medicine=
POST   /api/doses/{id}/taken | /skip | /undo            (Idempotency-Key required)
POST   /api/medicines/{id}/doses/prn                    (log an as-needed dose)

GET    /api/medicines/{id}/stock                        (usable balance, lots, ledger, forecast)
POST   /api/medicines/{id}/purchases                    (adds to existing stock; creates a lot)
DELETE /api/purchases/{id}                              (void within the allowed window)
POST   /api/medicines/{id}/stock/adjust | /discard
POST   /api/households/{id}/medicines-with-purchase     (new medicine + schedule + first purchase, atomic)
GET    /api/stock/overview                              (all medicines, sorted by urgency, lots included)
GET    /api/households/{id}/reorder-list?days=30        (medicines at or below lead time, units needed)
POST   /api/medicines/{id}/reorder-requests             (mark as ordered, expected_by)
GET/PUT /api/households/{id}/pharmacy-link              (URL template with {name})
GET    /api/households/{id}/spend?month=                (optional)

GET    /api/adherence?person=&range=
GET    /api/people/{id}/printable

POST   /api/notifications/test
POST   /api/telegram/link-code                 POST /api/push/subscribe
GET    /api/export?format=json|csv
```

Errors use RFC 7807 problem details. List endpoints are paginated. Responses with medicine data include the standing disclaimer where appropriate.

## 12. Safety, privacy and compliance notes (apply throughout)

- No dose recommendations, interaction checks, or medical claims anywhere in code, UI copy, or README.
- Disclaimer on first run and in the footer: "Khayaal helps you remember and track. It is not medical advice. Follow your doctor's and pharmacist's instructions."
- Treat all entered data as sensitive: HTTPS in deployment notes, no analytics or third-party trackers, no medicine names in logs or error reports, discreet notifications option.
- Repository contains **only synthetic demo data**.
- Document in the README what is *not* handled (not a medical device, no regulatory compliance claims).

## 13. Project-level Definition of Done

- `docker compose up` runs frontend, backend, Postgres and Mailpit; README has a quick start and screenshots or a demo GIF of Today, the schedule builder, and Stock.
- CI green: backend build + tests, frontend lint + type-check + unit tests, Playwright e2e.
- `schedule`, `forecast`, `adherence` coverage >= 90% including property tests; concurrency tests pass.
- At least 5 ADRs (modular monolith; ledger over mutable counter; materialized dose events vs computing on the fly; outbox + SKIP LOCKED dispatcher; at-least-once with dedupe), an architecture diagram, and a performance report with numbers.
- Core journey works end to end on mobile and desktop: register, create household, add a person and a medicine with a schedule and a first purchase, receive an on-time dose reminder in Mailpit, confirm the dose, see stock decrease and the forecast update, receive a low-stock alert 3 days before run-out, record a new purchase that adds to the existing stock, and see the alert clear.
- Synthetic seed data and generator; no real health data committed.

---

# 14. PHASE PLAN

**Phases 0 to 6 form the core product (backend, reminders, working UI). Phase 7 adds depth and polish.**

## Phase 0: Foundation, backend and frontend scaffolding (about 1.5 hrs)
- Maven Spring Boot 3 / Java 21 project, package layout per Section 9, ArchUnit rule for pure packages.
- Vite + React + TS (strict) + Tailwind + shadcn/ui + PWA plugin scaffold, ESLint + Prettier, Vitest, route skeletons, app shell (sidebar on desktop, bottom tabs on mobile, theme toggle, disclaimer footer).
- `docker-compose.yml` (Postgres, Mailpit, backend, frontend/nginx), `.env.example`, Flyway baseline, health endpoint, springdoc, RFC 7807 handler, CORS.
- GitHub Actions for backend and frontend.
- `docs/PROJECT_BRIEF.md`, `docs/adr/0001-modular-monolith.md`.

**Acceptance:** `docker compose up` shows the empty app shell; CI green.

## Phase 1: Pure engines: schedule, forecast, adherence (about 3 hrs)
- `schedule`: expand rules (DAILY, WEEKDAYS, EVERY_N_DAYS) into occurrences between two instants for a given IANA zone; handle start/end dates, courses, versioned rules, DST gap/overlap.
- `forecast`: run-out simulation over upcoming doses, `runOutDate`, `daysLeft`, `lastCoveredDate`, adherence-adjusted estimate, `reorderBy`, 365-day cap, course-ends-before-stock case, **multiple lots where a lot expires mid-simulation**, FEFO lot-consumption helper.
- `adherence`: taken/late/skipped/missed percentages, streaks, heatmap bucketing, most-missed times.
- Tests: the FR-4.6 known-value case; every rule type; DST zone examples; property tests (idempotent expansion, monotonic forecast, ledger-sum invariants over random operation sequences, undo restores balance).
- ADRs: pure engines vs services; forecast from rules instead of materialized events.

**Acceptance:** >= 90% coverage on all three packages; no Spring/DB imports (ArchUnit).

## Phase 2: Auth, households, people, medicines, dose generation (about 4 hrs)
- Identity (JWT + refresh cookie), households with roles and expiring single-use invites, role/isolation checks as a reusable authorization layer.
- Flyway schema for everything in Section 10.
- People and medicines CRUD, schedule rule versioning, schedule preview endpoint.
- Dose event materialization (rolling 14-day horizon, nightly job + on-edit regeneration, idempotent upsert, future-only regeneration on edits).
- Integration tests (Testcontainers): role matrix, cross-household isolation, regeneration doesn't touch past events, generating twice creates no duplicates, timezone cases.

**Acceptance:** a README curl walkthrough creates a household, a person, a medicine, and shows the generated dose events.

## Phase 3: Dose actions, stock ledger, Today and Stock APIs (about 3 hrs)
- Dose actions (taken, skip, undo, PRN) with transition rules, idempotency, row locking, and audit log.
- Stock: purchases (adding to the existing balance) that create lots, FEFO consumption across lots, adjust, discard, automatic expiry entries, dose-taken and dose-undo entries, balance derivation, the unique (dose event, lot) constraint, purchase void rules, the atomic "new medicine + first purchase" endpoint, reorder list, reorder requests, pharmacy link setting.
- Missed-dose sweep logic (as a service method, scheduled in Phase 4).
- Today endpoint, stock overview endpoint with forecast and urgency sorting, adherence endpoint.
- Tests: concurrent confirmations (parallel requests, one wins), idempotent replay, undo restores stock, MISSED does not deduct, late TAKEN deducts, ledger sum equals balance, a purchase adds to (not replaces) the balance, FEFO consumption matches the FR-4.9 examples, expired lots drop out of the usable balance, a dose spanning two lots creates two entries and undo restores both.

**Acceptance:** two simulated caregivers confirming the same dose results in one TAKEN and one stock deduction.

> **Checkpoint:** review all backend code, finalize ADRs, record a short API demo.

## Phase 4: Notification outbox, planner, dispatcher, channels (about 4 hrs)
- `notifications` outbox with dedupe keys; planner ticks (due, follow-up, missed, daily forecast/low-stock/expiry) all idempotent.
- Dispatcher using `FOR UPDATE SKIP LOCKED`, exponential backoff, dead state, metrics.
- Channels: email via Mailpit, Telegram (link-code flow). Discreet mode and quiet-hours logic. Web Push left for Phase 6.
- ShedLock (or documented DB-only claiming) so ticks are safe on multiple instances.
- **Alert rules (R2, R3):** dose alerts per FR-5.8; run-out evaluation per FR-4.5 (3-day default, daily reminders, critical alerts, dedupe by medicine + runOutDate + stage, re-evaluation after purchases, adjustments, schedule changes and nightly); a reorder request pauses repeats until its expected date; a purchase closes the alert. Add a test clock so time-based rules are testable without waiting.
- Tests (Awaitility + Testcontainers): a tick run twice enqueues once; two dispatcher instances never send the same row twice; a failing channel retries then goes DEAD; follow-up only if still PENDING; MISSED transitions at the grace boundary; low-stock alert fires once then at the reminder interval; discreet mode omits medicine names.

**Acceptance:** with a dose scheduled 1 minute ahead, Mailpit receives exactly one reminder and, if unconfirmed, one follow-up. With a medicine whose forecast shows 3 days left, exactly one low-stock alert is sent; recording a purchase that extends stock beyond the lead time stops further alerts.

## Phase 5: Frontend core: auth, Today, medicines, schedule builder (about 5 hrs)
- Generate the typed client; query-hooks layer; auth flow with silent refresh; role-aware route guards; invite acceptance page.
- **Today** screen per Section 6.3 (large Taken/Skip, undo, who-confirmed, filters, optimistic updates).
- **Family** and **Person Detail** screens.
- **Medicine form with live schedule preview**, plain-language schedule summary, edit-from-date flow.
- Component tests (MSW) for the dose card and schedule builder; Playwright: register, create household, add person and medicine, see today's dose, confirm it.

**Acceptance:** you can run a household's daily routine from a phone browser.

> **Checkpoint:** use it for a few days with a real routine (synthetic or non-sensitive data in the repo). Fix friction before continuing.

## Phase 6: Frontend, stock, adherence, household, settings, PWA (about 5 hrs)
- **Stock and Refills** screen per Section 6.3: urgency-sorted list, lot breakdown, "Add purchase" form with live "adds to existing stock" preview and undo, reorder list with copy/share, "Mark as ordered", pharmacy link ("Buy"), critical banners, and the "add new medicine with first purchase" flow.
- **Adherence** screen (breakdown, heatmap, most-missed times, streak), **Household** screen (members, roles, invites), **Settings** (channels, test message, Telegram linking, discreet mode, quiet hours, text size, export, delete), **printable list** page with print CSS.
- PWA: installable manifest and icons, service worker, offline shell and messaging; optional Web Push subscription flow (verify support and library license).
- Accessibility pass (large targets, contrast, keyboard, ARIA, non-color signals); responsive checks at phone, tablet and desktop.
- Playwright: add a purchase and see days-left and the run-out date update; mark a low-stock item as ordered; viewer role cannot see write controls.

**Acceptance:** all routes in Section 6.2 work; Lighthouse accessibility >= 95; installable on a phone.

## Phase 7: Hardening, performance, docs (about 4 hrs)
- Data generator (20 recipients, 150 medicines, 100k dose events, 10k queued notifications); k6/load tests for Today, Stock overview, and dispatcher throughput; record before/after numbers for at least one optimization (e.g. partial index on PENDING events) in `docs/performance.md` with `EXPLAIN ANALYZE` evidence.
- Security pass: rate limiting, cookie/CORS/CSRF review, role-matrix tests, invite token handling, log scrubbing check (no medicine names), account/household deletion tests.
- Reliability drills: kill the app mid-dispatch and verify no lost or duplicate reminders after restart; run two instances.
- README: architecture diagram, quick start, screenshots/GIF, design decisions, safety notes, limitations, "how this would scale to many households".
- Finish ADR set (>= 5).

**Acceptance:** the Project-level Definition of Done (Section 13) is met.

---

## Reusable prompt snippets

**Start of each phase**
> Follow `docs/PROJECT_BRIEF.md`. We are doing **Phase N** only. First list the files you will create or change and the tests you will write. Then implement. Do not touch other phases.

**After implementation**
> Review what you just wrote as a skeptical senior reviewer. List bugs, race conditions, missing edge cases (time zones, retries, concurrent users), and anything an interviewer would challenge. Fix the important ones and add tests for them.

**UI review (frontend phases)**
> Review the screens you just built for: use by an older user on a phone, keyboard accessibility, color-independence of status signals, loading/empty/error states, and unnecessary re-renders. List issues and fix the important ones.

**Safety review (any phase)**
> Scan the code, UI copy and docs for anything that could be read as medical advice, dose recommendation, or interaction checking, and for any place medicine names could leak into logs or notifications in discreet mode. Fix them.

**Before committing**
> Summarize this phase's design decisions as an ADR (context, decision, alternatives, consequences) in under 200 words.

**Interview prep after each phase**
> Ask me 5 hard interview questions about the code from this phase (concurrency, idempotency, time zones, delivery guarantees), then critique my answers.


---

# 15. SESSION PROTOCOL AND KICKOFF (follow this)

## Protocol
1. **Never generate the whole project at once.** Work one phase at a time, in order.
2. Maintain `docs/PROGRESS.md`: phase status, decisions made, assumptions, known gaps. Update it at the end of every phase.
3. At the end of each phase reply with: what was built; how to run it; how to run the tests and their result; known gaps; the acceptance criteria and whether each passes. Then **STOP and wait** for me to say "next phase".
4. If tests fail, fix them before moving on. Do not stack new code on failing code.
5. If something is ambiguous, state your assumption in one line and continue. Ask me at most one question, and only if you are truly blocked.
6. Do not add features that are not in this brief. If you think something is missing, list it at the end of the phase instead of building it.
7. Keep code simple and explainable. Add brief comments only where the reasoning is non-obvious (time zones, locking, idempotency, ledger rules).
8. Obey the safety rule in Section 1: no medical advice, dose recommendations or interaction checks anywhere in code, UI copy or docs.

## Day-1 mode (use only if I say "day-1 mode")
Build only this thin vertical slice, ignoring the rest of the phase plan until I ask for more.

**Include**
- Phase 0 foundation: Spring Boot + Postgres + Mailpit + React/Vite/Tailwind in Docker Compose, Flyway, health endpoint, basic CI.
- Pure `schedule` (DAILY rule only) and pure `forecast` (single balance, no lots) with unit tests, including the known-value cases FR-4.6 and FR-4.9(a).
- Single-user auth (JWT); a household is created automatically on registration.
- People and medicines with a DAILY schedule; dose event generation (14-day horizon, idempotent).
- Dose actions (taken, skip, undo) with idempotency keys and a transaction around the status change and ledger entry.
- Stock ledger where a purchase **adds to the existing balance**; forecast endpoint with `daysLeft`, `runOutDate`, `reorderBy` (lead time default 3 days).
- A simple scheduler tick (every 30 s) writing to a `notifications` table with dedupe keys and sending email to Mailpit: an **on-time dose reminder** and the **3-day low-stock alert** (one alert, then daily until a purchase is recorded).
- UI: Today (Taken/Skip with undo), Medicine form with an "Add purchase" action showing a live preview, Stock list with days left and run-out date.

**Exclude for now**
Telegram, web push, roles and invites, stock lots/FEFO/expiry, WEEKDAYS and EVERY_N_DAYS rules, adherence charts, reorder list and pharmacy link, printable list, PWA, load tests, `SKIP LOCKED` dispatcher and ShedLock, discreet mode.

## Kickoff (do this now)
1. Reply with a summary of your understanding of the project in at most 10 lines, and list any assumptions.
2. List the files you will create for **Phase 0** (or for the Day-1 slice's first step if I said "day-1 mode"), and the tests you will write.
3. Implement Phase 0 only, then follow the protocol above and wait for my "next phase".
