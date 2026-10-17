# Performance verification

The repository includes `performance/seed.sql` for 20 synthetic recipients, 150 medicines, and 100,000 dose events, plus `performance/k6.js` for Today and Stock.

Run only against a disposable database. Register a synthetic account, pass its UUID to `psql`, then run:

```powershell
k6 run -e ACCESS_TOKEN=<token> performance/k6.js
```

Required threshold: p95 below 100 ms with under 1% failed requests. Record the machine, Docker resources, commit, results, and `EXPLAIN (ANALYZE, BUFFERS)` output here after running. No benchmark numbers are claimed until measured.

Existing supporting indexes include partial pending-dose and pending-notification indexes, recipient/medicine joins, FEFO stock lots, and stock ledger history. Compare query plans before and after any index change; retain an index only when evidence shows a useful plan or latency improvement.

The Stock overview implementation was changed before final measurement from per-medicine context, lot, balance, and reorder lookups (`1 + 4N` database calls) to four set-based queries regardless of medicine count: overview/context, lots, schedule times, and schedule days. The run-out calculation still uses the pure forecast engine. This is a query-count result, not a latency claim; record before/after p95 and query plans below during the final verification run.

| Commit/build | Machine and Docker resources | Today p95 | Stock p95 | Failed requests | Notes |
|---|---|---:|---:|---:|---|
| Pending final verification | Pending | — | — | — | Do not replace with estimates. |

Dispatcher throughput and the 10,000-notification reliability drill remain separate from HTTP k6 traffic: seed the outbox, interrupt one backend during dispatch, restart it, and verify every dedupe key reaches `SENT` once with no rows stranded indefinitely in `PROCESSING`.

After loading `performance/seed.sql`, automate that drill from the repository root:

```powershell
.\performance\reliability-drill.ps1 -ExpectedNotifications 10000 -MaximumWaitSeconds 900
```

The script starts two backend workers, interrupts them, restarts them, waits for completion, and fails if the synthetic count changes, a dedupe key is duplicated, or a row remains stranded in `PROCESSING`.
