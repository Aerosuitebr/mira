# Pipeline completa: download -> extrair -> importar (com geocode + Elasticsearch)
param(
    [string[]]$States = @("RJ"),
    [string]$ReferenceMonth = "2026-01",
    [string]$DataRoot = "C:\prospect-portal-data",
    [string]$ApiBase = "http://localhost:8082",
    [switch]$SkipDownload,
    [switch]$SkipExtract,
    [switch]$SkipImport,
    [switch]$ImportOnlyNewEstabelecimentos,
    [switch]$EnrichOnly
)

$ErrorActionPreference = "Stop"

if ($EnrichOnly) {
    & "$PSScriptRoot\post-import-enrich.ps1" -States $States -ApiBase $ApiBase
    exit $LASTEXITCODE
}

Write-Host "=== 1/4 Infra (Elasticsearch + Redis) ==="
Push-Location (Join-Path $PSScriptRoot "..")
docker compose up -d elasticsearch redis
Pop-Location
Start-Sleep -Seconds 5

if (-not $SkipDownload) {
    Write-Host "`n=== 2/4 Download dataset completo ==="
    & "$PSScriptRoot\download-rf-full.ps1" -ReferenceMonth $ReferenceMonth -DataRoot $DataRoot
}

if (-not $SkipExtract) {
    Write-Host "`n=== 3/4 Extrair ZIPs ==="
    & "$PSScriptRoot\extract-rf.ps1" -DataRoot $DataRoot
}

if (-not $SkipImport) {
    Write-Host "`n=== 4/4 Importar para PostgreSQL + geocode + Elasticsearch ==="
    $login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
        -ContentType "application/json" `
        -Body (@{ email = "demo@prospectportal.com"; password = "demo123" } | ConvertTo-Json)
    $headers = @{ Authorization = "Bearer $($login.token)" }

    $body = @{
        states = $States
        loadEmpresas = -not $ImportOnlyNewEstabelecimentos
        geocodeAfterImport = $true
        syncElasticsearch = $true
        estabelecimentoFiles = @()
    } | ConvertTo-Json

    $result = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/admin/import/rf" `
        -Headers $headers -ContentType "application/json" -Body $body

    Write-Host "Job importacao: $($result.id) | $($result.status)"
}

Write-Host "`nAcompanhe: Invoke-RestMethod $ApiBase/api/admin/import/rf/status (com token admin)"
Write-Host "Dica: use -SkipDownload -SkipExtract se os ZIPs ja estiverem em $DataRoot\rf"
