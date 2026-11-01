# PROJECT_STATE

**Last updated:** 2026-11-01  
**Updated by:** Cursor Agent (GitHub publish + workflow)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Mandatory** | A vs C (D-009) |
| **Protocol** | `protocol-v0` (D-012); timings provisional until pilot |
| **Inventory success** | `HELD → CONSUMED` (OWN-3 confirmed; no double decrement) |
| **DB** | PostgreSQL schema-per-service (D-013) |
| **OPTIONAL** | B, MCP, RQ4 |

## Phase / milestone

- **Phase:** Infra bootstrap complete → publish repo → domain contracts next
- **Done:** TH-010 … TH-012, **TH-100**, coordination migrated into Git repo
- **Next proposed:** TH-101 (API contracts + order state model)
- **Open:** OWN-1 confirmatory *n*; OWN-2 LLM model

## Engineering SoT

| | |
| --- | --- |
| **GitHub** | https://github.com/oskargawr/agentic-microservices-orchestration (public) |
| **Local clone** | `agentic-orchestration-thesis/` |
| **Coordination** | **this directory** (`coordination/`) — canonical |
| **Workflow** | [`docs/DEVELOPMENT_WORKFLOW.md`](../docs/DEVELOPMENT_WORKFLOW.md) |

## Completed (verified)

- Gradle multi-module stubs build green (`./gradlew build`)
- Compose: Postgres `:5433` + Temporal `:7233` + UI `:8088`
- Schemas `inventory`, `payment`, `shipping` in DB `business`
- Coordination / AGENTS / thesis Cursor rule live inside the app Git repo

## In progress

- **GitHub push blocked:** local `main` ready (`c2a67c6`); remote still empty until Owner re-auths as `oskargawr`

## Blockers

1. **Auth:** `gh auth login -h github.com -u oskargawr` then `git push -u origin main`
2. Owner approve TH-101 (or TH-021 parallel lit.)
3. OWN-1 / OWN-2 still open (not blocking TH-101)

## Next three actions

1. Verify remote `main` matches local HEAD
2. Owner: approve TH-101
3. TH-101 — OpenAPI/DTO + state diagram aligned to invariants

## Pointers

- [NEXT_TASK.md](NEXT_TASK.md) · [HANDOFF_TO_CHATGPT.md](HANDOFF_TO_CHATGPT.md) · [README.md](../README.md)
