$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$backendLog = Join-Path $env:TEMP 'prospect-portal-backend.log'
$backendErrorLog = Join-Path $env:TEMP 'prospect-portal-backend-error.log'

function Import-DotEnv([string] $Path) {
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { return }
        $eq = $line.IndexOf('=')
        if ($eq -lt 1) { return }
        $name = $line.Substring(0, $eq).Trim()
        $value = $line.Substring($eq + 1).Trim()
        Set-Item -Path "Env:$name" -Value $value
    }
}

Import-DotEnv (Join-Path $root '.env')
Import-DotEnv (Join-Path $root '.env.production')

function Test-LocalPort([int] $Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $connection.AsyncWaitHandle.WaitOne(400)) {
            return $false
        }
        $client.EndConnect($connection)
        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-InfraReady {
    param(
        [int] $TimeoutSeconds = 180
    )

    Write-Host 'Aguardando infraestrutura (Postgres + Elasticsearch)...' -ForegroundColor Cyan

    $ready = $false
    for ($attempt = 0; $attempt -lt $TimeoutSeconds; $attempt++) {
        $postgresUp = Test-LocalPort 5433
        $elasticsearchUp = $false

        if ($postgresUp) {
            try {
                $response = Invoke-WebRequest -Uri 'http://localhost:9201/_cluster/health' -TimeoutSec 3 -UseBasicParsing
                if ($response.Content -match '"status":"(green|yellow)"') {
                    $elasticsearchUp = $true
                }
            } catch {
                $elasticsearchUp = $false
            }
        }

        if ($postgresUp -and $elasticsearchUp) {
            $ready = $true
            break
        }

        if ($attempt -gt 0 -and ($attempt % 10) -eq 0) {
            $pg = if ($postgresUp) { 'ok' } else { 'aguardando' }
            $es = if ($elasticsearchUp) { 'ok' } else { 'aguardando' }
            Write-Host "  postgres: $pg | elasticsearch: $es ($attempt s)" -ForegroundColor DarkGray
        }

        Start-Sleep -Seconds 1
    }

    if (-not $ready) {
        Write-Host 'Infraestrutura não ficou pronta a tempo. Verifique: docker compose ps' -ForegroundColor Red
        exit 1
    }

    Write-Host 'Infraestrutura pronta.' -ForegroundColor Green
}

Write-Host 'Preparando Prospect Portal...' -ForegroundColor Cyan

try {
    docker info *> $null
} catch {
    Write-Host 'Docker Desktop não está rodando. Inicie o Docker e tente novamente.' -ForegroundColor Red
    exit 1
}

docker compose --project-directory $root up -d

Wait-InfraReady -TimeoutSeconds 180

if (-not (Test-LocalPort 8082)) {
    Write-Host 'Iniciando backend (primeira execução pode levar 1-2 min por causa do Maven)...' -ForegroundColor Cyan
    Start-Process -FilePath 'mvn.cmd' `
        -ArgumentList '-s', '.mvn/settings.xml', 'spring-boot:run' `
        -WorkingDirectory $backend `
        -WindowStyle Hidden `
        -RedirectStandardOutput $backendLog `
        -RedirectStandardError $backendErrorLog

    $ready = $false
    for ($attempt = 0; $attempt -lt 180; $attempt++) {
        Start-Sleep -Seconds 1
        if (Test-LocalPort 8082) {
            $ready = $true
            break
        }
        if ($attempt -gt 0 -and ($attempt % 15) -eq 0) {
            Write-Host "  aguardando backend... ($attempt s)" -ForegroundColor DarkGray
        }
    }

    if (-not $ready) {
        Write-Host "O backend não iniciou em 180 s. Consulte:" -ForegroundColor Red
        Write-Host "  $backendErrorLog" -ForegroundColor Red
        Write-Host "  $backendLog" -ForegroundColor Red
        if (Test-Path $backendErrorLog) {
            Get-Content $backendErrorLog -Tail 20 | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkRed }
        }
        exit 1
    }
}

Write-Host 'Backend disponível em http://localhost:8082' -ForegroundColor Green

if (Test-LocalPort 4201) {
    Write-Host 'Frontend já está ativo em http://localhost:4201' -ForegroundColor Green
    exit 0
}

Write-Host 'Abrindo frontend em http://localhost:4201' -ForegroundColor Green
Set-Location $frontend
npm.cmd start
