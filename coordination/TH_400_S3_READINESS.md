# TH-400 S3 — F3 Readiness Investigation

**Mode:** READ-ONLY / DISCOVERY  
**Date:** 2026-11-03  
**Base:** `origin/main` @ `f3ac351` (S2 merge `b4ab3fa` is ancestor)  
**D-AUTH-S3:** **NOT granted** — no implementation in this session  

Labels: **Source fact** · **Proposed design** · **Open question**

---

## 1. Verified Git / sync status

| Item | Status |
| --- | --- |
| PR #13 | **MERGED** |
| Merge commit | `b4ab3fa966b6d749530b7ace7feef1d84f5d46c6` |
| S2 impl ancestor | `0905bac` ✓ |
| `origin/main` | `f3ac351` |
| Local ahead | `c16e1f0` EOD docs (not pushed) |
| Obsidian EOD sync | **Done** (Current Status / Thesis Home / Weekly Log / Taiga / #68 lesson) |
| Taiga #35 / #37 | **In progress** (S2 notes appended; ≠ Done) |
| Taiga #68 | **Exists** — New · 5 SP · Java cleanup audit |

---

## 2. F3 inventory — already implemented vs missing

### 2.1 Already in Payment (TH-103 / D-016) — Source fact

| Capability | Location / evidence |
| --- | --- |
| `ThesisFault.F3_CAPTURE_LOST_RESPONSE` | `services/payment/.../ThesisFault.java` |
| Charge: persist **CAPTURED**, observe **UNKNOWN** (+ paymentId) | `PaymentService.charge` L67–110 |
| Resolve/GET expose CAPTURED when opacity absent | `PaymentService.resolve` already-CAPTURED short-circuit; GET returns DB state |
| Resolve probes `RESOLVE_CAPTURE` / `FAIL` / `INCONCLUSIVE` | `mapResolveFault` |
| Flag `thesis.payment.fault-injection-enabled` | `application.yml` / `application-test.yml` |
| IT: F3 charge + no FAILED from timeout path | `PaymentIntegrationTest.f3CapturePersistedDespiteLostResponse…` |

### 2.2 Already in Variant A — Source fact

| Capability | Evidence |
| --- | --- |
| `chargeFaultHint` / `resolveFaultHint` on input | `OrderFulfillmentInput` |
| Charge / resolve pass `X-Thesis-Fault` | `HttpOrderFulfillmentActivities` |
| Bounded resolve loop → AMBIGUOUS; no arrange / no refund on UNKNOWN | `OrderFulfillmentWorkflowImpl.handleUnresolvedPayment` |
| Activity retries: maxAttempts=5; 5xx retryable; 4xx BUSINESS | `ActivityPolicies.defaultOptions()` |
| CompIT with CHARGE_UNKNOWN + resolve probes | `OrderFulfillmentCompensationIT` |

### 2.3 Already in Runner (S1/S2) — Source fact

| Capability | Evidence |
| --- | --- |
| FaultController arm / verify / disarm + Composite | F0, F1-P, F2 |
| Fail-closed payment preflight (CHARGE_FAIL→FAILED) | `HttpPaymentFaultInjectionVerifier` |
| Adapter maps F1-P → charge hint; F2 → arrange hint | `VariantAAdapter` |
| D-R7 serial lock + StateReset | `TrialRunner` / `ExperimentEnvLock` |
| `effectiveConfig` persistence on trial record | F1-P / F2 lifecycle tests |

### 2.4 Already in Oracle — Source fact

| Capability | Evidence |
| --- | --- |
| CAPTURED + HELD + no shipment → **AMBIGUOUS** (`COMPENSATION_INCOMPLETE`) | `ScenarioClassificationTest.s8_f3_dbCapturedHeld` |
| Four-label set unchanged | `OutcomeClassifier` |
| `evaluate(orderId, sku, workflowId, trialId)` — **no faultProfileId input** | `IndependentOracle` / `OracleEvaluationRequest` |

### 2.5 Missing for S3 — Source fact / Proposed

| Gap | Notes |
| --- | --- |
| `ThesisFault.RESOLVE_TRANSPORT_OPAQUE` | Spec’d; **not** in enum; no 503 opacity path on resolve/GET |
| Payment controller/service opacity branch | Must return **HTTP 503**, **no DB write**, while header present |
| `getPayment(…, faultHint)` | Today **no** fault param — forbidden escape hatch for F3-Unresolved (spec §5.3.1) |
| Workflow: store/pass resolve opacity hint on resolve **and** fallback getPayment | SagaState has charge hint; resolve hint from input; getPayment unhinted |
| Runner profiles `F3-Recovered` / `F3-Unresolved` | CLI / Composite / controllers absent |
| Adapter mappings for F3-* | charge=`F3_CAPTURE_LOST_RESPONSE`; Unresolved also resolve/get=`RESOLVE_TRANSPORT_OPAQUE` |
| F3 verify/preflight | Recovered: F3 charge → UNKNOWN + DB CAPTURED (or documented probe); Unresolved: also prove 503 opacity |
| Harness acceptance for Unresolved | Profile metadata + Oracle `AMBIGUOUS` (OQ-F3U-1) |
| Live Temporal E2E F3 cells | Not run |

---

## 3. Answers to investigation questions

1. **Payment F3?** Charge lost-response + normal resolve/GET exposure: **implemented**. Transport opacity: **missing**.  
2. **Runner missing?** Entire F3 FaultControllers, CLI profiles, adapter hints, verify/preflight, Composite wiring.  
3. **Config representation?** Canonical ids **`F3-Recovered`** and **`F3-Unresolved`** (OD-F3). `effectiveConfig`: `faultProfileId`, `trialId`, `orderId`, `mechanism=HEADER`, `header=X-Thesis-Fault`, `chargeCode=F3_CAPTURE_LOST_RESPONSE`, Unresolved adds `resolveCode=RESOLVE_TRANSPORT_OPAQUE`, `opacityMode=header-presence`, `service=payment`.  
4. **Trial-scoped headers?** Via workflow input hints → activities → `X-Thesis-Fault` (same channel as F1-P/F2). No shared mutable counter (OQ-F3U-2).  
5. **Opacity vs retry budget?** Header-presence for whole armed trial: Temporal retries re-send same hint → remain 503 for up to `resolveAttemptLimit × 5` HTTP resolves + hinted getPayment. Disarm/end trial stops sending header.  
6. **Other trials?** D-R7 serial + exclusive env lock; per-trial hints; no process-global opacity counter.  
7. **Arm / verify / disarm?** Mirror F1-P/F2: arm effectiveConfig → fail-closed verify (live Payment) → start → finally disarm. Disarm failure → `blockFurtherTrials`.  
8. **`effectiveConfig`?** See Q3; optional `paymentId` after charge for audit only.  
9. **Tests?** See §5.  
10. **Oracle?** DB path already yields **AMBIGUOUS** for CAPTURED+HELD (**Source fact** s8). **Open question:** whether Oracle API must *ingest* `faultProfileId` or harness acceptance alone satisfies OQ-F3U-1. **Proposed default for D-AUTH:** no classifier change; acceptance gate = profile id + `AMBIGUOUS` (+ DB CAPTURED/HELD/no arranged). Separate Owner decision if metadata must enter `OutcomeClassifier`.

---

## 4. Proposed S3 scope (logical tasks)

| # | Task | SP (suggest) |
| --- | --- | --- |
| S3-A | Payment: `RESOLVE_TRANSPORT_OPAQUE` + 503 on resolve/GET; IT opacity vs no-header expose | 2 |
| S3-B | Orchestration: `getPayment` faultHint; workflow forward opacity hint on resolve+getPayment; recovery consistency | 2 |
| S3-C | Runner: `F3RecoveredFaultController` + `F3UnresolvedFaultController` + Composite + CLI + adapter + README | 3 |
| S3-D | Tests: runner unit/lifecycle; CompIT Recovered→SUCCESS & Unresolved→AMBIGUOUS; F0/F1-P/F2 regression | 2 |
| S3-E | Optional live Temporal E2E lab (not mandatory for merge if IT strong) | 1 (optional) |

**Recommended slice estimate:** **8 SP** for A–D (or **5 SP** if Owner freezes Recovered-only first — **not** recommended: OD-F3 is dual-profile).  
**Proposed D-AUTH-S3:** both profiles in one auth (mirrors S0 dual OD-F3).

### Implementation sequence (Proposed)

1. Payment opacity enum + controller/service (no DB write) + IT.  
2. Activities/workflow getPayment hint + CompIT Unresolved (opacity) / Recovered (no opacity).  
3. Runner controllers + adapter + CLI + verify.  
4. Regression F0/F1-P/F2 + docs README.  
5. Handoff / Owner audit before push/PR.

---

## 5. Required tests & acceptance

| Layer | F3-Recovered | F3-Unresolved |
| --- | --- | --- |
| Payment IT | F3 charge UNKNOWN observe / CAPTURED DB (exists) | Header → 503; DB CAPTURED; without header → CAPTURED |
| Orchestration IT | F3 charge + resolve → SUCCESS package | Opacity on resolve+getPayment → workflow AMBIGUOUS; no refund; no arrange |
| Runner unit | arm/verify/disarm; effectiveConfig | same + opacity fields; fail-closed verify |
| Runner lifecycle | start with charge hint only | charge + resolve hints; verify fail blocks start |
| Oracle | CONFIRMED_SUCCESS when full success package | AMBIGUOUS on CAPTURED+HELD (existing) |
| Acceptance | classification SUCCESS (pattern SUCCESS) | classification AMBIGUOUS + profile F3-Unresolved |

Preserve: D-016, D-201 (no refund without CAPTURED), D-208, D-R4/R5/R7, four Oracle labels as a set.

---

## 6. Risks & open questions

| ID | Risk / question | Severity |
| --- | --- | --- |
| R1 | Unhinted `getPayment` flips Unresolved → Recovered | **HIGH** — must extend API |
| R2 | Shared opacity counter anti-pattern | Avoid; use header-presence |
| R3 | Oracle metadata input vs harness gate | **Open question** for D-AUTH wording |
| R4 | Preflight forge-URL / probe residue (same class as S1/S2) | MEDIUM limitation |
| R5 | Compensation path `getPayment` during Unresolved mid-flight | Ensure compensation uses DB truth after disarm or non-opaque path |
| R6 | Naming: CLI `F3-Recovered` / `F3-Unresolved` vs short aliases | Prefer full canonical ids only |

---

## 7. Files/modules likely to change (when authorized)

- `services/payment/.../ThesisFault`, `PaymentService`, `PaymentController`, IT, possibly exception handler for opacity 503  
- `orchestration/variant-a/.../OrderFulfillmentActivities`, `Http*`, `WorkflowImpl`, `RecordingActivities`, CompIT  
- `experiment/runner/.../fault/*F3*`, `CompositeFaultController`, `RunnerMain`, `VariantAAdapter`, README, tests  
- **Likely unchanged:** Oracle classifier (if harness-gate chosen), protocol-v0, Inventory, Shipping F2, Variant C  

---

## 8. Explicit non-goals (this discovery)

No Java F3 impl · no Oracle/protocol edits · no Variant C · no push/PR · no Taiga Done · no D-AUTH self-grant.

---

## 9. Owner ask

1. Review this readiness report.  
2. Grant **D-AUTH-S3** (recommended: both F3 profiles) + set `NEXT_TASK=approved` before coding.  
3. Decide OQ on Oracle metadata input (**R3**).  
4. Optional: push local `c16e1f0` EOD docs separately.  
