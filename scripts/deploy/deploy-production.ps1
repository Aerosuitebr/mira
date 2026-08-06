# Deploy de producao MIRA via SSH no Vultr (bypass do GitHub Actions).
#
# Uso:
#   .\scripts\deploy\deploy-production.ps1
#   .\scripts\deploy\deploy-production.ps1 -Ref HEAD
#   .\scripts\deploy\deploy-production.ps1 -HostName 216.238.102.195
#
# Requer: ssh/scp + chave ~/.ssh/aerosuite_ed25519 (ou $env:MIRA_SSH_KEY)

param(
    [string]$Ref = 'HEAD',
    [string]$HostName = $(if ($env:MIRA_SSH_HOST) { $env:MIRA_SSH_HOST } else { '216.238.102.195' }),
    [string]$User = $(if ($env:MIRA_SSH_USER) { $env:MIRA_SSH_USER } else { 'root' }),
    [string]$IdentityFile = $(if ($env:MIRA_SSH_KEY) { $env:MIRA_SSH_KEY } else { Join-Path $env:USERPROFILE '.ssh\aerosuite_ed25519' })
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')

function Write-Step([string]$Text) {
    Write-Host ''
    Write-Host "-- $Text" -ForegroundColor Cyan
}

if (-not (Test-Path $IdentityFile)) {
    throw "Chave SSH nao encontrada: $IdentityFile"
}

$sha = (git -C $root rev-parse $Ref).Trim()
$short = $sha.Substring(0, [Math]::Min(7, $sha.Length))
$tgz = Join-Path $env:TEMP 'mira-release.tgz'
$shaFile = Join-Path $env:TEMP 'mira-release.sha'
$sshTarget = "${User}@${HostName}"
$sshArgs = @('-i', $IdentityFile, '-o', 'StrictHostKeyChecking=accept-new', '-o', 'ConnectTimeout=20')

Write-Host "Deploy producao $short ($sha) -> $sshTarget" -ForegroundColor Cyan

Write-Step 'Empacotando release'
if (Test-Path $tgz) { Remove-Item $tgz -Force }
git -C $root archive --format=tar.gz -o $tgz $Ref -- `
    backend/src/main/java `
    backend/src/main/resources `
    frontend/src `
    frontend/angular.json `
    frontend/nginx.conf `
    frontend/package.json `
    frontend/package-lock.json `
    services/outreach-bot `
    docker-compose.production.yml `
    docker-compose.tunnel.yml `
    docker-compose.yml `
    .env.production.example `
    scripts/deploy/nginx-mira.conf `
    scripts/deploy/prod-remote.sh `
    scripts/deploy/mira-poll-deploy.sh `
    scripts/deploy/install-mira-poller.sh
[System.IO.File]::WriteAllText($shaFile, $sha)

Write-Step 'Upload para o servidor'
& scp.exe @sshArgs $tgz $shaFile "${sshTarget}:/tmp/"
if ($LASTEXITCODE -ne 0) { throw "scp falhou (exit $LASTEXITCODE)" }

$remoteScript = Join-Path $PSScriptRoot 'prod-remote.sh'
& scp.exe @sshArgs $remoteScript "${sshTarget}:/tmp/mira-prod-remote.sh"
if ($LASTEXITCODE -ne 0) { throw "scp do prod-remote.sh falhou (exit $LASTEXITCODE)" }

Write-Step 'Rebuild + smoke no Vultr'
$remoteCmd = "sed -i 's/\r$//' /tmp/mira-prod-remote.sh && bash /tmp/mira-prod-remote.sh"
& ssh.exe @sshArgs $sshTarget $remoteCmd
if ($LASTEXITCODE -ne 0) { throw "deploy remoto falhou (exit $LASTEXITCODE)" }

Write-Step 'Smoke publico'
try {
    $code = [int](curl.exe -s -o NUL -w '%{http_code}' https://search.aerosuite.com.br/)
    Write-Host "public_http=$code" -ForegroundColor $(if ($code -eq 200) { 'Green' } else { 'Yellow' })
} catch {
    Write-Host "Smoke publico indisponivel: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ''
Write-Host "Producao em $short. Atualize https://search.aerosuite.com.br (Ctrl+F5)." -ForegroundColor Green
