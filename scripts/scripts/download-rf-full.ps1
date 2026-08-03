# Baixa o dataset completo CNPJ (Empresas0-9 + Estabelecimentos0-9 + referência)
param(
    [string]$ReferenceMonth = "2026-01",
    [string]$DataRoot = "C:\prospect-portal-data"
)

$files = @("Municipios", "Cnaes")
0..9 | ForEach-Object { $files += "Empresas$_"; $files += "Estabelecimentos$_" }

Write-Host "Dataset completo: $($files.Count) arquivos (mes $ReferenceMonth)"
Write-Host "Estimativa de download: ~20 GB (depende da versao RF)"
Write-Host ""

& "$PSScriptRoot\download-rf.ps1" -ReferenceMonth $ReferenceMonth -Files $files -DataRoot $DataRoot
