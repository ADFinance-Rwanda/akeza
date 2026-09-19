$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$user = if ($env:DATABASE_USERNAME) { $env:DATABASE_USERNAME } else { "tenant" }
$db = "tenant_management"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupDir = Join-Path $repoRoot "backups"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$outFile = Join-Path $backupDir "tenant_management-$stamp.sql"

Write-Host "Dumping $db from Compose service db as user $user"
cmd /c "docker compose exec -T db pg_dump -U $user -d $db --no-owner --no-acl --clean --if-exists > `"$outFile`""
if ($LASTEXITCODE -ne 0) {
    throw "pg_dump failed with exit code $LASTEXITCODE"
}

Write-Host "Backup written to $outFile"
Write-Output $outFile
