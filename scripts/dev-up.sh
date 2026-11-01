#!/usr/bin/env bash
# Local Experiment Console stack (TH-205). Does not start the Vite UI (run separately).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-}"
if [[ -z "${JAVA_HOME}" && -d "$HOME/Library/Java/JavaVirtualMachines/ms-21.0.7/Contents/Home" ]]; then
  export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/ms-21.0.7/Contents/Home"
fi

mkdir -p .run/logs

echo "==> Infra (Postgres + Temporal + UI)"
(cd infra && docker compose up -d)

echo "==> Build boot jars"
./gradlew :services:inventory:bootJar :services:payment:bootJar :services:shipping:bootJar \
  :orchestration:worker:bootJar :orchestration:console:bootJar --quiet

start_jar() {
  local name="$1"; shift
  local jar="$1"; shift
  local log=".run/logs/${name}.log"
  local pidfile=".run/${name}.pid"
  if [[ -f "$pidfile" ]] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    echo "    $name already running (pid $(cat "$pidfile"))"
    return
  fi
  nohup java -jar "$jar" "$@" >"$log" 2>&1 &
  echo $! >"$pidfile"
  echo "    started $name pid=$(cat "$pidfile") log=$log"
}

INV_JAR=$(ls services/inventory/build/libs/inventory-*-SNAPSHOT.jar | grep -v plain | head -1)
PAY_JAR=$(ls services/payment/build/libs/payment-*-SNAPSHOT.jar | grep -v plain | head -1)
SHIP_JAR=$(ls services/shipping/build/libs/shipping-*-SNAPSHOT.jar | grep -v plain | head -1)
WORKER_JAR=$(ls orchestration/worker/build/libs/worker-*-SNAPSHOT.jar | grep -v plain | head -1)
CONSOLE_JAR=$(ls orchestration/console/build/libs/console-*-SNAPSHOT.jar | grep -v plain | head -1)

echo "==> Business services"
start_jar inventory "$INV_JAR" --server.address=127.0.0.1 --server.port=8081
start_jar payment "$PAY_JAR" --server.address=127.0.0.1 --server.port=8082
start_jar shipping "$SHIP_JAR" --server.address=127.0.0.1 --server.port=8083

echo "==> Variant A worker"
start_jar worker "$WORKER_JAR"

echo "==> Console BFF"
start_jar console "$CONSOLE_JAR"

echo
echo "Stack starting. Health checks:"
echo "  Inventory  http://127.0.0.1:8081/actuator/health"
echo "  Payment    http://127.0.0.1:8082/actuator/health"
echo "  Shipping   http://127.0.0.1:8083/actuator/health"
echo "  Console    http://127.0.0.1:8090/api/console/runs (POST)"
echo "  Temporal UI http://localhost:8088"
echo
echo "Frontend (separate terminal):"
echo "  cd console-ui && npm install && npm run dev"
echo
echo "Shutdown: scripts/dev-down.sh"
