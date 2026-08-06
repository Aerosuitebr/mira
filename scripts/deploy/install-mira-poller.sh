#!/bin/bash
# Instala/atualiza o poller systemd do MIRA no Vultr.
# Uso (no servidor): bash scripts/deploy/install-mira-poller.sh
set -euo pipefail

APP_DIR="${MIRA_APP_DIR:-/opt/mira}"
POLL_SCRIPT="$APP_DIR/scripts/deploy/mira-poll-deploy.sh"
UNIT_DIR=/etc/systemd/system

if [[ ! -f "$POLL_SCRIPT" ]]; then
  echo "Script ausente: $POLL_SCRIPT"
  exit 1
fi
chmod +x "$POLL_SCRIPT" "$APP_DIR/scripts/deploy/prod-remote.sh" 2>/dev/null || true

cat >"$UNIT_DIR/mira-poll-deploy.service" <<EOF
[Unit]
Description=MIRA poll master and deploy to /opt/mira
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
Environment=MIRA_APP_DIR=$APP_DIR
Environment=MIRA_GIT_MIRROR=/opt/mira-git
Environment=MIRA_DEPLOY_BRANCH=master
Environment=MIRA_GIT_URL=https://github.com/Aerosuitebr/mira.git
ExecStart=/bin/bash $POLL_SCRIPT
Nice=10
TimeoutStartSec=45min
EOF

cat >"$UNIT_DIR/mira-poll-deploy.timer" <<'EOF'
[Unit]
Description=MIRA deploy poller every 2 minutes

[Timer]
OnBootSec=2min
OnUnitActiveSec=2min
AccuracySec=30s
Persistent=true
Unit=mira-poll-deploy.service

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now mira-poll-deploy.timer
systemctl start mira-poll-deploy.service || true
systemctl status mira-poll-deploy.timer --no-pager || true
echo "Poller instalado. Logs: journalctl -u mira-poll-deploy.service -n 50"
