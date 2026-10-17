# Final verification

Run this checkpoint from a normal PowerShell session with Docker Desktop running. Record outputs in the result table rather than estimating them.

## Automated checks

```powershell
cd "C:\Users\anjal\Documents\Codex\Khayaal\backend"
mvn clean verify

cd "..\frontend"
pnpm run check

cd ..
docker compose down
docker compose up --build --detach --wait

cd frontend
pnpm run api:generate
pnpm run check
pnpm run e2e
```

The Chromium e2e project regenerates `docs/screenshots/today.png`, `schedule-builder.png`, and `stock.png` from synthetic records.

## Performance and recovery

From the repository root:

```powershell
.\performance\run-load-test.ps1
.\performance\reliability-drill.ps1
```

Copy the k6 output and relevant `EXPLAIN (ANALYZE, BUFFERS)` plans into `docs/performance.md`.

## Browser acceptance

1. Open `http://localhost:3000` in a private Chrome window.
2. Run Lighthouse on Today in desktop and mobile modes. Required: performance >= 90, accessibility >= 95, and installable PWA checks passing.
3. Toggle offline mode and confirm the shell and accurate offline warning render without implying that medicine data can be changed offline.
4. Keyboard-check registration, Today actions, purchase dialog, household roles, Settings, and the print page. Confirm every focused control has a visible focus indicator and touch targets are at least 44 px.
5. Open Mailpit at `http://localhost:8025`, send a test email from Settings, and confirm exactly one synthetic message arrives.

## Results

| Check | Required result | Recorded result |
|---|---|---|
| Backend `mvn clean verify` | Pass | Pending host run |
| Frontend `pnpm run check` | Pass | Pending host run |
| Playwright desktop/mobile | Pass | Pending host run |
| Lighthouse performance | >= 90 | Pending host run |
| Lighthouse accessibility | >= 95 | Pending host run |
| Today API p95 | < 100 ms | Pending host run |
| Stock API p95 | < 100 ms | Pending host run |
| Failed request rate | < 1% | Pending host run |
| Two-worker recovery drill | No loss, duplicate key, or stranded claim | Pending host run |
