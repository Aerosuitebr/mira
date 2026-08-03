# Geocodifica em massa por CEP (AwesomeAPI) — ~677k CEPs únicos para 16M empresas
param(
    [string[]]$States = @("SP", "RJ", "MG", "ES", "GO", "DF", "MT", "MS"),
    [string]$ApiBase = "http://localhost:8082",
    [string]$Email = "demo@prospectportal.com",
    [string]$Password = "demo123",
    [switch]$Watch
)

$login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
    -ContentType "application/json" `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)

$headers = @{ Authorization = "Bearer $($login.token)" }

Write-Host "=== Geocodificação em massa (por CEP) ===" -ForegroundColor Cyan
Write-Host "Estados: $($States -join ', ')"
Write-Host "Estratégia: cache CEP paralelo (24 threads) + UPDATE em massa por UF"
Write-Host "Prioridade: SP -> RJ -> MG -> ... | ~677k CEPs únicos"
Write-Host "Estimativa: SP ~2-3h, total ~6-8h"
Write-Host ""

$body = @{
    states = $States
    geocode = $true
    syncElasticsearch = $false
} | ConvertTo-Json

$result = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/admin/import/rf/enrich" `
    -Headers $headers -ContentType "application/json" -Body $body

Write-Host "Job ID: $($result.id) | Tipo: $($result.jobType) | Status: $($result.status)"
Write-Host ""

if ($Watch) {
    & "$PSScriptRoot\watch-geocode.ps1" -ApiBase $ApiBase -Email $Email -Password $Password
} else {
    Write-Host "Acompanhe: .\scripts\watch-geocode.ps1"
}
