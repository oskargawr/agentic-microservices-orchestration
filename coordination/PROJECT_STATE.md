# PROJECT_STATE

**Last updated:** 2026-11-01  
**Updated by:** Cursor Agent (TH-104 Shipping implementation)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Mandatory** | A vs C (D-009) |
| **Protocol** | `protocol-v0` + F3 via Payment fault header (test-gated) |
| **Services** | Inventory **Done**; Payment **Done**; Shipping **implemented** (PR pending) |
| **OPTIONAL** | B, MCP, RQ4 |

## Phase / milestone

- **Done:** … TH-102, TH-103 (merged `main` @ `5bdf809`)
- **In progress:** TH-104 Shipping — branch `feature/th-104-shipping-service`
- **Open:** OWN-1 *n*; OWN-2 model

## Engineering

| | |
| --- | --- |
| **Branch** | `feature/th-104-shipping-service` |
| **Base** | `main` @ `5bdf809` (TH-103 PR #2 merged) |
| **Shipping tests** | 7/7 Postgres integration, 0 skipped |
| **Payment re-verify** | 13/13 Postgres (TH-103 acceptance checks) |
| **PR** | https://github.com/oskargawr/agentic-microservices-orchestration/pull/3 (open; merge pending) |

## TH-103 acceptance verification (2026-11-01)

| # | Check | Result |
| --- | --- | --- |
| 1 | Repeated charge after F3 cannot duplicate capture | **Pass** — unique `order_id` + idempotency replay; concurrent charge test |
| 2 | Refund only if persisted `CAPTURED` | **Pass** — `assertRefundAllowed` + `refundCapturedAndRefuseOtherwise` |
| 3 | Op log: order + persisted vs observed | **Pass** — F3 detail has `persistedState`/`observedResponse` |
| 4 | F3 independent of A/C | **Pass** — Payment HTTP header only; no orchestration coupling |
| 5 | Fault injection off by default | **Pass** — `fault-injection-enabled: false`; test profile enables |

## Next three actions

1. Merge TH-104 PR into `main`
2. Propose TH-200 Variant A (or lit. TH-021 / TH-022 in parallel)
3. Update Taiga TH-102–104 Done if board reachable
