# Domain calculation policies

## Schedule and daylight-saving time

- Schedule dates and times are interpreted in the care recipient's IANA timezone.
- A nonexistent local time in a spring-forward gap moves to the first valid local time after the transition.
- An ambiguous local time in an autumn overlap uses the earlier offset.
- Expansion ranges are instant-based: start inclusive, end exclusive.
- Schedule end dates are inclusive.

## Stock and forecast

- Quantities use `BigDecimal`; floating point is prohibited.
- Dated lots are consumed by earliest expiry, then purchase time, then stable lot ID.
- Undated lots follow all dated lots.
- A lot is usable through its expiry date and excluded on later dates.
- Planned run-out is the first scheduled dose that cannot be fully covered.
- Alerts use planned run-out, never the adherence-adjusted likely date.
- Forecast simulation is capped at 365 days.

## Adherence

- A taken dose is on time when its actual time is no more than 15 minutes after its scheduled instant; later confirmations count as taken late.
- On-time, late, and missed percentages share the denominator `on-time + late + missed`.
- Skipped doses are reported separately and excluded from that denominator.
- A fully completed day has at least one non-skipped dose and no missed dose.
- A partially completed day contains both a taken and missed dose.
- A missed day has only missed non-skipped doses.
- Days containing only skipped doses do not receive a completion bucket.

## Dose actions and purchases

- Dose transitions, stock allocation, audit writes, and idempotency snapshots commit atomically.
- An idempotency-key replay returns the original action result even if the dose changes later.
- Purchases may be voided for 24 hours only while their lot is untouched; otherwise users record a correcting adjustment.
