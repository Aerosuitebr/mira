# Extrai ZIPs da Receita para C:\prospect-portal-data\rf\extracted
param(
    [string]$DataRoot = "C:\prospect-portal-data"
)

$ErrorActionPreference = "Stop"
$incoming = Join-Path $DataRoot "rf\incoming"
$extracted = Join-Path $DataRoot "rf\extracted"
New-Item -ItemType Directory -Force -Path $extracted | Out-Null

Get-ChildItem $incoming -Filter "*.zip" | ForEach-Object {
    $targetDir = Join-Path $extracted $_.BaseName
    if (Test-Path $targetDir) {
        Write-Host "[skip] $($_.Name) ja extraido"
        return
    }
    Write-Host "[extract] $($_.Name) -> $targetDir"
    Expand-Archive -Path $_.FullName -DestinationPath $targetDir -Force
}

Write-Host "Concluido. CSVs em $extracted"
