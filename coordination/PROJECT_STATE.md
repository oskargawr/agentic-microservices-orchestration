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

## Explore gaps vs TH-200 (follow-up)

Pre-coding explore checklist largely **superseded** by implemented `:orchestration:variant-a` + PR #4:

| Gap | Resolution |
| --- | --- |
| G0 TH-104 merge | Done (`45849ab`) |
| G1 NEXT_TASK approve | Owner brief → TH-200 |
| G2 module layout | Chose `variant-a` (not platform-only) |
| G3 Temporal SDK | Pinned `1.40.0` (test env); Compose server still `1.25.2` |
| G4–G6 worker/tests | TestWorkflowEnvironment + service bootJars; no CI Temporal server yet |
| G7 amount | Assumption `Money.plnMinor(2599)` documented |
| G8 consume-after-arrange fail | Deferred to TH-201 |
| G9 oracle | Independent HTTP GET + stock SQL in IT (not Temporal COMPLETED alone) |

**Still open (non-blocking TH-200 merge):** align Compose Temporal server with Java SDK before a real worker against `:7233`; optional later move of shared activities into `platform` for Variant C surface parity; protocol-v0 mermaid still says “consume on arrange success” vs C-101-1 four-step wording.

## Next three actions

1. Merge TH-200 PR #4 (CI already green)
2. Propose TH-201 Saga compensations A
3. Parallel lit. TH-021 / TH-022
