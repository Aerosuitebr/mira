# Quality gate do MIRA Prospect Portal.
# Uso: .\scripts\deploy\quality-gate.ps1
#      .\scripts\deploy\quality-gate.ps1 -BackendOnly
#      .\scripts\deploy\quality-gate.ps1 -FrontendOnly

param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$SkipFrontendBuild
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$mvnSettings = Join-Path $backend '.mvn\settings.xml'

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host "==> $Text" -ForegroundColor Cyan
}

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Comando obrigatorio nao encontrado no PATH: $Name"
    }
}

function Invoke-BackendCompile {
    Write-Step 'Quality: compilando backend (Maven)'
    Assert-Command 'mvn.cmd'
    Push-Location $backend
    try {
        & mvn.cmd -s $mvnSettings -DskipTests -q compile
        if ($LASTEXITCODE -ne 0) {
            throw "Maven compile falhou (exit $LASTEXITCODE)"
        }
    } finally {
        Pop-Location
    }
    Write-Host 'Backend OK (compile)' -ForegroundColor Green
}

function Invoke-FrontendBuild {
    Write-Step 'Quality: build de producao do frontend (Angular)'
    Assert-Command 'npm.cmd'
    Push-Location $frontend
    try {
        if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
            Write-Host 'node_modules ausente; rodando npm ci...' -ForegroundColor DarkGray
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) {
                throw "npm ci falhou (exit $LASTEXITCODE)"
            }
        }

        & npm.cmd run build -- --configuration=production
        if ($LASTEXITCODE -ne 0) {
            throw "ng build production falhou (exit $LASTEXITCODE)"
        }
    } finally {
        Pop-Location
    }
    Write-Host 'Frontend OK (production build)' -ForegroundColor Green
}

function Test-SecretLeakInTree {
    Write-Step 'Quality: varredura de segredos na staging area / working tree'
    $denyName = @(
        '\.env$',
        '\.env\.',
        'credentials\.json$',
        'secret',
        '\.pem$',
        '\.p12$',
        '\.key$',
        'id_rsa',
        'id_ed25519'
    )

    $paths = @()
    $paths += @(git -C $root status --porcelain | ForEach-Object { $_.Substring(3).Trim().Trim('"') })
    $paths += @(git -C $root diff --cached --name-only)
    $paths = $paths | Where-Object { $_ } | Select-Object -Unique

    $blocked = @()
    foreach ($p in $paths) {
        $leaf = Split-Path $p -Leaf
        foreach ($rx in $denyName) {
            if ($leaf -match $rx -or $p -match '(^|/|\\)\.env($|\.)') {
                # allow example templates
                if ($leaf -match '\.example$' -or $leaf -match '\.sample$') { continue }
                $blocked += $p
                break
            }
        }
    }

    if ($blocked.Count -gt 0) {
        Write-Host 'Arquivos bloqueados (possivel segredo):' -ForegroundColor Red
        $blocked | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
        throw 'Quality gate bloqueou commit/deploy por risco de segredo.'
    }

    Write-Host 'Varredura de segredos OK' -ForegroundColor Green
}

function Get-ChangedSourceFiles {
    $dirty = @(git -C $root status --porcelain)
    $paths = @()

    if ($dirty.Count -gt 0) {
        $paths += @($dirty | ForEach-Object { $_.Substring(3).Trim().Trim('"') })
        $paths += @(git -C $root diff --cached --name-only)
        $paths += @(git -C $root diff --name-only)
        $paths += @(git -C $root ls-files --others --exclude-standard)
    } elseif ($env:GITHUB_BASE_REF) {
        git -C $root fetch origin $env:GITHUB_BASE_REF --depth=1 2>$null | Out-Null
        $paths += @(git -C $root diff --name-only "origin/$($env:GITHUB_BASE_REF)...HEAD")
    } elseif (git -C $root rev-parse --verify HEAD^ 2>$null) {
        $paths += @(git -C $root diff --name-only HEAD^ HEAD)
    }

    return $paths |
        Where-Object { $_ } |
        Where-Object { $_ -match '\.(ts|html|scss|java|yml|yaml|md)$' } |
        Where-Object { $_ -notmatch 'db/migration/' } |
        Select-Object -Unique
}

function Invoke-EmDashCopyCheck {
    Write-Step 'Quality: checagem de travessao em copy alterado'
    $changed = @(Get-ChangedSourceFiles)
    if ($changed.Count -eq 0) {
        Write-Host 'Sem arquivos de copy alterados; skip travessao.' -ForegroundColor DarkGray
        return
    }

    $hits = @()
    foreach ($rel in $changed) {
        $full = Join-Path $root $rel
        if (-not (Test-Path -LiteralPath $full)) { continue }
        $content = Get-Content -LiteralPath $full -Raw -ErrorAction SilentlyContinue
        if (-not $content) { continue }
        if ($content.Contains([char]0x2014) -or $content.Contains([char]0x2013)) {
            $hits += $rel
        }
    }

    if ($hits.Count -gt 0) {
        Write-Host 'Travessao (—/–) encontrado em arquivos alterados:' -ForegroundColor Yellow
        $hits | Select-Object -First 30 | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
        throw 'Quality gate: remova travessoes do copy alterado (use virgula, dois-pontos ou ponto medio).'
    }

    Write-Host "Copy sem travessao OK ($($changed.Count) arquivo(s) checados)" -ForegroundColor Green
}

Write-Host '=== MIRA Quality Gate ===' -ForegroundColor Cyan
Write-Host "Root: $root" -ForegroundColor DarkGray

Test-SecretLeakInTree
Invoke-EmDashCopyCheck

$runBackend = -not $FrontendOnly
$runFrontend = -not $BackendOnly

if ($env:MIRA_QUALITY_LIGHT -eq '1') {
    Write-Host 'Modo leve (MIRA_QUALITY_LIGHT=1): sem compile/build' -ForegroundColor DarkGray
    $runBackend = $false
    $runFrontend = $false
}

if ($runBackend) {
    Invoke-BackendCompile
}

if ($runFrontend) {
    if ($SkipFrontendBuild) {
        Write-Host 'Frontend build pulado (-SkipFrontendBuild)' -ForegroundColor DarkGray
    } else {
        Invoke-FrontendBuild
    }
}

Write-Host ''
Write-Host 'Quality gate APROVADO' -ForegroundColor Green
exit 0
