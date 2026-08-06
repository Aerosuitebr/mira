#!/bin/bash
# Deploy de runtime no host Vultr (/opt/mira).
# Entrada: /tmp/mira-release.tgz + /tmp/mira-release.sha
# Usado por: GitHub Actions, deploy-production.ps1 e mira-poll-deploy.sh
set -euo pipefail

cd /opt/mira
SHA="$(tr -d '[:space:]' </tmp/mira-release.sha)"
SHORT="${SHA:0:7}"
LOCK="/opt/mira/.deploy.lock"

if [[ -z "$SHA" || "$SHORT" == "" ]]; then
  echo "SHA de release ausente em /tmp/mira-release.sha"
  exit 1
fi

exec 9>"$LOCK"
if ! flock -n 9; then
  echo "Outro deploy em andamento (lock $LOCK). Abortando."
  exit 0
fi

echo "=== deploying ${SHORT} ==="

touch .env.production
if ! grep -qE '^MIRA_SERVICE_TOKEN=.{24,}$' .env.production; then
  umask 077
  printf '\nMIRA_SERVICE_TOKEN=%s\n' "$(openssl rand -hex 32)" >> .env.production
fi

mkdir -p /opt/mira-backups
tar -czf "/opt/mira-backups/mira-pre-${SHORT}-$(date +%Y%m%d%H%M%S).tgz" \
  backend/src docker-compose.production.yml frontend/src scripts/deploy/nginx-mira.conf \
  2>/dev/null || true

tar -xzf /tmp/mira-release.tgz -C /opt/mira

# Garante que o proprio script de deploy remoto fique atualizado no servidor
if [[ -f /opt/mira/scripts/deploy/prod-remote.sh ]]; then
  chmod +x /opt/mira/scripts/deploy/prod-remote.sh /opt/mira/scripts/deploy/mira-poll-deploy.sh 2>/dev/null || true
fi

docker compose -f docker-compose.yml -f docker-compose.production.yml build mira-api mira-frontend mira-outreach-bot
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --no-deps mira-api mira-frontend mira-outreach-bot

# O nginx monta o arquivo de configuracao. Recriar o proxy garante inode novo.
docker compose -f docker-compose.tunnel.yml up -d --force-recreate mira-tunnel-proxy

webhook_code="000"
for _ in $(seq 1 15); do
  webhook_code="$(curl -s -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:8083/webhooks/outreach-bot -H 'Content-Type: application/json' -d '{}' || true)"
  [[ "$webhook_code" = "401" ]] && break
  sleep 1
done
echo "outreach_webhook_http=$webhook_code"
test "$webhook_code" = "401"

bot_health="$(docker exec mira-outreach-bot node -e 'fetch("http://127.0.0.1:8090/health").then(r => r.json()).then(x => process.stdout.write(x.status)).catch(() => process.exit(1))')"
echo "outreach_bot_health=$bot_health"
test "$bot_health" = "ok"

bot_status_http="$(docker exec mira-outreach-bot node -e 'fetch("http://127.0.0.1:8090/v1/status", {headers: {authorization: `Bearer ${process.env.BOT_SERVICE_TOKEN || process.env.MIRA_SERVICE_TOKEN || ""}`}}).then(r => process.stdout.write(String(r.status))).catch(() => process.exit(1))')"
echo "outreach_bot_status_http=$bot_status_http"
test "$bot_status_http" = "200"
docker inspect mira-outreach-bot --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -qx 'OUTREACH_DELIVERY_ENABLED=false'

if ! docker inspect mira-api --format '{{json .NetworkSettings.Networks}}' | grep -q aerosuite_default; then
  docker network connect aerosuite_default mira-api || true
fi

ready=0
api=000
front=000
for i in $(seq 1 120); do
  api="$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8082/actuator/health || true)"
  front="$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:4201/ || true)"
  if [[ "$api" = "200" && "$front" = "200" ]]; then
    echo "OK api+front after ${i}s"
    ready=1
    break
  fi
  sleep 2
done
if [[ "$ready" != "1" ]]; then
  echo "Services not healthy api=$api front=$front"
  docker logs mira-api --tail 80 || true
  docker logs mira-frontend --tail 40 || true
  exit 1
fi

echo "$SHA" > /opt/mira/.deployed-commit
docker exec mira-api printenv | grep -E 'APP_EVOLUTION_OUTBOUND|APP_OUTREACH_DISPATCH|APP_EVOLUTION_ENABLED|PUBLIC_BASE_URL' || true
docker ps --filter name=mira --format '{{.Names}} {{.Status}}'
echo "DEPLOYED ${SHORT}"
