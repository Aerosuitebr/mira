#Requires -RunAsAdministrator
<#
.SYNOPSIS
  Redefine a senha do superusuario postgres quando ela foi esquecida ou nao definida.

.EXAMPLE
  .\reset-postgres-password.ps1 -NewPassword 'Prospect2026Dev'
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$NewPassword
)

$ErrorActionPreference = "Stop"
$PgHba = "C:\Program Files\PostgreSQL\18\data\pg_hba.conf"
$Psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
$ServiceName = "postgresql-x64-18"
$Backup = "$PgHba.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"

if (-not (Test-Path $PgHba)) {
    throw "Arquivo nao encontrado: $PgHba"
}

Write-Host "Backup de pg_hba.conf -> $Backup" -ForegroundColor Cyan
Copy-Item $PgHba $Backup -Force

$lines = Get-Content $PgHba
$trustLines = $lines | ForEach-Object {
    if ($_ -match '^(local|host)\s+all\s+all\s+' -and $_ -notmatch 'replication') {
        $_ -replace 'scram-sha-256\s*$', 'trust'
    } else {
        $_
    }
}
Set-Content -Path $PgHba -Value $trustLines -Encoding ASCII

Write-Host "Reiniciando servico $ServiceName..." -ForegroundColor Cyan
Restart-Service $ServiceName
Start-Sleep -Seconds 3

$escaped = $NewPassword.Replace("'", "''")
& $Psql -U postgres -h localhost -p 5432 -d postgres -c "ALTER USER postgres WITH PASSWORD '$escaped';"
if ($LASTEXITCODE -ne 0) {
    Copy-Item $Backup $PgHba -Force
    Restart-Service $ServiceName
    throw "Falha ao alterar senha. pg_hba.conf restaurado."
}

Write-Host "Restaurando autenticacao segura (scram-sha-256)..." -ForegroundColor Cyan
Copy-Item $Backup $PgHba -Force
Restart-Service $ServiceName
Start-Sleep -Seconds 2

$env:PGPASSWORD = $NewPassword
& $Psql -U postgres -h localhost -p 5432 -d postgres -c "SELECT 'Senha alterada com sucesso' AS status;"
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue

Write-Host "`nPronto. Usuario: postgres" -ForegroundColor Green
Write-Host "Proximo passo:"
Write-Host "  cd d:\Desenvolvimento\b2b-prospect-portal"
Write-Host "  .\scripts\setup-database.ps1 -InstallLocal -PostgresSuperPassword '$NewPassword'"
