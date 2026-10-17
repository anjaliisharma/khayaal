param([string]$BaseUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
$database = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'khayaal' }
$databaseUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'khayaal' }
$email = "performance-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@example.test"
$registration = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register" -ContentType 'application/json' -Body `
    (@{ email = $email; password = 'synthetic-performance-only'; displayName = 'Synthetic Performance User' } | ConvertTo-Json)
$userId = docker compose exec -T postgres psql -U $databaseUser -d $database -Atc "select id from user_accounts where email='$email';"
if (-not $userId) { throw 'Could not resolve the synthetic performance user.' }

Get-Content -Raw -LiteralPath '.\performance\seed.sql' | docker compose exec -T postgres psql -U $databaseUser -d $database -v "user_id='$userId'"
if ($LASTEXITCODE -ne 0) { throw 'Synthetic seed failed.' }

Get-Content -Raw -LiteralPath '.\performance\k6.js' | docker run --rm -i `
    -e BASE_URL=http://host.docker.internal:8080 -e "ACCESS_TOKEN=$($registration.accessToken)" grafana/k6:latest run -
if ($LASTEXITCODE -ne 0) { throw 'k6 thresholds failed.' }
