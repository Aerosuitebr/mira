#Requires -Version 5.1
<#
.SYNOPSIS
  Prepara PostgreSQL persistente e aplica o schema Flyway do Prospect Portal.

.DESCRIPTION
  Modo padrão (Docker + PostGIS):
    - Dados em C:\prospect-portal-data\postgres (sobrevive a recriação de imagem/container)
    - Sobe o serviço postgres do docker-compose
    - Aplica migrations V1..V4 via Flyway

  Modo -InstallLocal:
    - Instala PostgreSQL via winget (se ausente)
    - Cria role/database prospect / prospect_portal
    - Requer PostGIS instalado no PostgreSQL local (Stack Builder / instalador PostGIS)
    - Ajusta application.yml para porta 5432 (perfil local)

.PARAMETER DataRoot
  Raiz dos dados persistentes (padrão: C:\prospect-portal-data)

.PARAMETER InstallLocal
  Usar PostgreSQL instalado no Windows em vez do container Docker.

.PARAMETER PostgresSuperPassword
  Senha do superusuário postgres (obrigatória em -InstallLocal).

.EXAMPLE
  .\setup-database.ps1

.EXAMPLE
  .\setup-database.ps1 -InstallLocal -PostgresSuperPassword "sua-senha-postgres"
#>
param(
    [string]$DataRoot = "C:\prospect-portal-data",
    [switch]$InstallLocal,
    [string]$PostgresSuperPassword = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$BackendDir = Join-Path $ProjectRoot "backend"
$SqlDir = Join-Path $PSScriptRoot "sql"
$PostgresDataDir = Join-Path $DataRoot "postgres"
$DbName = "prospect_portal"
$DbUser = "prospect"
$DbPassword = "prospect"
$DockerPort = 5433
$LocalPort = 5432
$PsqlDocker = @("docker", "exec", "-i", "prospect-portal-postgres", "psql", "-U", $DbUser, "-d", $DbName)

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Invoke-DockerCompose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$ComposeArgs)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker compose @ComposeArgs
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose $($ComposeArgs -join ' ') falhou (exit $LASTEXITCODE)"
        }
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Wait-PostgresDocker {
    param([int]$TimeoutSeconds = 90)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $health = docker inspect --format "{{.State.Health.Status}}" prospect-portal-postgres 2>$null
        if ($health -eq "healthy") { return }
        Start-Sleep -Seconds 2
    }
    throw "PostgreSQL Docker nao ficou healthy em ${TimeoutSeconds}s."
}

function Invoke-FlywayMigrate([int]$Port) {
    Write-Step "Aplicando schema Flyway (migrations V1..V4) na porta $Port"
    Push-Location $BackendDir
    try {
        mvn -s .mvn/settings.xml -q flyway:migrate `
            "-Dflyway.url=jdbc:postgresql://localhost:${Port}/${DbName}" `
            "-Dflyway.user=$DbUser" `
            "-Dflyway.password=$DbPassword"
    } finally {
        Pop-Location
    }
}

function Test-PostgresTables([int]$Port) {
    if ($InstallLocal) {
        $env:PGPASSWORD = $DbPassword
        $psql = Resolve-PsqlPath
        $count = & $psql -U $DbUser -h localhost -p $Port -d $DbName -tAc `
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    } else {
        $count = docker exec prospect-portal-postgres psql -U $DbUser -d $DbName -tAc `
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
    }
    return [int]$count.Trim()
}

function Resolve-PsqlPath {
    $candidates = @(
        "C:\Program Files\PostgreSQL\18\bin\psql.exe",
        "C:\Program Files\PostgreSQL\17\bin\psql.exe",
        "C:\Program Files\PostgreSQL\16\bin\psql.exe"
    )
    foreach ($path in $candidates) {
        if (Test-Path $path) { return $path }
    }
    $fromPath = Get-Command psql -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }
    throw "psql não encontrado. Instale PostgreSQL ou use o modo Docker (sem -InstallLocal)."
}

function Ensure-LocalPostgres {
    $service = Get-Service -Name "postgresql*" -ErrorAction SilentlyContinue | Where-Object { $_.Status -eq "Running" } | Select-Object -First 1
    if (-not $service) {
        Write-Step "Instalando PostgreSQL 16 via winget (se necessário)"
        winget install --id PostgreSQL.PostgreSQL.16 --accept-package-agreements --accept-source-agreements --silent
        $service = Get-Service -Name "postgresql*" -ErrorAction SilentlyContinue | Where-Object { $_.Status -eq "Running" } | Select-Object -First 1
        if (-not $service) {
            throw "PostgreSQL local não está em execução após instalação. Inicie o serviço postgresql-x64-*."
        }
    }
    Write-Host "Serviço PostgreSQL: $($service.DisplayName)" -ForegroundColor Green
}

function Initialize-LocalDatabase {
    if (-not $PostgresSuperPassword) {
        throw "Informe -PostgresSuperPassword para configurar PostgreSQL local."
    }
    $psql = Resolve-PsqlPath
    $env:PGPASSWORD = $PostgresSuperPassword

    Write-Step "Criando role e database ($DbUser / $DbName)"
    & $psql -U postgres -h localhost -p $LocalPort -f (Join-Path $SqlDir "00-init-role-database.sql")

    Write-Step "Habilitando extensões PostGIS e pg_trgm"
    try {
        & $psql -U postgres -h localhost -p $LocalPort -d $DbName -f (Join-Path $SqlDir "01-init-extensions.sql")
    } catch {
        Write-Warning @"
PostGIS não encontrado no PostgreSQL local.
Instale via Stack Builder (componente PostGIS) ou use o modo Docker padrão:
  .\setup-database.ps1
"@
        throw
    }

    & $psql -U postgres -h localhost -p $LocalPort -d $DbName -c `
        "GRANT ALL ON SCHEMA public TO $DbUser; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO $DbUser;"

    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

# --- main ---
Write-Step "Prospect Portal - setup de banco de dados"
New-Item -ItemType Directory -Force -Path $PostgresDataDir | Out-Null
Write-Host "Dados persistentes: $PostgresDataDir"

if ($InstallLocal) {
    Ensure-LocalPostgres
    Initialize-LocalDatabase
    Invoke-FlywayMigrate -Port $LocalPort
    $tables = Test-PostgresTables -Port $LocalPort
    Write-Host "`nConcluido (PostgreSQL local :$LocalPort). Tabelas public: $tables" -ForegroundColor Green
    Write-Host "Atualize backend/src/main/resources/application.yml para jdbc:postgresql://localhost:5432/prospect_portal"
    exit 0
}

Write-Step "Recriando container PostgreSQL com volume persistente no host"
Push-Location $ProjectRoot
try {
    $env:PROSPECT_DATA_ROOT = $DataRoot.Replace("\", "/")
    Invoke-DockerCompose stop postgres
    Invoke-DockerCompose rm -f postgres
    Invoke-DockerCompose up -d postgres
    Wait-PostgresDocker
} finally {
    Pop-Location
    Remove-Item Env:PROSPECT_DATA_ROOT -ErrorAction SilentlyContinue
}

Invoke-FlywayMigrate -Port $DockerPort
$tables = Test-PostgresTables -Port $DockerPort
$users = docker exec prospect-portal-postgres psql -U $DbUser -d $DbName -tAc "SELECT COUNT(*) FROM users;"

Write-Host "`nConcluido (Docker PostGIS :$DockerPort)." -ForegroundColor Green
Write-Host "  Pasta de dados : $PostgresDataDir"
Write-Host "  Tabelas public : $tables"
Write-Host "  Usuarios demo  : $users"
Write-Host "  Login          : demo@prospectportal.com / demo123"
