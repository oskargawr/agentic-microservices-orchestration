# Experiment Console (TH-205)

Local-only manual runner for Variant A happy path against real Temporal + business services.

## Architecture

```text
console-ui (:3000) ──► console BFF (:8090)
                         ├─ Temporal client (start / describe / history / result)
                         └─ GET snapshots from inventory/payment/shipping
variant-a worker  ◄── poll ── Temporal (:7233), queue variant-a-order-fulfillment, namespace default
services          inventory :8081 · payment :8082 · shipping :8083
```

Modules:

| Module | Role |
| --- | --- |
| `:orchestration:variant-a` | Workflow + HTTP activities + `runtime` helpers |
| `:orchestration:worker` | Independent worker process |
| `:orchestration:console` | Spring Boot BFF |
| `console-ui/` | Vite + React + TypeScript |

## Temporal compatibility

- Compose server: `temporalio/auto-setup:1.25.2`
- Java SDK: `1.40.0`
- Temporal documents independent SDK/server releases; all SDK versions support all server versions.
- Verified in TH-205 E2E by polling the real server (`GetSystemInfo` + workflow completion).

## Amount field

`amountMinor` / `currency` are **required** request fields. The value `2599` used in tests is a local test input only — **not** an approved protocol constant.

## Startup

```bash
# 1) Infra
cd infra && docker compose up -d

# 2) Services + worker + BFF
./scripts/dev-up.sh

# 3) UI
cd console-ui && npm install && npm run dev
```

Open http://127.0.0.1:3000

Shutdown Java processes: `./scripts/dev-down.sh`  
Infra: `cd infra && docker compose down`

## Manual order

1. Enter orderId, SKU (`SKU-MVP-1`), qty, **explicit** amountMinor + currency.
2. Start Variant A.
3. Watch Temporal panel (status + four history steps).
4. Open Temporal UI link.
5. When completed, Business panel shows fresh GET snapshots + consistency check.

## BFF contracts

- `POST /api/console/runs` — start workflow; 409 on duplicate workflowId
- `GET /api/console/runs/{workflowId}` — Temporal status, steps, result/errors
- `GET /api/console/runs/{workflowId}/business` — service snapshots + console consistency check

BFF does not charge/arrange/consume itself — only starts the workflow and reads.
