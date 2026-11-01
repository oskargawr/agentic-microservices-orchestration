# PROJECT_STATE

**Last updated:** 2026-11-01  
**Updated by:** Cursor Agent (TH-101 complete)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Mandatory** | A vs C (D-009) |
| **Protocol** | `protocol-v0` (D-012); timings provisional until pilot |
| **Inventory success** | `HELD → CONSUMED` (OWN-3 confirmed; no double decrement) |
| **DB** | PostgreSQL schema-per-service (D-013) |
| **OPTIONAL** | B, MCP, RQ4 |

## Phase / milestone

- **Phase:** Shared contracts done → implement services
- **Done:** TH-010 … TH-012, TH-100, **TH-101**
- **Next proposed:** TH-102 (Inventory reserve/release/consume) — or TH-021 lit. in parallel
- **Open:** OWN-1 confirmatory *n*; OWN-2 LLM model; C-101-1..3 contract assumptions (Owner confirm)

## Engineering SoT

| | |
| --- | --- |
| **GitHub** | https://github.com/oskargawr/agentic-microservices-orchestration (public; push may still need `oskargawr` auth) |
| **Branch (this work)** | `feature/th-101-domain-contracts` |
| **Coordination** | this directory |
| **Contracts** | `:contracts` + `docs/contracts/` |

## Completed (verified)

- OpenAPI + Java DTOs/enums + transition guards + unit tests
- `./gradlew :contracts:test build` SUCCESS
- No Temporal / LLM / runner / persistence in TH-101

## In progress

- None (TH-101 ready for Owner review / merge)

## Blockers

1. GitHub push auth for `oskargawr` (if remote still empty)
2. Owner confirm C-101 assumptions (consume caller, money minor units) — non-blocking for TH-102 if accepted
3. OWN-1 / OWN-2 still open

## Next three actions

1. Owner: review TH-101 branch / merge when satisfied
2. Approve **TH-102** (Inventory service)
3. Parallel: TH-021 / TH-022

## Pointers

- [NEXT_TASK.md](NEXT_TASK.md) · [HANDOFF_TO_CHATGPT.md](HANDOFF_TO_CHATGPT.md) · [docs/contracts/STATE_MODELS.md](../docs/contracts/STATE_MODELS.md)
