param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}

$resolved = (Resolve-Path $BackupFile).Path
$user = if ($env:DATABASE_USERNAME) { $env:DATABASE_USERNAME } else { "tenant" }
$db = "tenant_management"

Write-Host "Stopping app and worker so connections are released"
docker compose stop app worker

Write-Host "Terminating leftover sessions on $db"
docker compose exec -T db psql -U $user -d postgres -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$db' AND pid <> pg_backend_pid();"

Write-Host "Dropping and recreating $db"
docker compose exec -T db psql -U $user -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $db;"
docker compose exec -T db psql -U $user -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $db;"

Write-Host "Restoring $resolved"
cmd /c "docker compose exec -T db psql -U $user -d $db -v ON_ERROR_STOP=1 < `"$resolved`""
if ($LASTEXITCODE -ne 0) {
    throw "psql restore failed with exit code $LASTEXITCODE"
}

Write-Host "Starting app and worker"
docker compose start app worker

Write-Host "Restore finished. Confirm GET /ready after the app is healthy."
