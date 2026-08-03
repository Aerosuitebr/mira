# Geocodifica empresas pendentes e/ou sincroniza Elasticsearch (sem reimportar CSVs)
# Para só indexar no ES (buscas rápidas): .\scripts\reindex-elasticsearch.ps1 -Watch
param(
    [string[]]$States = @("RJ"),
    [string]$ApiBase = "http://localhost:8082",
    [string]$Email = "demo@prospectportal.com",
    [string]$Password = "demo123",
    [switch]$GeocodeOnly,
    [switch]$IndexOnly
)

$geocode = $true
$syncElasticsearch = $true
if ($GeocodeOnly) { $syncElasticsearch = $false }
if ($IndexOnly) { $geocode = $false }

$login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
    -ContentType "application/json" `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)

$headers = @{ Authorization = "Bearer $($login.token)" }

$body = @{
    states = $States
    geocode = $geocode
    syncElasticsearch = $syncElasticsearch
} | ConvertTo-Json

Write-Host "Enriquecimento para: $($States -join ', ') | geocode=$geocode | elasticsearch=$syncElasticsearch"
$result = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/admin/import/rf/enrich" `
    -Headers $headers -ContentType "application/json" -Body $body

Write-Host "Job ID: $($result.id) | Tipo: $($result.jobType) | Status: $($result.status)"
Write-Host "Acompanhe: GET $ApiBase/api/admin/import/rf/status"
Write-Host "Nota: geocoding usa Nominatim (~1 req/s). ~27k empresas RJ ~= 8h."
