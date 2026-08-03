# Reimporta estabelecimentos RJ (rf_empresas deve estar completo — ~66M registros).
# Corrige empresas ignoradas na 1ª importação por rf_empresas incompleto.
param(
    [string[]]$States = @("RJ"),
    [switch]$WithGeocode,
    [switch]$WithElasticsearch
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$skipGeo = -not $WithGeocode
$skipEs = -not $WithElasticsearch

Write-Host "Reimportando estabelecimentos (sem recarregar empresas)..."
& (Join-Path $scriptDir "start-rf-import.ps1") -States $States -SkipEmpresas `
    $(if ($skipGeo) { '-SkipGeocode' }) `
    $(if ($skipEs) { '-SkipElasticsearch' })

Write-Host ""
Write-Host "Após concluir, valide MRO:"
Write-Host "  docker exec prospect-portal-postgres psql -U prospect -d prospect_portal -c `"SELECT COUNT(*) FROM companies WHERE state='RJ' AND (cnae_main LIKE '33163%' OR COALESCE(cnae_secondary,'') LIKE '%33163%');`""
