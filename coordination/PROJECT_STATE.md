# PROJECT_STATE

**Last updated:** 2026-11-01  
**Updated by:** Cursor Agent (TH-200 Variant A happy path)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Mandatory** | A vs C (D-009) |
| **Protocol** | `protocol-v0` + F3 via Payment fault header (test-gated) |
| **Services** | Inventory / Payment / Shipping **Done** (merged `main`) |
| **Orchestration** | Variant A happy path **implemented** (PR pending) |
| **OPTIONAL** | B, MCP, RQ4 |

## Phase / milestone

- **Done:** TH-102, TH-103, **TH-104** (PR #3 → `main` @ `45849ab`)
- **In progress:** TH-200 Variant A Temporal happy path
- **Open:** OWN-1 *n*; OWN-2 model

## Engineering

| | |
| --- | --- |
| **Branch** | `feature/th-200-variant-a-happy-path` |
| **Base** | `main` @ `45849ab` |
| **Variant A tests** | workflow order 1/1; happy-path IT 1/1 (Postgres + bootJar processes), 0 skipped |
| **PR** | https://github.com/oskargawr/agentic-microservices-orchestration/pull/4 |

## TH-104 CI closure evidence (PR #3 run `41324579450`)

| Check | Evidence |
| --- | --- |
| Postgres started | `postgres service is healthy` + PostgreSQL 16.15 logs |
| Shipping tests ran | `:services:shipping:test` + HikariPool `[shipping]` (not `@EnabledIf` skip) |
| Payment tests ran | `:services:payment:test` + HikariPool `[payment]` |
| Build | `BUILD SUCCESSFUL` |
| Local re-verify | shipping 7/7, payment 13/13, 0 skipped |
| Merge | PR #3 **MERGED** (`45849ab`) |

## Next three actions

1. Merge TH-200 PR when CI green
2. Propose TH-201 Saga compensations A
3. Parallel lit. TH-021 / TH-022
