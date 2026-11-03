# 05 — Fault profiles

Canonical IDs only (`CanonicalFaultProfiles`). **`F1` and `F3` alone are not valid profile IDs.**

## Fault profile matrix

| Profile | Injected fault | Service / operation | Expected workflow behavior | Expected business end-state (typical) | Expected Oracle (`ExpectedCellOutcome`) | Acceptance rule |
| --- | --- | --- | --- | --- | --- | --- |
| **F0** | None (`mechanism=NONE`) | — | Happy path completes | Consumed reservation, CAPTURED payment, ARRANGED shipment | `CONFIRMED_SUCCESS` | Harness COMPLETED + cell match |
| **F1-P** | Header `X-Thesis-Fault: CHARGE_FAIL` | Payment **charge** (after inventory HELD) | Charge fails → compensate (release reservation); no successful capture path | No CAPTURED payment; reservation released | `CONFIRMED_FAILURE` | COMPLETED + cell match |
| **F2** | Header `X-Thesis-Fault: ARRANGE_REJECTED` | Shipping **arrange** (after payment CAPTURED) | Arrange rejects → compensate refund + release | Payment refunded; reservation released; no ARRANGED | `CONFIRMED_FAILURE` | COMPLETED + cell match |
| **F3-Recovered** | Charge `F3_CAPTURE_LOST_RESPONSE`; resolve/GET **without** transport opacity | Payment charge + resolve | Activity sees UNKNOWN then resolve → CAPTURED; continues to success | Same success package as F0 | `CONFIRMED_SUCCESS` | COMPLETED + cell match |
| **F3-Unresolved** | Same F3 charge **+** `RESOLVE_TRANSPORT_OPAQUE` on resolve/GET | Payment charge + resolve/GET | Bounded resolve fails to clear UNKNOWN/PENDING; no arrange; ambiguous path | Payment unresolved (UNKNOWN/PENDING); no success package | `AMBIGUOUS` | COMPLETED + **F3-Unresolved acceptance gate** (`requireF3UnresolvedGate=true`) |

Sources: `F*FaultController.java`, `ExpectedCellOutcome.forProfile()`, `OrderFulfillmentWorkflowImpl`, payment fault header handling in `:services:payment`.

## Why not `F1` / `F3`

- **F1-P** names the **Payment** charge-fail profile explicitly (D-R5); bare `F1` is ambiguous.  
- **F3** splits into **Recovered** vs **Unresolved** because Oracle expectations differ (`SUCCESS` vs `AMBIGUOUS`) and Unresolved needs an extra gate.

`CanonicalFaultProfiles.requireCanonical()` throws if `F1` or `F3` is passed.

## Injection lifecycle

1. `FaultController.arm(FaultArmRequest)` — records armed config (and enables header path for the trial).  
2. `verify(armed, TrialSpec)` — **fail-closed** HTTP confirmation that the live service honors the fault (`PaymentFaultInjectionVerifier` / `ShippingFaultInjectionVerifier` / F3 verifier).  
3. `VariantAAdapter` passes fault hints into workflow input so activities attach headers.  
4. `disarm` in `finally` of `TrialRunner.run`.

## OraclePattern vs profile IDs

`OraclePattern` enum (`SUCCESS`, `F0`, `F1`, `F2`, `NONE`) is an **explanatory pattern** inside the classifier (TH-011), **not** the harness fault-profile catalogue. Do not confuse `OraclePattern.F1` with profile id `F1-P`.

## Sequence diagrams

See [04-runtime-lifecycle.md](04-runtime-lifecycle.md).
