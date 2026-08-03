# Sincroniza PostgreSQL -> Elasticsearch (buscas rápidas na Descoberta)
param(
    [string[]]$States = @("RJ", "SP", "MG", "ES", "DF", "GO", "MT", "MS"),
    [string]$ApiBase = "http://localhost:8082",
    [string]$Email = "demo@prospectportal.com",
    [string]$Password = "demo123",
    [switch]$RecreateIndex,
    [switch]$Watch
)

$login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
    -ContentType "application/json" `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)

$headers = @{ Authorization = "Bearer $($login.token)" }

Write-Host "=== Elasticsearch Reindex ===" -ForegroundColor Cyan
Write-Host "Estados: $($States -join ', ')"
Write-Host "Recriar índice: $RecreateIndex"
Write-Host ""

$before = Invoke-RestMethod -Headers $headers -Uri "$ApiBase/api/admin/search/status"
Write-Host "Antes: $($before.indexedDocuments) docs no ES / $($before.postgresCompanies) no Postgres"

$body = @{
    states = $States
    recreateIndex = [bool]$RecreateIndex
} | ConvertTo-Json

$result = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/admin/search/reindex" `
    -Headers $headers -ContentType "application/json" -Body $body

Write-Host "Job iniciado: $($result.id) ($($result.jobType))"
Write-Host ""

if ($Watch) {
    & "$PSScriptRoot\watch-reindex.ps1" -ApiBase $ApiBase -Email $Email -Password $Password
} else {
    Write-Host "Acompanhe: .\scripts\watch-reindex.ps1"
    Write-Host "Status:    GET $ApiBase/api/admin/search/status"
}
