# Scan RF Estabelecimentos for MRO (33163) in RJ and compare with PostgreSQL
param(
    [string]$DataRoot = "C:\prospect-portal-data"
)

$ErrorActionPreference = "Stop"
$rfRoot = Join-Path $DataRoot "rf\extracted"
$encoding = [System.Text.Encoding]::GetEncoding("ISO-8859-1")

function Only-Digits([string]$v) {
    if ([string]::IsNullOrEmpty($v)) { return "" }
    return ($v -replace '\D', '')
}

function Pad-Left([string]$v, [int]$size) {
    $d = Only-Digits $v
    if ([string]::IsNullOrEmpty($d)) { return "" }
    return $d.PadLeft($size, '0')
}

function Get-Field([string[]]$cols, [int]$idx) {
    if ($idx -lt $cols.Count) { return $cols[$idx] }
    return ""
}

$primary = @{}
$secondary = @{}

Get-ChildItem $rfRoot -Directory | Where-Object { $_.Name -like "Estabelecimentos*" } | Sort-Object Name | ForEach-Object {
    $file = Get-ChildItem $_.FullName -File | Where-Object { $_.Name -like "*ESTABELE*" } | Select-Object -First 1
    if (-not $file) { return }
    Write-Host "Scanning $($file.Name)..."
    $reader = [System.IO.StreamReader]::new($file.FullName, $encoding)
    try {
        while ($null -ne ($line = $reader.ReadLine())) {
            $cols = $line.Split(';', [System.StringSplitOptions]::None)
            if ((Get-Field $cols 5) -ne "02") { continue }
            if ((Get-Field $cols 20).ToUpper() -ne "RJ") { continue }

            $basico = Pad-Left (Get-Field $cols 0) 8
            $ordem = Pad-Left (Get-Field $cols 1) 4
            $dv = Pad-Left (Get-Field $cols 2) 2
            $cnpj = $basico + $ordem + $dv
            if ($cnpj.Length -ne 14) { continue }

            $cnaeMain = Only-Digits (Get-Field $cols 11)
            if ($cnaeMain.StartsWith("33163")) {
                $primary[$cnpj] = $cnaeMain
            }

            $cnaeSec = Get-Field $cols 12
            if ($cnaeSec -match "33163" -and -not $secondary.ContainsKey($cnpj)) {
                $secondary[$cnpj] = $cnaeSec.Substring(0, [Math]::Min(120, $cnaeSec.Length))
            }
        }
    }
    finally {
        $reader.Close()
    }
}

$secOnly = @{}
foreach ($k in $secondary.Keys) {
    if (-not $primary.ContainsKey($k)) { $secOnly[$k] = $secondary[$k] }
}

Write-Host ""
Write-Host "RF primary MRO (33163*) RJ active: $($primary.Count)"
Write-Host "RF secondary-only MRO RJ active: $($secOnly.Count)"
Write-Host "RF any MRO mention RJ active: $($primary.Count + $secOnly.Count)"

if ($primary.Count -eq 0) { exit 0 }

$cnpjList = ($primary.Keys | Sort-Object | ForEach-Object { "'$_'" }) -join ","
$inDbRaw = docker exec prospect-portal-postgres psql -U prospect -d prospect_portal -t -A -c `
    "SELECT cnpj, cnae_main FROM companies WHERE cnpj IN ($cnpjList) ORDER BY cnpj;"
$dbLines = @($inDbRaw -split "`n" | Where-Object { $_.Trim() -ne "" })
$dbCnpjs = @{}
foreach ($l in $dbLines) {
    $parts = $l.Split("|")
    if ($parts.Count -ge 2) { $dbCnpjs[$parts[0]] = $parts[1] }
}

$missing = @($primary.Keys | Where-Object { -not $dbCnpjs.ContainsKey($_) } | Sort-Object)
Write-Host "In companies with primary MRO cnae: $($dbCnpjs.Count)"
Write-Host "Missing from companies: $($missing.Count)"

if ($missing.Count -gt 0) {
    $sample = $missing | Select-Object -First 15
    $basics = ($sample | ForEach-Object { "'$($_.Substring(0,8))'" }) -join ","
    $empRaw = docker exec prospect-portal-postgres psql -U prospect -d prospect_portal -t -A -c `
        "SELECT cnpj_basico FROM rf_empresas WHERE cnpj_basico IN ($basics);"
    $empSet = @{}
    foreach ($b in ($empRaw -split "`n" | Where-Object { $_.Trim() -ne "" })) { $empSet[$_.Trim()] = $true }

    Write-Host ""
    Write-Host "Sample missing CNPJs (first 15):"
    foreach ($c in $sample) {
        $hasEmp = if ($empSet.ContainsKey($c.Substring(0,8))) { "YES" } else { "NO" }
        Write-Host "  $c cnae=$($primary[$c]) rf_empresas=$hasEmp"
    }
}

$outFile = Join-Path $PSScriptRoot "mro-rj-missing-cnpjs.txt"
$missing | Set-Content $outFile -Encoding UTF8
Write-Host ""
Write-Host "Wrote $($missing.Count) missing CNPJs to $outFile"
