#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

stop_one() {
  local name="$1"
  local pidfile=".run/${name}.pid"
  if [[ -f "$pidfile" ]]; then
    local pid
    pid="$(cat "$pidfile")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      sleep 1
      kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null || true
      echo "stopped $name ($pid)"
    fi
    rm -f "$pidfile"
  fi
}

stop_one console
stop_one worker
stop_one shipping
stop_one payment
stop_one inventory

echo "Java processes stopped. Infra left running (docker compose)."
echo "To stop infra: (cd infra && docker compose down)"
