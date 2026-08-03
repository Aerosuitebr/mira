# Baixa arquivos abertos CNPJ (Receita Federal via Nextcloud WebDAV)
param(
    [string]$ReferenceMonth = "2026-01",
    [string[]]$Files = @("Municipios", "Cnaes", "Empresas0", "Estabelecimentos0"),
    [string]$DataRoot = "C:\prospect-portal-data",
    [string]$ShareToken = "gn672Ad4CF8N6TK"
)

$ErrorActionPreference = "Stop"
$incoming = Join-Path $DataRoot "rf\incoming"
New-Item -ItemType Directory -Force -Path $incoming | Out-Null

$webDavBase = "https://arquivos.receitafederal.gov.br/public.php/webdav/Dados/Cadastros/CNPJ/$ReferenceMonth"

Write-Host "Referencia: $ReferenceMonth"
Write-Host "Fonte: Receita Federal (Nextcloud WebDAV)"
Write-Host "Destino: $incoming"
Write-Host ""

foreach ($file in $Files) {
    $name = "$file.zip"
    $dest = Join-Path $incoming $name
    $url = "$webDavBase/$name"

    if (Test-Path $dest) {
        $existingMb = [math]::Round((Get-Item $dest).Length / 1MB, 2)
        Write-Host "[skip] $name ja existe ($existingMb MB)"
        continue
    }

    Write-Host "[download] $name ..."
    $curlArgs = @(
        "-u", "${ShareToken}:",
        "-L", "--fail", "--silent", "--show-error",
        "-o", $dest,
        $url
    )
    & curl.exe @curlArgs 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "[erro] falha ao baixar $name de $url"
        if (Test-Path $dest) { Remove-Item $dest -Force }
        continue
    }
    $sizeMb = [math]::Round((Get-Item $dest).Length / 1MB, 2)
    Write-Host "[ok] $name ($sizeMb MB)"
}

Write-Host ""
Write-Host "Proximo passo: extract-rf.ps1"
