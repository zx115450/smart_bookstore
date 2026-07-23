#!/bin/bash
set -euo pipefail
# 有序启动 Milvus，避免冷启动 node 元数据不一致导致 unhealthy
log() { echo "[milvus-stack] $(date '+%F %T') $*"; }

log "waiting for docker"
for i in $(seq 1 60); do
  if docker info >/dev/null 2>&1; then break; fi
  sleep 2
done
docker info >/dev/null 2>&1 || { log "docker not ready"; exit 1; }

log "start etcd + minio"
docker start milvus-etcd milvus-minio >/dev/null
sleep 8

log "start standalone"
docker start milvus-standalone >/dev/null

# 等待 healthy，最多约 2 分钟
for i in $(seq 1 24); do
  status=$(docker inspect -f '{{.State.Health.Status}}' milvus-standalone 2>/dev/null || echo starting)
  log "standalone health=$status"
  if [ "$status" = "healthy" ]; then
    if curl -sf -m 3 http://127.0.0.1:9091/healthz >/dev/null 2>&1; then
      log "milvus ready"
      exit 0
    fi
  fi
  if [ "$status" = "unhealthy" ] && [ "$i" -ge 6 ]; then
    log "unhealthy, restart standalone once"
    docker restart milvus-standalone >/dev/null || true
    sleep 15
  else
    sleep 5
  fi
done
log "timeout waiting healthy (containers may still be starting)"
exit 0
