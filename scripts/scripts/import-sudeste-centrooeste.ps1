# Importa estabelecimentos ativos do Sudeste e Centro-Oeste (rf_empresas já carregado).
# UFs: SP, RJ, MG, ES, DF, GO, MT, MS
param(
    [switch]$WithGeocode,
    [switch]$WithElasticsearch
)

$states = @('SP', 'RJ', 'MG', 'ES', 'DF', 'GO', 'MT', 'MS')
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "Importando UFs: $($states -join ', ')"
Write-Host "Geocodificação: $(if ($WithGeocode) { 'sim' } else { 'não (recomendado após importação)' })"
Write-Host "Elasticsearch: $(if ($WithElasticsearch) { 'sim' } else { 'não' })"
Write-Host ""

$skipGeo = -not $WithGeocode
$skipEs = -not $WithElasticsearch

$params = @{
    States = $states
    SkipEmpresas = $true
}
if ($skipGeo) { $params.SkipGeocode = $true }
if ($skipEs) { $params.SkipElasticsearch = $true }

& (Join-Path $scriptDir 'start-rf-import.ps1') @params

Write-Host ""
Write-Host "Acompanhe o progresso:"
Write-Host "  .\scripts\watch-rf-import.ps1"
Write-Host ""
Write-Host "Contagem por UF:"
Write-Host "  docker exec prospect-portal-postgres psql -U prospect -d prospect_portal -c `"SELECT state, COUNT(*) FROM companies GROUP BY state ORDER BY state;`""
