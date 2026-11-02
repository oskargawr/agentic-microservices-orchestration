# TH-400 S2 proposal — F2 Shipping fault injection

**Status:** proposal only — **not approved** · **no D-AUTH**  
**Date:** 2026-11-02  
**Depends on:** D-404 / OQ-F2-1 (Owner-ratified) · S1 on `main` (`c732558`)  
**Normative:** `docs/architecture/TH_400_FAULT_PROFILE_SPEC.md` §4

---

## 1. Exact scope

Implement **F2** only: deterministic **business** reject on Shipping `POST /api/v1/shipments/arrange` **before** shipment insert, armed via Runner FaultController profile **`F2`**, header `X-Thesis-Fault` (existing name; value / mapping per S0 — fault code **`ARRANGE_REJECTED`** as ErrorCode + HTTP semantics).

Preserve OQ-F2-1 **verbatim**:

| Rule | Binding |
| --- | --- |
| ErrorCode | `ARRANGE_REJECTED` (new `ErrorCode` member) |
| HTTP | **409 CONFLICT** |
| Retryability | Non-retryable **BUSINESS** (4xx path) |
| Same key + same fingerprint | Replay **exact failure** (incl. after disarm) |
| Same key + different fingerprint | `IDEMPOTENCY_KEY_CONFLICT` |
| Shipment row | **Never** created for rejected attempt |

**Out of S2:** F3 profiles · Variant C · Oracle classifier changes · protocol / rev. 3.1 · S1 redesign · Toxiproxy.

---

## 2. Affected files / modules (expected)

| Area | Likely paths |
| --- | --- |
| Contracts | `contracts/.../ErrorCode.java` (+ tests/OpenAPI if present) |
| Shipping service | `ShippingService.arrange`, persistence idempotency **failure** replay, fault parse gated by `thesis.shipping.fault-injection-enabled` (or agreed flag), controller header read |
| Runner | `F2FaultController` / Composite route; CLI `--fault-profile=F2`; verify/disarm; effectiveConfig |
| Adapter | `VariantAAdapter` → arrange/shipping fault hint on `OrderFulfillmentInput` **or** equivalent existing hook — **only if** already available; otherwise smallest Owner-approved wiring (may need activity/input field — **flag if expands orchestration**) |
| Tests | Shipping unit/IT (reject, idempotent replay, disarm-then-replay, fingerprint conflict, happy path unchanged); runner arm/verify/isolation; existing `t201_2` arrange-fail compensation IT as regression anchor |
| Docs | runner README · coordination handoff/state |

**Source fact today:** shipping idempotency replays **success** bodies only (`ShippingService`); S2 must extend failure replay without changing happy path (`TH_400_S0_TECHNICAL_VERIFICATION` §C).

---

## 3. Fault injection mechanism

- Header: **`X-Thesis-Fault`** (same string as payment).  
- Enable flag: per-service default **false** (lab/test **true**) — mirror payment pattern; **do not** invent a second public business API.  
- Arming: FaultController profile `F2` → `effectiveConfig` with `faultProfileId`, `trialId`, `orderId`, `service=shipping`, `header`, `code=ARRANGE_REJECTED` (or S0-specified fault token that maps to that ErrorCode).  
- Adapter passes hint only on **arrange** for armed F2 trial; F0/F1-P unaffected.  
- Reject **before** `shipments` insert; single TX: no row + failure idempotency + operation_log.

---

## 4. Idempotency and replay

Normative §4.3–4.4:

1. Atomic TX on reject.  
2. Persist failure outcome (`http_status=409`, `ApiError.code=ARRANGE_REJECTED`).  
3. Same key+fp → replay failure forever (including after disarm).  
4. Same key+diff fp → `IDEMPOTENCY_KEY_CONFLICT`.  
5. Crash-before-commit: no shipment; safe to reject again under armed F2.  
6. Crash-after-commit: durable failure replay prevents late create.

---

## 5. Test plan

| Layer | Cases |
| --- | --- |
| Unit | Fault parse; arrange reject path; idempotency failure insert/replay; fingerprint conflict |
| Shipping IT | Armed reject → 409 + no row; replay after disarm; happy path regression |
| Orchestration IT | Real header → compensate refund+release (`t201_2`-style); no shipment |
| Runner | Arm/verify/disarm F2; isolation vs F0/F1-P; effectiveConfig persistence |
| Optional live E2E | Lab only — not mandatory gate unless Owner expands D-AUTH |

---

## 6. Acceptance criteria (draft for D-AUTH)

1. `ARRANGE_REJECTED` in shared `ErrorCode`; arrange+F2 → HTTP 409 BUSINESS.  
2. No `shipments` row for rejected orderId/attempt.  
3. Idempotent failure replay + fingerprint conflict per OQ-F2-1.  
4. Happy-path arrange unchanged.  
5. Runner profile `F2` arms/verifies/disarms; F0/F1-P regressions green.  
6. Variant A compensation: CAPTURED→REFUNDED, HELD→RELEASED, no shipment.  
7. Oracle for clean cell: classification **`CONFIRMED_FAILURE`** (pattern per existing classifier — do not change classifier unless separately authorized).  
8. README + coordination updated; S0 normative text not rewritten.

---

## 7. Risks and dependencies

| Risk | Note |
| --- | --- |
| Idempotency schema | May need status/body shape for failure rows — design carefully vs existing success replay |
| Adapter/input field | If arrange hint missing on workflow input, orchestration change may need explicit Owner allow in D-AUTH |
| Injection enable verify | Prefer fail-closed probe analogous to S1 **or** document+Owner decision if no trustworthy probe without new API |
| Serial env | Keep D-R7 serial trials + env lock |
| Scope creep | F3 opacity / new headers / classifier edits → STOP |

**Dependencies:** S1 on main (done); shipping service exists; Variant A compensation for arrange-fail exists (`t201_2`).

---

## 8. Estimated story points

**3 SP** (aligns with Taiga #35 historical 3 SP for TH-400; S2 is one profile slice — Owner may re-point).

---

## Authorization

**D-AUTH-S2 granted** (Owner „zatwierdzam”, 2026-11-02). Implementation on `feature/th-400-s2-f2` (local). Push/PR still needs separate Owner auth.

---

## Feasibility review (2026-11-02)

Owner accepted proposal **direction** for investigation; **D-AUTH-S2 still ungranted**.

Full READ-ONLY review (current `main` @ `c732558`):  
→ **`coordination/TH_400_S2_FEASIBILITY_REVIEW.md`**

Key finding: **no existing arrange fault-hint path** — minimal S2 requires orchestration changes (`OrderFulfillmentInput.arrangeFaultHint`, activity/HTTP/workflow **including recovery**), not runner-only. Idempotency can extend via existing `http_status` without schema migration if failure bodies are stored and replayed **before** re-checking injection.
