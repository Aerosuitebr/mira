# Configura MIRA no Cloudflare Tunnel (Zero Trust API ou instrucoes manuais).
# Uso com API:
#   $env:CLOUDFLARE_API_TOKEN = "token com Account.Cloudflare Tunnel Edit + Zone.DNS Edit"
#   .\scripts\deploy\configure-mira-zero-trust.ps1

$ErrorActionPreference = 'Stop'

$accountId = '4591ec7b63032dc157df648991469050'
$zoneIdComBr = '44a7c31ca337648abef38dea0c599e79'
# Tunnel ativo no servico Windows (cloudflared --token)
$tunnelId = '368bb8f3-2ae0-44ca-8b6f-28c2ef0f344c'
$tunnelCname = "$tunnelId.cfargotunnel.com"
$serviceUrl = 'http://127.0.0.1:8083'

$hostnames = @(
  'search.aerosuite.com.br'
)

Write-Host '=== MIRA - Cloudflare Tunnel ===' -ForegroundColor Cyan
Write-Host "Tunnel ativo (servico Windows): $tunnelId" -ForegroundColor DarkGray
Write-Host "Destino local: $serviceUrl" -ForegroundColor DarkGray
Write-Host ''
Write-Host 'NOTA: aerosuite.app usa DNS na CrazyDomains (nao Cloudflare).' -ForegroundColor Yellow
Write-Host '      Use search.aerosuite.com.br ou migre aerosuite.app para Cloudflare.' -ForegroundColor Yellow
Write-Host ''

$token = $env:CLOUDFLARE_API_TOKEN
if (-not $token) {
  Write-Host 'CLOUDFLARE_API_TOKEN nao definido - modo manual.' -ForegroundColor Yellow
  Write-Host ''
  Write-Host '1) Abra Zero Trust > Networks > Connectors > clique no tunnel ativo (Healthy)' -ForegroundColor Cyan
  Write-Host "   https://one.dash.cloudflare.com/$accountId/networks/connectors" -ForegroundColor DarkGray
  Write-Host '2) Aba Public Hostname (ou Routes) > Add a public hostname' -ForegroundColor Cyan
  foreach ($hostName in $hostnames) {
    Write-Host "   - Hostname: $hostName" -ForegroundColor Green
    Write-Host "     Service:  HTTP -> 127.0.0.1:8083" -ForegroundColor Green
  }
  Write-Host ''
  Write-Host '3) Autorize o cloudflared CLI (para DNS automatico via CLI):' -ForegroundColor Cyan
  Write-Host '   cloudflared tunnel login' -ForegroundColor DarkGray
  Write-Host '   (clique Authorize na pagina argotunnel que abrir)' -ForegroundColor DarkGray
  Write-Host ''
  Write-Host '4) Confirme MIRA local:' -ForegroundColor Cyan
  Write-Host '   curl http://localhost:8083' -ForegroundColor DarkGray
  exit 0
}

$headers = @{
  Authorization = "Bearer $token"
  'Content-Type' = 'application/json'
}

Write-Host 'Buscando configuracao atual do tunnel...' -ForegroundColor Cyan
$getUrl = "https://api.cloudflare.com/client/v4/accounts/$accountId/cfd_tunnel/$tunnelId/configurations"
$current = Invoke-RestMethod -Uri $getUrl -Headers $headers -Method Get
if (-not $current.success) {
  Write-Host "Erro ao ler tunnel: $($current.errors | ConvertTo-Json -Compress)" -ForegroundColor Red
  exit 1
}

$ingress = @($current.result.config.ingress)
$catchAll = $ingress | Where-Object { $_.service -match 'http_status:404' } | Select-Object -First 1
$ingress = @($ingress | Where-Object { $_.service -notmatch 'http_status:404' })

foreach ($hostName in $hostnames) {
  $exists = $ingress | Where-Object { $_.hostname -eq $hostName }
  if ($exists) {
    Write-Host "   Ingress ja existe: $hostName" -ForegroundColor DarkGray
    continue
  }
  $ingress += [ordered]@{
    hostname = $hostName
    service = $serviceUrl
    originRequest = @{
      noHappyEyeballs = $true
      keepAliveConnections = 100
      keepAliveTimeout = '90s'
      httpHostHeader = $hostName
    }
  }
  Write-Host "   + Ingress: $hostName -> $serviceUrl" -ForegroundColor Green
}

if ($catchAll) { $ingress += $catchAll }

$body = @{ config = @{ ingress = $ingress } } | ConvertTo-Json -Depth 8
$put = Invoke-RestMethod -Uri $getUrl -Headers $headers -Method Put -Body $body
if (-not $put.success) {
  Write-Host "Erro ao atualizar ingress: $($put.errors | ConvertTo-Json -Compress)" -ForegroundColor Red
  exit 1
}
Write-Host 'Ingress atualizado.' -ForegroundColor Green

foreach ($hostName in $hostnames) {
  $dnsName = $hostName
  $listUrl = "https://api.cloudflare.com/client/v4/zones/$zoneIdComBr/dns_records?type=CNAME&name=$dnsName"
  $list = Invoke-RestMethod -Uri $listUrl -Headers $headers -Method Get
  $existing = $list.result | Where-Object { $_.content -eq $tunnelCname }
  if ($existing) {
    Write-Host "   DNS OK: $dnsName" -ForegroundColor DarkGray
    continue
  }
  $createBody = @{
    type = 'CNAME'
    name = 'search'
    content = $tunnelCname
    proxied = $true
    ttl = 1
  } | ConvertTo-Json
  $create = Invoke-RestMethod -Uri "https://api.cloudflare.com/client/v4/zones/$zoneIdComBr/dns_records" -Headers $headers -Method Post -Body $createBody
  if (-not $create.success) {
    Write-Host "   Aviso DNS $dnsName : $($create.errors | ConvertTo-Json -Compress)" -ForegroundColor Yellow
  } else {
    Write-Host "   + DNS CNAME: $dnsName -> $tunnelCname" -ForegroundColor Green
  }
}

Write-Host ''
Write-Host 'Pronto. Teste em alguns segundos:' -ForegroundColor Green
Write-Host '  https://search.aerosuite.com.br' -ForegroundColor Cyan
