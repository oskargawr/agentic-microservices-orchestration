# HANDOFF_TO_CHATGPT

**Date:** 2026-11-01  
**Task:** TH-205 — Manual Testing Runtime & Experiment Console  
**Prepared by:** Cursor Agent  
**MODE:** IMPLEMENTATION  
**Branch:** `feature/th-205-experiment-console`

---

## Architecture / modules

| Module | Role |
| --- | --- |
| `:orchestration:variant-a` | + `runtime` (TemporalSettings/Clients/Workers) |
| `:orchestration:worker` | Independent worker Boot app (web none) |
| `:orchestration:console` | BFF on `127.0.0.1:8090` |
| `console-ui/` | Vite + React + TS on `127.0.0.1:3000` |

Docs: `docs/architecture/EXPERIMENT_CONSOLE.md`  
Scripts: `scripts/dev-up.sh`, `scripts/dev-down.sh`

## Temporal compatibility

- Server Compose `1.25.2` + Java SDK `1.40.0`
- Temporal docs: SDK and server version independently; all SDKs support all servers
- Verified by E2E: `GetSystemInfo` + completed workflow on real server

## BFF contracts

- `POST /api/console/runs` — required orderId, sku, qty, amountMinor, currency; 409 duplicate workflowId
- `GET /api/console/runs/{id}` — Temporal status, history steps, result/errors
- `GET /api/console/runs/{id}/business` — fresh service GETs + console consistency check (not formal oracle)

## Tests

| Suite | Result |
| --- | --- |
| `ConsoleRealTemporalE2EIT` | **1/1, skipped=0** (real Temporal + Postgres + HTTP services) |
| variant-a | 2/2, skipped=0 |
| inventory/payment/shipping | 11/13/7, skipped=0 |
| `./gradlew build` | SUCCESS |

## Example manual/E2E execution

```
workflowId=order-34fd5c47-07ef-448e-8d52-ae23b42408de
reservation=CONSUMED · payment=CAPTURED · shipment=ARRANGED
stock onHand=99 held=0
consistencyCheck.looksConsistentWithConfirmedSuccess=true
```

## Startup

```bash
cd infra && docker compose up -d
./scripts/dev-up.sh
cd console-ui && npm install && npm run dev
```

## Known limitations

- Amount `2599` only as explicit test input — not protocol-approved
- Console consistency check ≠ TH-401 oracle
- No fault UI / saga / Variant C
- Activity history types are PascalCase (`Reserve`…) per Java SDK
- CI may skip E2E if Temporal service absent (local verification used real Temporal; CI currently Postgres-only — consider adding Temporal later)

## Commits / PR

| **HEAD** | a6ffdd3fcb68195f78c0c7da763cfa11863f9c8c |
| **PR** | https://github.com/oskargawr/agentic-microservices-orchestration/pull/5 |

**Next:** TH-201 Saga compensations A (not started).
