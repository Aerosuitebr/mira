# Esteira automatica MIRA: working tree -> quality -> commit -> push -> deploy.
#
# Uso:
#   .\scripts\deploy\auto-deploy.ps1
#   .\scripts\deploy\auto-deploy.ps1 -Message "fix(ui): contraste do select"
#   .\scripts\deploy\auto-deploy.ps1 -Watch
#   .\scripts\deploy\auto-deploy.ps1 -ForceDeploy
#   .\scripts\deploy\auto-deploy.ps1 -QualityOnly
#
# Entrada rapida: deploy.bat na raiz do repo.

param(
    [string]$Message = '',
    [switch]$Watch,
    [int]$WatchSeconds = 45,
    [int]$SettleSeconds = 8,
    [switch]$ForceDeploy,
    [switch]$SkipPush,
    [switch]$SkipDeploy,
    [switch]$SkipQuality,
    [switch]$QualityOnly,
    [switch]$SkipFrontendBuild
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$mvnSettings = Join-Path $backend '.mvn\settings.xml'
$backendLog = Join-Path $env:TEMP 'mira-auto-deploy-backend.log'
$backendErrLog = Join-Path $env:TEMP 'mira-auto-deploy-backend-error.log'
$qualityScript = Join-Path $PSScriptRoot 'quality-gate.ps1'

function Write-Banner([string]$Text) {
    Write-Host ''
    Write-Host ('=' * 64) -ForegroundColor DarkCyan
    Write-Host " $Text" -ForegroundColor Cyan
    Write-Host ('=' * 64) -ForegroundColor DarkCyan
}

function Write-Step([string]$Text) {
    Write-Host ''
    Write-Host "-- $Text" -ForegroundColor Cyan
}

function Import-DotEnv([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { return }
        $eq = $line.IndexOf('=')
        if ($eq -lt 1) { return }
        Set-Item -Path ("Env:" + $line.Substring(0, $eq).Trim()) -Value $line.Substring($eq + 1).Trim()
    }
}

function Test-LocalPort([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(400)) { return $false }
        $client.EndConnect($async)
        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-GitPorcelain {
    @(git -C $root status --porcelain)
}

function Test-WorkingTreeDirty {
    return (Get-GitPorcelain).Count -gt 0
}

function Get-ChangedPaths {
    $paths = @()
    $paths += @(git -C $root diff --name-only)
    $paths += @(git -C $root diff --cached --name-only)
    $paths += @(git -C $root ls-files --others --exclude-standard)
    return $paths | Where-Object { $_ } | Select-Object -Unique
}

function New-AutoCommitMessage([string[]]$Paths) {
    if ($Message -and $Message.Trim().Length -gt 0) {
        return $Message.Trim()
    }

    $hasFront = $Paths | Where-Object { $_ -like 'frontend/*' }
    $hasBack = $Paths | Where-Object { $_ -like 'backend/*' }
    $hasDeploy = $Paths | Where-Object { $_ -like 'scripts/deploy/*' -or $_ -eq 'deploy.bat' -or $_ -like '.github/*' }
    $hasDocker = $Paths | Where-Object { $_ -like 'docker-compose*' -or $_ -like 'docs/*' }

    $scope = 'mira'
    if ($hasFront -and -not $hasBack) { $scope = 'ui' }
    elseif ($hasBack -and -not $hasFront) { $scope = 'api' }
    elseif ($hasDeploy -and -not $hasFront -and -not $hasBack) { $scope = 'deploy' }

    $type = 'chore'
    if ($hasFront -or $hasBack) { $type = 'feat' }
    if (($Paths | Where-Object { $_ -match 'fix|bug|hotfix' }).Count -gt 0) { $type = 'fix' }
    if ($hasDeploy -and -not $hasFront -and -not $hasBack) { $type = 'ci' }

    $summary = switch ($scope) {
        'ui' { 'atualiza frontend do portal' }
        'api' { 'atualiza backend da API' }
        'deploy' { 'atualiza esteira de deploy e qualidade' }
        default { 'publica mudancas do Prospect Portal' }
    }

    if ($hasDocker) {
        $summary = "$summary e infra"
    }

    $count = $Paths.Count
    return "$type($scope): $summary ($count arquivo(s))"
}

function Assert-GitReady {
    Write-Step 'Git: validando repositorio e remote'
    if (-not (Test-Path (Join-Path $root '.git'))) {
        throw 'Diretorio nao e um repositorio git.'
    }

    $remote = git -C $root remote
    if (-not $remote) {
        throw 'Nenhum remote configurado. Configure origin antes do push.'
    }

    $branch = (git -C $root rev-parse --abbrev-ref HEAD).Trim()
    Write-Host "Branch: $branch | Remote: $((git -C $root remote get-url origin).Trim())" -ForegroundColor DarkGray
    return $branch
}

function Invoke-QualityGate([string[]]$Paths) {
    if ($SkipQuality) {
        Write-Host 'Quality gate PULADO (-SkipQuality)' -ForegroundColor Yellow
        return
    }

    Write-Step 'Esteira: quality gate'
    $backendTouched = ($Paths | Where-Object { $_ -like 'backend/*' }).Count -gt 0
    $frontendTouched = ($Paths | Where-Object { $_ -like 'frontend/*' }).Count -gt 0

    $args = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $qualityScript)

    if ($backendTouched -and $frontendTouched) {
        # gate completo
    } elseif ($backendTouched -and -not $frontendTouched) {
        $args += '-BackendOnly'
    } elseif ($frontendTouched -and -not $backendTouched) {
        $args += '-FrontendOnly'
    } else {
        # so scripts/docs/ci: checagens leves (segredos + travessao), sem rebuild
        $env:MIRA_QUALITY_LIGHT = '1'
    }

    if ($SkipFrontendBuild) {
        $args += '-SkipFrontendBuild'
    }

    try {
        & powershell.exe @args
        if ($LASTEXITCODE -ne 0) {
            throw "Quality gate falhou (exit $LASTEXITCODE). Deploy abortado."
        }
    } finally {
        Remove-Item Env:MIRA_QUALITY_LIGHT -ErrorAction SilentlyContinue
    }
}

function Invoke-CommitAndPush([string]$Branch, [string[]]$Paths) {
    Write-Step 'Git: staging + commit'
    git -C $root add -A | Out-Null

    $staged = @(git -C $root diff --cached --name-only)
    if ($staged.Count -eq 0) {
        Write-Host 'Nada para commitar apos staging.' -ForegroundColor DarkGray
        return $false
    }

    foreach ($p in $staged) {
        $leaf = Split-Path $p -Leaf
        if (($leaf -match '^\.env' -or $p -match '(^|/|\\)\.env($|\.)') -and $leaf -notmatch 'example|sample') {
            git -C $root reset HEAD -- $p 2>$null | Out-Null
            throw "Bloqueado: tentativa de commitar segredo ($p)"
        }
    }

    $msg = New-AutoCommitMessage -Paths $Paths
    Write-Host "Mensagem: $msg" -ForegroundColor DarkGray

    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $commitOut = git -C $root commit -m $msg 2>&1 | ForEach-Object { "$_" } | Out-String
    $commitCode = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($commitCode -ne 0) {
        Write-Host $commitOut -ForegroundColor Red
        throw "git commit falhou (exit $commitCode)"
    }

    $sha = (git -C $root rev-parse --short HEAD).Trim()
    Write-Host "Commit $sha criado." -ForegroundColor Green

    if ($SkipPush) {
        Write-Host 'Push pulado (-SkipPush)' -ForegroundColor Yellow
        return $true
    }

    Write-Step 'Git: push para origin'
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $pushOut = git -C $root push -u origin "HEAD:$Branch" 2>&1 | ForEach-Object { "$_" } | Out-String
    $pushCode = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($pushCode -ne 0) {
        Write-Host $pushOut -ForegroundColor Red
        throw "git push falhou (exit $pushCode)"
    }
    if ($pushOut.Trim()) {
        Write-Host $pushOut.Trim() -ForegroundColor DarkGray
    }
    Write-Host 'Push OK' -ForegroundColor Green
    return $true
}

function Stop-BackendOnPort {
    Write-Step 'Deploy: liberando porta 8082'
    $listeners = @(Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue)
    foreach ($l in $listeners) {
        $procId = $l.OwningProcess
        if (-not $procId) { continue }
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$procId" -ErrorAction SilentlyContinue
        if ($proc -and $proc.ParentProcessId) {
            Stop-Process -Id $proc.ParentProcessId -Force -ErrorAction SilentlyContinue
        }
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Host "Encerrado PID $procId" -ForegroundColor DarkGray
    }
    Start-Sleep -Seconds 2
    if (Test-LocalPort 8082) {
        throw 'Porta 8082 ainda ocupada apos tentativa de stop.'
    }
}

function Start-Backend {
    Write-Step 'Deploy: iniciando Spring Boot (:8082)'
    Import-DotEnv (Join-Path $root '.env')
    Import-DotEnv (Join-Path $root '.env.production')

    if (Test-Path $backendLog) { Remove-Item $backendLog -Force -ErrorAction SilentlyContinue }
    if (Test-Path $backendErrLog) { Remove-Item $backendErrLog -Force -ErrorAction SilentlyContinue }

    Start-Process -FilePath 'mvn.cmd' `
        -ArgumentList @('-s', $mvnSettings, '-DskipTests', 'spring-boot:run') `
        -WorkingDirectory $backend `
        -WindowStyle Hidden `
        -RedirectStandardOutput $backendLog `
        -RedirectStandardError $backendErrLog

    $ready = $false
    for ($i = 0; $i -lt 180; $i++) {
        Start-Sleep -Seconds 1
        if (Test-LocalPort 8082) {
            try {
                $h = Invoke-WebRequest -Uri 'http://127.0.0.1:8082/actuator/health' -UseBasicParsing -TimeoutSec 3
                $body = if ($h.Content -is [byte[]]) {
                    [System.Text.Encoding]::UTF8.GetString($h.Content)
                } else {
                    [string]$h.Content
                }
                if ([int]$h.StatusCode -eq 200 -and ($body -match '"status"\s*:\s*"UP"' -or $body -match 'UP')) {
                    $ready = $true
                    break
                }
            } catch {
                # ainda subindo
            }
        }
        if ($i -gt 0 -and ($i % 15) -eq 0) {
            Write-Host "  aguardando API... ($i s)" -ForegroundColor DarkGray
        }
    }

    if (-not $ready) {
        Write-Host "Log: $backendLog" -ForegroundColor Red
        Write-Host "Err: $backendErrLog" -ForegroundColor Red
        if (Test-Path $backendErrLog) {
            Get-Content $backendErrLog -Tail 30 | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkRed }
        }
        throw 'Backend nao ficou healthy em 180s.'
    }

    Write-Host 'API healthy em http://127.0.0.1:8082' -ForegroundColor Green
}

function Stop-FrontendOnPort {
    Write-Step 'Deploy: liberando porta 4201'
    $listeners = @(Get-NetTCPConnection -LocalPort 4201 -State Listen -ErrorAction SilentlyContinue)
    foreach ($l in $listeners) {
        $procId = $l.OwningProcess
        if (-not $procId) { continue }
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$procId" -ErrorAction SilentlyContinue
        # mata node do ng serve e o pai npm, se houver
        if ($proc -and $proc.ParentProcessId) {
            Stop-Process -Id $proc.ParentProcessId -Force -ErrorAction SilentlyContinue
        }
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Host "Encerrado frontend PID $procId" -ForegroundColor DarkGray
    }
    Start-Sleep -Seconds 2
    if (Test-LocalPort 4201) {
        throw 'Porta 4201 ainda ocupada apos tentativa de stop.'
    }
}

function Ensure-Frontend([switch]$Restart) {
    Write-Step 'Deploy: garantindo frontend (:4201)'
    if ($Restart -and (Test-LocalPort 4201)) {
        Stop-FrontendOnPort
    }

    if (Test-LocalPort 4201) {
        Write-Host 'Frontend ja ativo em http://127.0.0.1:4201 (HMR / processo atual)' -ForegroundColor Green
        return
    }

    Write-Host 'Iniciando ng serve (pode levar 20-60s)...' -ForegroundColor DarkGray
    Start-Process -FilePath 'npm.cmd' `
        -ArgumentList @('start') `
        -WorkingDirectory $frontend `
        -WindowStyle Hidden

    $ready = $false
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Seconds 1
        if (Test-LocalPort 4201) {
            $ready = $true
            break
        }
        if ($i -gt 0 -and ($i % 15) -eq 0) {
            Write-Host "  aguardando frontend... ($i s)" -ForegroundColor DarkGray
        }
    }

    if (-not $ready) {
        throw 'Frontend nao subiu em :4201.'
    }
    Write-Host 'Frontend OK em http://127.0.0.1:4201' -ForegroundColor Green
}

function Get-HttpBodyText($Response) {
    if ($null -eq $Response) { return '' }
    if ($Response.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Response.Content)
    }
    return [string]$Response.Content
}

function Invoke-SmokeChecks {
    Write-Step 'Deploy: smoke checks'
    $checks = @(
        @{ Name = 'API health'; Url = 'http://127.0.0.1:8082/actuator/health'; Expect = 200; BodyMustMatch = 'UP' },
        @{ Name = 'Frontend'; Url = 'http://127.0.0.1:4201/'; Expect = 200; BodyMustMatch = $null },
        @{ Name = 'Tunnel proxy'; Url = 'http://127.0.0.1:8083/'; Expect = 200; BodyMustMatch = $null }
    )

    $failed = @()
    foreach ($c in $checks) {
        try {
            $r = Invoke-WebRequest -Uri $c.Url -UseBasicParsing -TimeoutSec 8
            $ok = [int]$r.StatusCode -eq $c.Expect
            if ($ok -and $c.BodyMustMatch) {
                $body = Get-HttpBodyText $r
                $ok = $body -match [regex]::Escape($c.BodyMustMatch)
            }
            if ($ok) {
                Write-Host ("  OK  {0} ({1})" -f $c.Name, $r.StatusCode) -ForegroundColor Green
            } else {
                Write-Host ("  FAIL {0} status {1}" -f $c.Name, $r.StatusCode) -ForegroundColor Yellow
                $failed += $c.Name
            }
        } catch {
            if ($c.Name -eq 'Tunnel proxy') {
                Write-Host ("  SKIP {0} (indisponivel: {1})" -f $c.Name, $_.Exception.Message) -ForegroundColor DarkGray
            } else {
                Write-Host ("  FAIL {0}: {1}" -f $c.Name, $_.Exception.Message) -ForegroundColor Red
                $failed += $c.Name
            }
        }
    }

    if ($failed.Count -gt 0) {
        throw ("Smoke checks falharam: " + ($failed -join ', '))
    }
}

function Invoke-Deploy([string[]]$Paths, [bool]$HadCommit) {
    if ($SkipDeploy) {
        Write-Host 'Deploy pulado (-SkipDeploy)' -ForegroundColor Yellow
        return
    }

    Write-Banner 'DEPLOY LOCAL'

    # Sempre classificar pelo que foi publicado (HEAD), nao so pela lista pre-commit
    $deployFiles = @($Paths)
    if ($HadCommit -or $deployFiles.Count -eq 0) {
        $deployFiles = @(git -C $root show --pretty='' --name-only HEAD | Where-Object { $_ })
    }

    Write-Host 'Arquivos do deploy (HEAD):' -ForegroundColor DarkGray
    if ($deployFiles.Count -eq 0) {
        Write-Host '  (nenhum)' -ForegroundColor DarkGray
    } else {
        $deployFiles | Select-Object -First 15 | ForEach-Object { Write-Host "  - $_" -ForegroundColor DarkGray }
        if ($deployFiles.Count -gt 15) {
            Write-Host "  ... +$($deployFiles.Count - 15) arquivo(s)" -ForegroundColor DarkGray
        }
    }

    $backendChanged = ($deployFiles | Where-Object { $_ -like 'backend/*' }).Count -gt 0
    $frontendChanged = ($deployFiles | Where-Object { $_ -like 'frontend/*' }).Count -gt 0

    # Front: reinicia ng serve quando houver mudanca de UI (HMR sozinho parece "nada")
    $restartBackend = $ForceDeploy -or $backendChanged -or -not (Test-LocalPort 8082)
    $restartFrontend = $ForceDeploy -or $frontendChanged -or -not (Test-LocalPort 4201)

    if ($ForceDeploy) {
        Write-Host 'Modo: FULL (-ForceDeploy) - reinicia API e frontend.' -ForegroundColor Yellow
    } elseif ($backendChanged -and $frontendChanged) {
        Write-Host 'Modo: FULL - backend + frontend alterados.' -ForegroundColor Yellow
    } elseif ($backendChanged) {
        Write-Host 'Modo: BACKEND RESTART - mudancas Java detectadas (~1-3 min).' -ForegroundColor Yellow
    } elseif ($frontendChanged) {
        Write-Host 'Modo: FRONTEND RESTART - reinicia ng serve para aplicar UI (~20-60s).' -ForegroundColor Yellow
    } else {
        Write-Host 'Modo: VERIFY - este commit nao alterou backend/ nem frontend/.' -ForegroundColor Yellow
        Write-Host '      App em runtime permanece igual. Restart completo: deploy.bat -ForceDeploy' -ForegroundColor DarkGray
    }

    if ($restartBackend) {
        Stop-BackendOnPort
        Start-Backend
    } else {
        Write-Host 'Backend: processo atual mantido.' -ForegroundColor DarkGray
    }

    if ($restartFrontend) {
        Ensure-Frontend -Restart
    } else {
        Write-Host 'Frontend: processo atual mantido.' -ForegroundColor DarkGray
        if (-not (Test-LocalPort 4201)) {
            Ensure-Frontend
        } else {
            Write-Host 'Frontend ja ativo em http://127.0.0.1:4201' -ForegroundColor Green
        }
    }

    if (-not (Test-LocalPort 5433)) {
        Write-Step 'Deploy: subindo infra docker (postgres/redis/es)'
        docker compose --project-directory $root up -d
    }

    Invoke-SmokeChecks

    if ($restartBackend -or $restartFrontend) {
        Write-Host 'Deploy de runtime CONCLUIDO (processo(s) reiniciado(s)).' -ForegroundColor Green
    } else {
        Write-Host 'Nada reiniciado: commit sem codigo de app. Use -ForceDeploy se quiser restart.' -ForegroundColor Yellow
    }
}

function Invoke-PipelineOnce {
    Write-Banner 'MIRA AUTO-DEPLOY'
    Write-Host "Root: $root" -ForegroundColor DarkGray
    Write-Host (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') -ForegroundColor DarkGray

    $branch = Assert-GitReady
    $dirty = Test-WorkingTreeDirty
    $paths = @(Get-ChangedPaths)

    if ($QualityOnly) {
        if ($paths.Count -eq 0) { $paths = @('backend/', 'frontend/') }
        Invoke-QualityGate -Paths $paths
        Write-Host 'QualityOnly concluido.' -ForegroundColor Green
        return
    }

    if (-not $dirty -and -not $ForceDeploy) {
        Write-Host 'Working tree limpa. Nada para commitar.' -ForegroundColor Yellow
        Write-Host 'Use -ForceDeploy para reiniciar servicos mesmo assim.' -ForegroundColor DarkGray
        # Ainda valida qualidade se pedido implicito? skip.
        return
    }

    if ($dirty) {
        Write-Step 'Working tree: mudancas detectadas'
        Get-GitPorcelain | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }

        Invoke-QualityGate -Paths $paths
        $committed = $false
        $commitResult = @(Invoke-CommitAndPush -Branch $branch -Paths $paths)
        if ($commitResult.Count -gt 0) {
            $committed = [System.Convert]::ToBoolean($commitResult[-1])
        }
        Invoke-Deploy -Paths $paths -HadCommit:$committed
    } else {
        Write-Host 'ForceDeploy com tree limpa.' -ForegroundColor DarkGray
        Invoke-Deploy -Paths @() -HadCommit:$false
    }

    Write-Banner 'ESTEIRA CONCLUIDA'
    Write-Host 'Codigo no GitHub + quality gate + smoke local.' -ForegroundColor Green
    Write-Host 'Front: http://127.0.0.1:4201  |  API: http://127.0.0.1:8082  |  Tunnel: http://127.0.0.1:8083' -ForegroundColor DarkGray
    Write-Host 'Dica: mudanca so de CSS/TS usa HMR (rapido). Restart completo: deploy.bat -ForceDeploy' -ForegroundColor DarkGray
}

function Wait-WorkingTreeSettle {
    Write-Host "Aguardando working tree estabilizar ($SettleSeconds s sem mudancas)..." -ForegroundColor DarkGray
    $stableFor = 0
    $last = (Get-GitPorcelain) -join "`n"
    while ($stableFor -lt $SettleSeconds) {
        Start-Sleep -Seconds 1
        $now = (Get-GitPorcelain) -join "`n"
        if ($now -eq $last -and $now.Length -gt 0) {
            $stableFor++
        } else {
            $stableFor = 0
            $last = $now
        }
    }
}

# ── entry ──
Push-Location $root
try {
    if ($Watch) {
        Write-Banner 'MODO WATCH'
        Write-Host "Intervalo: ${WatchSeconds}s | Settle: ${SettleSeconds}s" -ForegroundColor DarkGray
        Write-Host 'Ctrl+C para encerrar.' -ForegroundColor DarkGray
        while ($true) {
            if (Test-WorkingTreeDirty) {
                Wait-WorkingTreeSettle
                if (Test-WorkingTreeDirty) {
                    try {
                        Invoke-PipelineOnce
                    } catch {
                        Write-Host "ERRO na esteira: $($_.Exception.Message)" -ForegroundColor Red
                        Write-Host 'Watch continua; corrija e salve de novo.' -ForegroundColor Yellow
                    }
                }
            }
            Start-Sleep -Seconds $WatchSeconds
        }
    } else {
        Invoke-PipelineOnce
    }
} catch {
    Write-Host ''
    Write-Host "FALHA: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}

exit 0
