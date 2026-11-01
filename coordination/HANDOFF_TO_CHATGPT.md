# HANDOFF_TO_CHATGPT

**Date:** 2026-11-01  
**Task:** TH-101 — Domain contracts and state models  
**Prepared by:** Cursor Agent  
**MODE:** IMPLEMENTATION  
**Branch:** `feature/th-101-domain-contracts`

---

## What was implemented

Shared contract surface for Inventory / Payment / Shipping aligned to TH-011 and protocol-v0:

- Java module `:contracts` — enums, request/response records, snapshots, `Money`, `IdempotencyKeys`, transition guards, `CompensationOrder`
- OpenAPI 3: `docs/contracts/openapi.yaml`
- State diagrams + assumptions: `docs/contracts/STATE_MODELS.md`
- Focused unit tests for valid/invalid transitions and DTO validation
- Services declare `implementation(project(":contracts"))` (no domain HTTP yet)
- Taiga story TH-101 → **Done**

**Explicitly not implemented:** Temporal workflows, LLM, experiment runner, DB migrations/tables, full service logic, e2e slice.

---

## Files created / modified (high level)

| Path | Role |
| --- | --- |
| `contracts/**` | DTOs, transitions, tests |
| `docs/contracts/openapi.yaml` | HTTP contract |
| `docs/contracts/STATE_MODELS.md` | Mermaid + assumptions |
| `docs/architecture/OVERVIEW.md` | Points at contracts |
| `settings.gradle.kts` / service `build.gradle.kts` | Module wiring |
| `README.md` | Status + layout |
| `coordination/*` | State, next task TH-102 proposed, D-014 |

---

## Contract / state overview

| Entity | States | Key ops |
| --- | --- | --- |
| Reservation | HELD, RELEASED, CONSUMED | reserve, release, **consume** |
| Payment | PENDING, CAPTURED, FAILED, UNKNOWN, REFUNDED | charge, refund (CAPTURED only), resolve |
| Shipment | NONE, ARRANGED, CANCELLED | arrange, cancel |
| Oracle labels | CONFIRMED_SUCCESS / FAILURE / AMBIGUOUS / INVALID | `BusinessOutcome` enum (doc alignment) |

Compensation order: cancel → refund → release.

Semantics enforced in guards: HELD→CONSUMED success path; timeout→UNKNOWN ≠ FAILED; no refund without CAPTURED.

---

## Tests and build

```text
./gradlew :contracts:test build
→ BUILD SUCCESSFUL
```

---

## Acceptance criteria verification

| Criterion | Status |
| --- | --- |
| OpenAPI/DTO + state diagram (Taiga/backlog AC) | **Met** — openapi.yaml + `:contracts` DTOs + STATE_MODELS.md |
| Consistent with TH-011/D-012 (no double decrement on consume) | **Met** — consume transition HELD→CONSUMED only; release forbidden on CONSUMED; docs state single decrement |
| No Temporal / LLM / experiment runner | **Met** |
| Inventory / payment / shipping contracts + idempotency | **Met** at contract level |
| Transition unit tests | **Met** |
| Persistence / HTTP impl | **Out of scope** → TH-102..104 |

---

## Deviations / unresolved for Owner

| ID | Topic | Cursor choice | Action |
| --- | --- | --- | --- |
| C-101-1 | Who calls `consume`? | Orchestration after arrange | Confirm or override |
| C-101-2 | Money | `amountMinor` + currency | Confirm |
| C-101-3 | Path prefix | `/api/v1` | Cosmetic |

No change to approved research MVP (A vs C).

---

## Commits / branch

| | |
| --- | --- |
| **Branch** | `feature/th-101-domain-contracts` |
| **HEAD** | `40cc40d` |
| **Commits** | `664efae` feat(contracts) · `5d683fc` docs(contracts) · `40cc40d` docs(coordination) |

**Do not push** until GitHub auth for `oskargawr` is verified (prior session blocker).

---

## Questions for ChatGPT

1. Prefer explicit inventory `consume` API (chosen) vs shipping-side consume coupling?
2. Any missing fields on DTOs before TH-102 (e.g. reason codes on failures)?
3. Should OpenAPI be split per service file for TH-102..104?

---

## Recommended next

**TH-102** — Inventory service with real schema tables, idempotent reserve/release/consume, unit tests. Rationale: unblocks payment/shipping and Temporal activities on a frozen contract.

Parallel optional: TH-021 / TH-022 literature.
