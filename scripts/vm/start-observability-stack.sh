#!/bin/bash
set -euo pipefail
log() { echo "[observability] $(date '+%F %T') $*"; }

for i in $(seq 1 60); do
  if docker info >/dev/null 2>&1; then break; fi
  sleep 2
done

cd /opt/factory-observability
log "compose up"
docker compose -f docker-compose.observability.yml up -d
log "done"
