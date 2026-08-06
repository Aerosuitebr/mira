#!/bin/bash
# Poller de master no Vultr: se origin/master avancar e Actions estiver fora,
# faz o deploy sozinho (sem depender de runner GitHub-hosted).
#
# Instala via: scripts/deploy/install-mira-poller.sh (no servidor)
set -euo pipefail

REPO_URL="${MIRA_GIT_URL:-https://github.com/Aerosuitebr/mira.git}"
MIRROR="${MIRA_GIT_MIRROR:-/opt/mira-git}"
APP_DIR="${MIRA_APP_DIR:-/opt/mira}"
BRANCH="${MIRA_DEPLOY_BRANCH:-master}"
LOG_TAG="mira-poll-deploy"

log() { echo "[$LOG_TAG] $*"; }

mkdir -p "$(dirname "$MIRROR")"
if [[ ! -d "$MIRROR/.git" ]]; then
  log "clonando mirror $REPO_URL -> $MIRROR"
  git clone --depth 50 --branch "$BRANCH" "$REPO_URL" "$MIRROR"
fi

git -C "$MIRROR" remote set-url origin "$REPO_URL"
git -C "$MIRROR" fetch --depth 50 origin "$BRANCH"
git -C "$MIRROR" checkout -q -B "$BRANCH" "origin/$BRANCH"

NEW_SHA="$(git -C "$MIRROR" rev-parse "origin/$BRANCH")"
OLD_SHA=""
if [[ -f "$APP_DIR/.deployed-commit" ]]; then
  OLD_SHA="$(tr -d '[:space:]' <"$APP_DIR/.deployed-commit" || true)"
fi

if [[ "$NEW_SHA" == "$OLD_SHA" ]]; then
  log "ja em ${NEW_SHA:0:7}; nada a fazer"
  exit 0
fi

OLD_SHORT="${OLD_SHA:0:7}"
if [[ -z "$OLD_SHORT" ]]; then OLD_SHORT='none'; fi
log "novo commit ${NEW_SHA:0:7} (atual ${OLD_SHORT})"

# Empacota o mesmo conjunto do workflow deploy-production.yml
TMP_TGZ="/tmp/mira-release.tgz"
TMP_SHA="/tmp/mira-release.sha"
git -C "$MIRROR" archive --format=tar.gz -o "$TMP_TGZ" "$NEW_SHA" -- \
  backend/src/main/java \
  backend/src/main/resources \
  frontend/src \
  frontend/angular.json \
  frontend/nginx.conf \
  frontend/package.json \
  frontend/package-lock.json \
  services/outreach-bot \
  docker-compose.production.yml \
  docker-compose.tunnel.yml \
  docker-compose.yml \
  .env.production.example \
  scripts/deploy/nginx-mira.conf \
  scripts/deploy/prod-remote.sh \
  scripts/deploy/mira-poll-deploy.sh \
  scripts/deploy/install-mira-poller.sh

printf '%s\n' "$NEW_SHA" >"$TMP_SHA"

REMOTE_SCRIPT="$APP_DIR/scripts/deploy/prod-remote.sh"
if [[ ! -x "$REMOTE_SCRIPT" ]]; then
  # bootstrap: extrai so o script e roda
  tar -xzf "$TMP_TGZ" -C "$APP_DIR" scripts/deploy/prod-remote.sh 2>/dev/null || true
  chmod +x "$REMOTE_SCRIPT" 2>/dev/null || true
fi

if [[ -x "$REMOTE_SCRIPT" ]]; then
  bash "$REMOTE_SCRIPT"
else
  # fallback inline minimo se o script ainda nao existir no host
  bash -s <<'FALLBACK'
set -euo pipefail
cd /opt/mira
SHA="$(tr -d '[:space:]' </tmp/mira-release.sha)"
tar -xzf /tmp/mira-release.tgz -C /opt/mira
docker compose -f docker-compose.yml -f docker-compose.production.yml build mira-api mira-frontend mira-outreach-bot
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --no-deps mira-api mira-frontend mira-outreach-bot
docker compose -f docker-compose.tunnel.yml up -d --force-recreate mira-tunnel-proxy
echo "$SHA" > /opt/mira/.deployed-commit
echo "DEPLOYED ${SHA:0:7} (fallback)"
FALLBACK
fi
