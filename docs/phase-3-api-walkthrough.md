# Phase 3 API walkthrough

Use the access token and IDs created in the Phase 2 walkthrough.

## Add stock without replacing the current balance

```powershell
$purchase = @{
  packCount = 2
  unitsPerPack = 10
  purchasedOn = (Get-Date -Format 'yyyy-MM-dd')
  pharmacy = 'Synthetic demo pharmacy'
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/medicines/$medicineId/purchases" `
  -Headers @{ Authorization = "Bearer $accessToken" } -ContentType 'application/json' -Body $purchase
```

Repeat with another purchase and call `GET /api/medicines/{medicineId}/stock`; the balance is the sum of ledger entries.

## Confirm and replay a dose safely

```powershell
$headers = @{
  Authorization = "Bearer $accessToken"
  'Idempotency-Key' = [guid]::NewGuid().ToString()
  'X-Client-Identifier' = 'walkthrough'
}

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/doses/$doseId/taken" `
  -Headers $headers -ContentType 'application/json' -Body '{}'

# Reusing the same key returns the original result and does not deduct stock again.
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/doses/$doseId/taken" `
  -Headers $headers -ContentType 'application/json' -Body '{}'
```

Use a new idempotency key with `POST /api/doses/{id}/undo` to append compensating stock entries.

Khayaal records user-entered medicine information only. It does not recommend doses or provide medical advice.
