# Aguarda download RF terminar, extrai ZIPs e dispara importacao
param(
    [string]$DataRoot = "C:\prospect-portal-data",
    [string[]]$States = @("RJ"),
    [string]$ApiBase = "http://localhost:8082",
    [int]$PollSeconds = 60,
    [int]$DownloadPid = 0
)

$ErrorActionPreference = "Stop"
$logDir = Join-Path $DataRoot "rf\logs"
$pipelineLog = Join-Path $logDir "pipeline-auto-$(Get-Date -Format 'yyyyMMdd-HHmm').log"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Write-Log($msg) {
    $line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $msg"
    Write-Host $line
    Add-Content -Path $pipelineLog -Value $line
}

$expectedNew = @()
1..9 | ForEach-Object {
    $expectedNew += "Empresas$_.zip"
    $expectedNew += "Estabelecimentos$_.zip"
}

Write-Log "Monitor iniciado. Aguardando $($expectedNew.Count) ZIPs (Empresas1-9 + Estabelecimentos1-9)"

if ($DownloadPid -gt 0) {
    Write-Log "Acompanha PID download: $DownloadPid"
}

$lastStatus = ""
while ($true) {
    $incoming = Join-Path $DataRoot "rf\incoming"
    $present = Get-ChildItem $incoming -Filter "*.zip" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
    $done = ($expectedNew | Where-Object { $present -contains $_ }).Count
    $status = "$done/$($expectedNew.Count) ZIPs prontos"

    if ($status -ne $lastStatus) {
        Write-Log $status
        $lastStatus = $status
    }

    $downloadRunning = $false
    if ($DownloadPid -gt 0) {
        $downloadRunning = $null -ne (Get-Process -Id $DownloadPid -ErrorAction SilentlyContinue)
    } else {
        $downloadRunning = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -like "*download-rf.ps1*" -and $_.CommandLine -like "*Empresas1*" } |
            Select-Object -First 1
    }

    if ($done -ge $expectedNew.Count -and -not $downloadRunning) {
        Write-Log "Download concluido."
        break
    }

    if (-not $downloadRunning -and $done -lt $expectedNew.Count) {
        $missing = $expectedNew | Where-Object { $present -notcontains $_ }
        Write-Log "Download parou com $($missing.Count) arquivos faltando: $($missing -join ', ')"
        Write-Log "Tentando retomar download..."
        $files = 1..9 | ForEach-Object { "Empresas$_"; "Estabelecimentos$_" }
        & "$PSScriptRoot\download-rf.ps1" -ReferenceMonth "2026-01" -Files $files *>> $pipelineLog
        if ($LASTEXITCODE -ne 0) {
            Write-Log "AVISO: download retomado com possiveis falhas (exit $LASTEXITCODE)"
        }
    }

    Start-Sleep -Seconds $PollSeconds
}

Write-Log "=== Extracao ==="
& "$PSScriptRoot\extract-rf.ps1" -DataRoot $DataRoot *>> $pipelineLog
Write-Log "Extracao concluida."

Write-Log "=== Importacao (geocode + Elasticsearch) ==="
Write-Log "Requer backend em $ApiBase"

$maxRetries = 30
$importStarted = $false
for ($i = 1; $i -le $maxRetries; $i++) {
    try {
        $login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
            -ContentType "application/json" `
            -Body (@{ email = "demo@prospectportal.com"; password = "demo123" } | ConvertTo-Json) `
            -TimeoutSec 10
        $headers = @{ Authorization = "Bearer $($login.token)" }

        $status = Invoke-RestMethod -Uri "$ApiBase/api/admin/import/rf/status" -Headers $headers -TimeoutSec 10
        if ($status.running) {
            Write-Log "Importacao ja em execucao. Monitorando job existente."
            $importStarted = $true
            break
        }

        $body = @{
            states = $States
            loadEmpresas = $true
            geocodeAfterImport = $true
            syncElasticsearch = $true
            estabelecimentoFiles = @()
        } | ConvertTo-Json

        $result = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/admin/import/rf" `
            -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 30

        Write-Log "Importacao iniciada. Job ID: $($result.id)"
        $importStarted = $true
        break
    } catch {
        Write-Log "Backend indisponivel (tentativa $i/$maxRetries): $($_.Exception.Message)"
        Start-Sleep -Seconds 30
    }
}

if (-not $importStarted) {
    Write-Log "ERRO: nao foi possivel iniciar importacao. Suba o backend e rode: .\start-rf-import.ps1 -States $($States -join ',')"
    exit 1
}

Write-Log "=== Monitorando importacao ==="
while ($true) {
    Start-Sleep -Seconds 120
    try {
        $login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
            -ContentType "application/json" `
            -Body (@{ email = "demo@prospectportal.com"; password = "demo123" } | ConvertTo-Json) -TimeoutSec 10
        $headers = @{ Authorization = "Bearer $($login.token)" }
        $status = Invoke-RestMethod -Uri "$ApiBase/api/admin/import/rf/status" -Headers $headers -TimeoutSec 10

        if ($status.running) {
            $job = $status.recentJobs | Where-Object { $_.status -eq "RUNNING" } | Select-Object -First 1
            if ($job) {
                Write-Log "RUNNING | processadas=$($job.processedRows) inseridas=$($job.insertedRows) ignoradas=$($job.skippedRows)"
            }
        } else {
            $last = $status.recentJobs | Select-Object -First 1
            Write-Log "Importacao finalizada: $($last.status) | inseridas=$($last.insertedRows) | erro=$($last.errorMessage)"
            break
        }
    } catch {
        Write-Log "Erro ao consultar status: $($_.Exception.Message)"
    }
}

Write-Log "Pipeline automatica concluida. Log: $pipelineLog"
