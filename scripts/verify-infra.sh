#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/infra"

echo "== compose ps =="
docker compose ps

echo "== postgres schemas =="
docker compose exec -T postgres psql -U thesis -d business -c '\dn'

echo "== temporal port =="
curl -s -o /dev/null -w "temporal_ui_http=%{http_code}\n" http://127.0.0.1:8088/ || true

# Temporal frontend is gRPC; check container health via logs/port
if ! docker compose exec -T temporal tctl --address temporal:7233 cluster health 2>/dev/null; then
  # fallback: open port
  if nc -z 127.0.0.1 7233 2>/dev/null; then
    echo "temporal_grpc=port_open"
  else
    echo "temporal_grpc=FAIL" >&2
    exit 1
  fi
fi

echo "OK"
