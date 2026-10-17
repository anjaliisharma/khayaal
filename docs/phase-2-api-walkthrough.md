# Phase 2 API walkthrough (PowerShell)

Start the stack with `docker compose up --build`, then run these commands from another terminal.

```powershell
$base = "http://localhost:8080"
$auth = Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType "application/json" -SessionVariable session -Body (@{
  email = "caregiver@example.test"
  password = "correct-horse-battery"
  displayName = "Caregiver"
} | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($auth.accessToken)" }

$household = Invoke-RestMethod -Method Post -Uri "$base/api/households" -Headers $headers -ContentType "application/json" -Body (@{
  name = "Demo household"
  defaultTimezone = "Asia/Kolkata"
} | ConvertTo-Json)

$person = Invoke-RestMethod -Method Post -Uri "$base/api/households/$($household.id)/people" -Headers $headers -ContentType "application/json" -Body (@{
  name = "Synthetic person"
  birthYear = 1960
  timezone = "Asia/Kolkata"
  notes = "Synthetic demo data"
} | ConvertTo-Json)

$medicineBody = @{
  name = "Synthetic medicine"
  strength = "User-entered text"
  form = "tablet"
  unitsPerDose = 1.00
  instructions = "User-entered instructions"
  startDate = (Get-Date).ToString("yyyy-MM-dd")
  endDate = $null
  asNeeded = $false
  leadTimeDays = 3
  schedule = @{
    type = "DAILY"
    localTimes = @("08:00:00", "20:00:00")
    daysOfWeek = @()
    intervalDays = $null
    anchorDate = $null
  }
} | ConvertTo-Json -Depth 5
$medicine = Invoke-RestMethod -Method Post -Uri "$base/api/people/$($person.id)/medicines" -Headers $headers -ContentType "application/json" -Body $medicineBody

$from = [Uri]::EscapeDataString((Get-Date).ToUniversalTime().ToString("o"))
$to = [Uri]::EscapeDataString((Get-Date).ToUniversalTime().AddDays(15).ToString("o"))
Invoke-RestMethod -Uri "$base/api/households/$($household.id)/doses?from=$from&to=$to" -Headers $headers
```

The last response shows the generated UTC instant and recipient-local date-time for each materialized dose. All names above are synthetic.
