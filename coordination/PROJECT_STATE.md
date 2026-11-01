# PROJECT_STATE

**Last updated:** 2026-11-01  
**Updated by:** Cursor Agent (TH-102 Inventory complete)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Mandatory** | A vs C (D-009) |
| **Protocol** | `protocol-v0` (D-012) |
| **Contracts** | TH-101 + TH-101.1 (C-101-1..3) |
| **Services** | **Inventory implemented** (TH-102); Payment/Shipping pending |
| **OPTIONAL** | B, MCP, RQ4 |

## Phase / milestone

- **Phase:** Shared services — Inventory done → Payment next
- **Done:** TH-010 … TH-012, TH-100, TH-101, TH-101.1, **TH-102**
- **Next proposed:** TH-103 (Payment simulation)
- **Open:** OWN-1 *n*; OWN-2 model

## Engineering SoT

| | |
| --- | --- |
| **GitHub** | https://github.com/oskargawr/agentic-microservices-orchestration (public; `main` has contracts) |
| **Branch** | `feature/th-102-inventory-service` |
| **Inventory** | Flyway V1 + HTTP `/api/v1/*` + Postgres integration tests |

## Completed (verified)

- Inventory reserve/release/consume/get with real Postgres
- Idempotency + concurrency tests: 11/11 passed against `localhost:5433`
- `./gradlew build` SUCCESS

## In progress

- None

## Blockers

1. Owner approve TH-103 (or merge TH-102 PR)
2. OWN-1 / OWN-2 still open

## Next three actions

1. Merge TH-102 to `main`
2. Approve **TH-103** Payment
3. Parallel lit. TH-021 / TH-022

## Pointers

- [HANDOFF_TO_CHATGPT.md](HANDOFF_TO_CHATGPT.md) · [docs/architecture/INVENTORY_CONCURRENCY.md](../docs/architecture/INVENTORY_CONCURRENCY.md)
