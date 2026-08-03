$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$tunnelId = $env:MIRA_TUNNEL_ID
if (-not $tunnelId) { $tunnelId = '6d599ea8-2354-4c3c-9968-5ded651c92fc' }

$hosts = @(
  'search.aerosuite.app',
  'search.aerosuite.com.br'
)

Write-Host '=== MIRA - preparar exposicao via Cloudflare Tunnel ===' -ForegroundColor Cyan
Write-Host "Tunnel ID: $tunnelId" -ForegroundColor DarkGray
Write-Host ''

try {
  docker info *> $null
} catch {
  Write-Host 'Docker Desktop nao esta rodando.' -ForegroundColor Red
  exit 1
}

Write-Host '1/3 Subindo proxy nginx (porta 8083)...' -ForegroundColor Cyan
docker compose --project-directory $root -f (Join-Path $root 'docker-compose.tunnel.yml') up -d

Start-Sleep -Seconds 2
try {
  $probe = Invoke-WebRequest -Uri 'http://localhost:8083' -TimeoutSec 5 -UseBasicParsing
  Write-Host "   Proxy OK (HTTP $($probe.StatusCode))" -ForegroundColor Green
} catch {
  Write-Host '   Proxy ainda nao responde - confira frontend :4201 e backend :8082.' -ForegroundColor Yellow
}

Write-Host ''
Write-Host '2/3 Registrar DNS no Cloudflare (se cloudflared estiver instalado)...' -ForegroundColor Cyan
if (Get-Command cloudflared -ErrorAction SilentlyContinue) {
  foreach ($hostName in $hosts) {
    try {
      cloudflared tunnel route dns $tunnelId $hostName 2>&1 | ForEach-Object { Write-Host "   $_" }
    } catch {
      Write-Host "   Aviso: nao foi possivel criar rota DNS para $hostName" -ForegroundColor Yellow
    }
  }
} else {
  Write-Host '   cloudflared nao encontrado no PATH.' -ForegroundColor Yellow
  Write-Host '   Crie manualmente no Zero Trust > Networks > Tunnels > Public Hostname' -ForegroundColor Yellow
}

Write-Host ''
Write-Host '3/3 Atualize o ingress do tunnel (servidor ou config local)' -ForegroundColor Cyan
Write-Host '   Arquivo modelo: scripts/deploy/cloudflared-ingress-mira.yml' -ForegroundColor DarkGray
Write-Host '   Destino:       http://127.0.0.1:8083' -ForegroundColor DarkGray
Write-Host ''
Write-Host 'URLs publicas (apos DNS + ingress):' -ForegroundColor Green
foreach ($hostName in $hosts) {
  Write-Host "   https://$hostName"
}
Write-Host ''
Write-Host 'Lembrete: defina no .env' -ForegroundColor Cyan
Write-Host '   PUBLIC_BASE_URL=https://search.aerosuite.app' -ForegroundColor DarkGray
Write-Host "   CORS_ALLOWED_ORIGINS=http://localhost:4201,https://search.aerosuite.app" -ForegroundColor DarkGray
