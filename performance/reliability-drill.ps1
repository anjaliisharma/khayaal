param(
    [int]$ExpectedNotifications = 10000,
    [int]$MaximumWaitSeconds = 900
)

$ErrorActionPreference = 'Stop'
$database = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'khayaal' }
$databaseUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'khayaal' }

function Read-NotificationCounts {
    docker compose exec -T postgres psql -U $databaseUser -d $database -Atc `
        "select status||'='||count(*) from notifications where dedupe_key like 'PERFORMANCE:%' group by status order by status;"
}

Write-Host 'Starting two backend dispatchers against the shared PostgreSQL outbox...'
docker compose up --detach backend
$workerContainer = docker compose run --detach --no-deps backend
Start-Sleep -Seconds 5

Write-Host 'Interrupting the dispatchers to exercise stale-claim recovery...'
docker compose stop backend
docker stop $workerContainer | Out-Null
docker compose up --detach backend
$workerContainer = docker compose run --detach --no-deps backend

$deadline = (Get-Date).AddSeconds($MaximumWaitSeconds)
do {
    $remaining = docker compose exec -T postgres psql -U $databaseUser -d $database -Atc `
        "select count(*) from notifications where dedupe_key like 'PERFORMANCE:%' and status not in ('SENT','DEAD');"
    Write-Host "Notifications still queued: $remaining"
    if ([int]$remaining -eq 0) { break }
    Start-Sleep -Seconds 5
} while ((Get-Date) -lt $deadline)

$total = docker compose exec -T postgres psql -U $databaseUser -d $database -Atc `
    "select count(*) from notifications where dedupe_key like 'PERFORMANCE:%';"
$duplicateKeys = docker compose exec -T postgres psql -U $databaseUser -d $database -Atc `
    "select count(*) from (select dedupe_key from notifications where dedupe_key like 'PERFORMANCE:%' group by dedupe_key having count(*) > 1) duplicates;"
$stranded = docker compose exec -T postgres psql -U $databaseUser -d $database -Atc `
    "select count(*) from notifications where dedupe_key like 'PERFORMANCE:%' and status='PROCESSING';"

Read-NotificationCounts | Write-Host
if ([int]$total -ne $ExpectedNotifications) { throw "Expected $ExpectedNotifications notifications but found $total." }
if ([int]$duplicateKeys -ne 0) { throw "Found $duplicateKeys duplicate dedupe keys." }
if ([int]$stranded -ne 0) { throw "Found $stranded notifications stranded in PROCESSING." }
docker stop $workerContainer | Out-Null
docker rm $workerContainer | Out-Null
Write-Host 'Reliability drill passed: count preserved, dedupe keys unique, and no processing rows stranded.'
