# TH-201 — Variant A Saga Compensation (implementation plan)

| Field | Value |
| --- | --- |
| **Status** | **Done / Closed** (merged PR #6 @ `4814de6`; impl `f052768`) |
| **Coding** | Complete on `main` — see `TH-201_CLOSURE.md` |
| **Date** | 2026-11-01 (plan) · 2026-11-02 (Owner approve + merge) |
| **Depends** | TH-200, TH-205 (Done @ `1137fd1`) |

Related: `NEXT_TASK.md` (`approved`) · `HANDOFF_TO_CHATGPT.md` · `DECISION_LOG.md` **D-201 approved**

---

## Protocol / invariant preservation (non-negotiable)

| Rule | Label |
| --- | --- |
| TH-011 compensation order: cancel → refund → release | Source fact / Design decision |
| Refund **only** if payment `CAPTURED` | Source fact / Owner clarification |
| Never treat payment `UNKNOWN` as `FAILED` | Source fact |
| Never release `CONSUMED` | Source fact |
| Oracle labels / invariants I1–I7 **unchanged** | Source fact |
| `amountMinor=2599` is **not** a protocol constant (test input only) | Source fact |
| No new business invariants; no oracle redesign | Constraint |
| No Variant C, experiment runner; TH-206 stays Proposed | Constraint |
| Do not modify TH-205 | Constraint |

---

## Approved decisions (D-201)

### U-201-1 — Consume failure after arrange

Full compensation after consume failure, in reverse business-effect order:

1. Cancel shipment (if ARRANGED)
2. Refund confirmed CAPTURED payment
3. Release HELD reservation

### U-201-2 — Payment UNKNOWN

Bounded payment resolution attempts (`resolve`). If still UNKNOWN:

- Do **not** arrange shipment
- Do **not** refund
- Release HELD inventory reservation **if safe** (reservation still HELD; never CONSUMED)
- Business / structured outcome remains **AMBIGUOUS**
- Never interpret UNKNOWN as FAILED

### U-201-3 — Workflow structure

One deterministic Temporal workflow using Saga-style try/catch and **separate compensation activities**. Compensation must remain executable even if the main workflow cancellation scope is cancelled (Temporal cancellation-scope / disconnected compensation pattern as appropriate for Java SDK).

### U-201-4 — Fault testing scope

Service-level fault tests in TH-201 now (e.g. payment fault headers already supported). Full F0–F3 experiment profiles remain **TH-400**.

### U-201-5 — Structured business outcomes

| Outcome | Meaning |
| --- | --- |
| `SUCCESS` | Happy path completed under existing success package |
| `COMPENSATED_FAILURE` | **All required** compensation activities completed per preconditions; consistent failed/compensated shape |
| `AMBIGUOUS` | Unresolved payment UNKNOWN (or equivalent ambiguity); not claimed as compensated failure |

Unhandled technical errors or **failed / incomplete compensation** must **not** be reported as `COMPENSATED_FAILURE`.

---

## Owner clarifications (2026-11-02)

1. An unresolved **UNKNOWN** payment remains **AMBIGUOUS** even if the HELD inventory reservation is safely released.
2. Releasing inventory does **not** establish that payment was not captured.
3. Never refund unless **CAPTURED** is confirmed.
4. Never report **COMPENSATED_FAILURE** if any required compensation activity failed or remained unresolved.
5. Unhandled technical errors and incomplete compensation must remain **distinguishable** from business-level compensated failure.
6. Tests must verify **`operation_log` ordering** in addition to final database states.

---

## Current gap (Source fact)

- `OrderFulfillmentWorkflowImpl`: reserve → charge → arrange → consume only
- Activities: no cancel / refund / release / resolve wrappers yet
- Services + `IdempotencyKeys` + `CompensationOrder` already exist

---

## Work packages

1. Compensation (+ resolve) activity methods and HTTP clients
2. Saga try/catch workflow with cancellation-safe compensation path
3. Structured outcome type (`SUCCESS` / `COMPENSATED_FAILURE` / `AMBIGUOUS` + distinct incomplete/technical-error path)
4. Explicit tests listed below (entity state **and** `operation_log` order)
5. Short architecture note (no protocol edits)

---

## Explicit test cases (required in TH-201)

| ID | Scenario | Expected business shape (sketch) |
| --- | --- | --- |
| T-201-1 | Failure after reserve | Reservation `RELEASED`; payment not `CAPTURED`; shipment not `ARRANGED` → `COMPENSATED_FAILURE` (F1-like) |
| T-201-2 | Failure after confirmed capture, before arrange | Payment `REFUNDED`; reservation `RELEASED`; no live `ARRANGED` → `COMPENSATED_FAILURE` (F2-like) |
| T-201-3 | Failure after arrange, before consume | Shipment `CANCELLED`; payment `REFUNDED`; reservation `RELEASED` → `COMPENSATED_FAILURE` |
| T-201-4 | Payment UNKNOWN resolving to CAPTURED | After bounded resolve → may proceed on happy path if CAPTURED confirmed; else follow U-201-2 |
| T-201-5 | Payment UNKNOWN remaining unresolved | No arrange; no refund; HELD released if safe; outcome **`AMBIGUOUS`** (still AMBIGUOUS after safe release) |
| T-201-6 | Compensation activity failure / incomplete compensation | Must **not** report `COMPENSATED_FAILURE`; distinct incomplete/technical outcome |

**All of T-201-1…6:** assert final DB/API states **and** `operation_log` ordering of compensation (and forward) effects where applicable.

---

## Unresolved technical details (engineering — not blocking Owner policy)

| ID | Detail |
| --- | --- |
| X-201-1 | Exact Temporal Java API for “compensation runs despite workflow cancel” — verify against SDK 1.40 in impl |
| X-201-2 | Bound on resolve attempts / wait (N, timeout) — propose short defaults in impl PR |
| X-201-3 | Wire format of structured outcome — choose in impl without changing oracle |
| X-201-4 | Console (TH-205) display of new outcomes — out of TH-201 mandatory scope |

---

## Out of scope

- TH-206 (remains **Proposed** / unapproved)
- Variant C / runner / new invariants / oracle changes
- Making `amountMinor=2599` a protocol constant
- Modifying TH-205
