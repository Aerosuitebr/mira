# Dispara importação RF (requer login admin + arquivos em C:\prospect-portal-data\rf\extracted)
param(
    [string[]]$States = @("RJ"),
    [string]$ApiBase = "http://localhost:8082",
    [string]$Email = "demo@prospectportal.com",
    [string]$Password = "demo123",
    [switch]$SkipEmpresas,
    [switch]$SkipGeocode,
    [switch]$SkipElasticsearch
)

$login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
    -ContentType "application/json" `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)

$headers = @{ Authorization = "Bearer $($login.token)" }

$body = @{
    states = $States
    loadEmpresas = -not $SkipEmpresas
    geocodeAfterImport = -not $SkipGeocode
    syncElasticsearch = -not $SkipElasticsearch
    estabelecimentoFiles = @()
} | ConvertTo-Json

Write-Host "Iniciando importacao para: $($States -join ', ')"
$result = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/admin/import/rf" `
    -Headers $headers -ContentType "application/json" -Body $body

Write-Host "Job ID: $($result.id) | Status: $($result.status)"
Write-Host "Acompanhe: GET $ApiBase/api/admin/import/rf/status"
