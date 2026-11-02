# TH-400 — Fault profile specification (S0)

| Field | Value |
| --- | --- |
| **Status** | **S0 revised after Owner OQ decisions — awaiting final Owner ratification** (docs only; **not** implementation auth) |
| **Date** | 2026-11-02 |
| **Ticket** | TH-400 · Taiga **#35** |
| **Decision record** | **D-404** (see `coordination/DECISION_LOG.md`) |
| **Basis** | D-012 · D-016 · D-011 · D-201 · D-202 · D-R4 · D-R5 · D-403 · D-402 (unchanged) |
| **Does not amend** | `docs/protocol/EXPERIMENTAL_PROTOCOL_V0.md` constants · design contract rev. **3.1** wording · Oracle four-label **set** |
| **Verification** | `docs/architecture/TH_400_S0_TECHNICAL_VERIFICATION.md` |

> Labels: **Accepted Owner direction** · **Specified (S0)** · **Proposed (needs Owner ratification)** · **Open question (Owner)** · **Source fact**

---

## 0. Accepted Owner directions (2026-11-02)

| ID | Accepted direction | Status |
| --- | --- | --- |
| **OD-F2** | F2 = deterministic **business** failure during Shipping `arrange`, **before** shipment creation (no shipment row) | **Accepted Owner direction** |
| **OD-F3** | F3 splits into **F3-Recovered** and **F3-Unresolved** experimental profiles | **Accepted Owner direction** |
| **OD-FC** | TH-400 uses a **unified Runner FaultController** + **service-header** injection, identical for Variant A and future Variant C | **Accepted Owner direction** |

These directions do **not** by themselves authorize Java implementation (no D-AUTH for TH-400 coding yet).

---

## 1. Mechanism family (D-R4 + OD-FC)

### 1.1 Header and gates (**Specified**)

| Element | Spec |
| --- | --- |
| Header name | `X-Thesis-Fault` (same string as payment D-016 / `ThesisFault.HEADER`) |
| Enable flags | Per-service: `thesis.<service>.fault-injection-enabled` (default **false**; lab/test **true**) |
| When disabled | Ignore header; behave as production happy-path |
| Arming authority | Runner `FaultController` only for harness trials (variants must not invent private fault semantics) |
| Trial scope | Profile + effectiveConfig bound to `trialId` / `orderId`; new trial ⇒ new orderId and new keys |

### 1.2 FaultController lifecycle (**Specified**)

```text
baseline verified
  → arm(trial) → ArmedFaultRecord (+ effectiveConfig)
  → verify(armed)  // fail ⇒ do not start (FAULT_ARM_FAILED / FAULT_VERIFY_FAILED)
  → VariantAdapter.start  // A or future C; no variant-specific fault logic inside controller
  → … trial …
  → Oracle (DB-primary, unchanged)
  → finally: disarm(trial) + StateReset
```

| Rule | Spec |
| --- | --- |
| F0 | Genuine no-injection: `mechanism=NONE`; no headers set; verify asserts none |
| Verify-before-start | Mandatory; wrong/leftover profile ⇒ exclude trial |
| Disarm | Clears trial arming; failed disarm blocks next trial (`FAULT_DISARM_FAILED`) |
| Leak prevention | Prefer request-scoped headers; session may keep enable=true but **profile rules** are trial-scoped; post-trial reset clears business rows |
| Recording | Persist `ArmedFaultRecord.effectiveConfig` on every trial (including F0) |
| Variant logic | **Forbidden** inside FaultController (no Temporal/saga/LLM branches) |

### 1.3 A/C parity (**Specified**)

For a given `faultProfileId`, A and C receive the **same** `effectiveConfig` (header codes, attempt budgets, service targets). Variants differ only in orchestration; services and Oracle stay shared.

---

## 2. Profile catalogue and mapping

### 2.1 F0–F3 mapping table

| Profile ID | Protocol intent (v0) | MVP harness meaning | Injection point | Service | Primary fault code(s) | Mechanism |
| --- | --- | --- | --- | --- | --- | --- |
| **F0** | Happy path | Baseline, no injection | — | — | _(none)_ | `NONE` |
| **F1** / **F1-P** | Fail after reserve / before successful pay | **D-R5:** charge fails after **HELD** | `POST …/payments/charge` | payment | `CHARGE_FAIL` | header |
| **F1-I** | Inventory fail | **Out of MVP** unless separately approved | — | — | — | — |
| **F2** | Ship fail after pay | Arrange business-reject after **CAPTURED**, **no** shipment row | `POST …/shipments/arrange` | shipping | `ARRANGE_REJECTED` (**Proposed** code name) | header |
| **F3-Recovered** | Ambiguous payment then recover | D-016 charge ambiguity; resolve/GET expose CAPTURED; A continues | charge (+ normal resolve) | payment | `F3_CAPTURE_LOST_RESPONSE` | header |
| **F3-Unresolved** | Ambiguous payment left unresolved | D-016 charge + **bounded resolve/GET transport opacity**; DB stays CAPTURED | charge + resolve/GET HTTP | payment | `F3_CAPTURE_LOST_RESPONSE` + `RESOLVE_TRANSPORT_OPAQUE` (**Proposed**) | header |

Protocol-v0 table text is **not** rewritten; D-R5 / OD-F3 **specialize** MVP harness mapping (vault/protocol pointer style).

### 2.2 Exact fault codes and ownership

| Code | Owner service | Applies to | Persistence effect | Observed HTTP effect | Status |
| --- | --- | --- | --- | --- | --- |
| _(none)_ | — | F0 | — | — | Existing |
| `CHARGE_FAIL` | payment | F1-P | payment row **FAILED** | body `FAILED` (2xx charge path as today) | **Source fact** (exists) |
| `CHARGE_CAPTURE` | payment | default | CAPTURED | CAPTURED | Existing |
| `F3_CAPTURE_LOST_RESPONSE` | payment | F3-* | **CAPTURED** | charge body **UNKNOWN** (+ `paymentId`) | **D-016** |
| `RESOLVE_CAPTURE` / `RESOLVE_FAIL` / `RESOLVE_INCONCLUSIVE` | payment | probes | per D-016/D-015 | resolve body | Existing |
| `RESOLVE_TRANSPORT_OPAQUE` | payment | F3-Unresolved | **no DB write** | resolve and GET return **HTTP 503** while header present for the trial | **Owner-accepted mechanism (OQ-F3U-2)**; code name still new enum member at impl |
| `ARRANGE_REJECTED` | shipping | F2 | **no shipment row** | **HTTP 409** `ApiError.code=ARRANGE_REJECTED` | **Owner-selected (OQ-F2-1)** — add to `ErrorCode` at impl |

Inventory: **no** MVP fault codes under D-R5.

---

## 3. F1-P (**D-R5** preserved)

| Step | Expected |
| --- | --- |
| 1 | Inventory `reserve` → **HELD** |
| 2 | Payment `charge` with `X-Thesis-Fault: CHARGE_FAIL` (injection enabled) → persisted **FAILED** |
| 3 | Variant A compensates: **release** HELD; **no refund** (not CAPTURED) |
| 4 | Shipping: no arrange |
| 5 | Oracle (typical): **CONFIRMED_FAILURE** (clean compensation) |

**Runner arming (**Specified**):** FaultController profile `F1-P` (alias `F1`) sets `effectiveConfig` so the variant adapter passes charge header `CHARGE_FAIL` (or equivalent shared outbound interceptor keyed by orderId). No inventory fault.

---

## 4. F2 — Shipping business failure (**OD-F2**)

### 4.1 Narrative (**Accepted + Specified**)

1. Payment already **CAPTURED**; reservation **HELD**.  
2. Shipping receives `arrange` with fault armed.  
3. **Before** `shipments` insert: deterministic business rejection.  
4. **No** shipment row for that `orderId` from the failed operation.  
5. Variant A compensates: **refund** CAPTURED + **release** HELD (D-201 order preserved).

### 4.2 Error code, HTTP, retryability (**Owner OQ-F2-1**)

| Item | Specification | Label |
| --- | --- | --- |
| **ErrorCode** | **`ARRANGE_REJECTED`** (new member of shared `ErrorCode` at implementation) | **Owner decision** |
| **Why not reuse** | Existing codes do not denote fault-injected arrange reject (`VALIDATION_ERROR`, `INVALID_STATE_TRANSITION`, `NOT_FOUND`) | **Source fact** |
| **HTTP status** | **409 CONFLICT** | **Owner decision** |
| **Retryability** | Non-retryable **BUSINESS** (`HttpOrderFulfillmentActivities`: 4xx → `BUSINESS`) | **Specified** |
| **Forbidden** | HTTP **503** / other 5xx for F2 | **Specified** |

### 4.3 Idempotency (**Owner OQ-F2-1** + atomicity)

| Rule | Spec |
| --- | --- |
| On F2 reject | **No** shipment insert. Persist idempotent **failure** outcome for `(Idempotency-Key, fingerprint)`. |
| Same key + same fingerprint | Replay **original failure** (after disarm included). |
| Same key + different fingerprint | **`IDEMPOTENCY_KEY_CONFLICT`** (existing `IdempotencyRules` / HTTP 409) — unchanged. |
| Happy path | Existing success replay semantics preserved. |

### 4.4 Atomicity of F2 reject (**Specified** — normative)

In **one database transaction** before the error response is returned:

1. Confirm **no** `shipments` row inserted for this attempt.  
2. Insert/commit the **idempotency failure** record (`http_status=409`, body = `ApiError` with `ARRANGE_REJECTED`).  
3. Append **operation_log** (`arrange`, `shipmentCreated=false`, fault code; null shipment id).  

| Failure mode | Required outcome |
| --- | --- |
| Crash before TX commit | No shipment row; client may retry; under still-armed F2, reject again and commit; **must never** create shipment for that key+fingerprint after a committed F2 reject |
| Crash after TX commit | Replay failure forever for that key+fingerprint |
| Disarm then retry same key+fp | Still replay failure (no late create) |

**Source fact:** today’s shipping store replays **success** bodies only — S2 must extend failure replay without changing happy-path behavior.

### 4.5 Operation log

Included in the atomic TX above (`"fault":"ARRANGE_REJECTED","shipmentCreated":false`).

### 4.6 Expected terminal DB + Oracle

| Entity | Expected state |
| --- | --- |
| payment | **REFUNDED** (was CAPTURED) |
| reservation | **RELEASED** (was HELD) |
| shipment | **no row** for orderId |
| Oracle | **CONFIRMED_FAILURE** (clean F2 compensation pattern) |

---

## 5. F3 — Two profiles (**OD-F3**)

### 5.1 Shared D-016 core (unchanged)

For both F3 profiles, charge uses **`F3_CAPTURE_LOST_RESPONSE`** when payment fault injection is enabled:

- Persist **CAPTURED**.  
- Charge response observes **UNKNOWN** (includes `paymentId`).  
- When resolve/GET **succeed** without transport opacity: expose **CAPTURED**.  
- Do **not** fake DB; do **not** convert UNKNOWN→FAILED; do **not** refund unless CAPTURED confirmed.

### 5.2 F3-Recovered (**Specified**)

| Item | Spec |
| --- | --- |
| Charge | `F3_CAPTURE_LOST_RESPONSE` |
| Resolve/GET | **No** transport opacity; normal D-016 exposure of CAPTURED |
| Variant A | Bounded resolve sees CAPTURED → arrange → consume → **SUCCESS** |
| Oracle | DB: CAPTURED + CONSUMED + ARRANGED → **CONFIRMED_SUCCESS** |
| Refund | Not used on success path |

### 5.3 F3-Unresolved — D-016 + transport opacity

**Constraint (**Source fact**):** After F3 charge, successful HTTP resolve/GET **must** expose CAPTURED (D-016). `RESOLVE_INCONCLUSIVE` cannot keep the caller on UNKNOWN once CAPTURED is stored.

**Approach (**Specified**):** trial-scoped **HTTP transport opacity** on resolve/GET — **no DB write**; Oracle JDBC still sees CAPTURED. Does **not** redefine successful GET/resolve.

| Item | Spec |
| --- | --- |
| Charge | `F3_CAPTURE_LOST_RESPONSE` |
| Opacity code | `RESOLVE_TRANSPORT_OPAQUE` on `POST …/resolve` and `GET …/payments/{id}` |
| HTTP | **503** (**Owner OQ-F3U-2**) — retryable at Temporal; **not** a business FAILED body |
| DB | Unchanged (CAPTURED) |
| After disarm / no header | Normal D-016 exposure |

#### 5.3.1 Retry budget — normative decision (**Specified**)

**Source facts (Variant A today):**

| Layer | Behavior |
| --- | --- |
| `resolveAttemptLimit` | Logical workflow **loop** count only (default **3** via `OrderFulfillmentInput.DEFAULT_RESOLVE_ATTEMPTS`) |
| Each `resolvePayment` activity | `ActivityPolicies.defaultOptions()`: **maxAttempts = 5**; 4xx → non-retryable `BUSINESS`; **5xx → retryable** `HTTP_503` |
| Nested HTTP client retries | **None** beyond Temporal activity retries (`HttpOrderFulfillmentActivities` single RestClient call per attempt) |
| `getPayment` | Called only if the resolve loop completes with `snap == null`; **currently has no fault-hint parameter** |

Therefore a naive opacity budget of `N = resolveAttemptLimit` HTTP calls is **insufficient**: one logical iteration can issue up to **5** HTTP resolve attempts. If opacity “runs out” mid-retries, a later attempt would expose CAPTURED and **silently become F3-Recovered**.

**Normative implementation decision (do not guess a silent multiplier):**

1. **Preferred:** Opacity is **header-presence scoped to the trial**, not a small global counter. While the trial’s activities send `X-Thesis-Fault: RESOLVE_TRANSPORT_OPAQUE`, every resolve/GET returns 503. Temporal retries re-send the same header → remain opaque for the **entire trial** without burn-through. Disarm / end of trial ⇒ adapter stops sending the header (no new production API).  
2. **If a server-side counter is used instead:** it MUST be keyed by `paymentId` (and/or `orderId`) and sized to cover **at least** `resolveAttemptLimit × forwardActivityMaxAttempts` resolve attempts **plus** any `getPayment` attempts that can observe payment state for that trial; record the formula in `effectiveConfig`.  
3. **Harness rule:** any payment-state HTTP read used in the unresolved path for this profile (**resolve and getPayment**) MUST carry the opacity header (impl may extend activity signatures). An un-hinted `getPayment` after failed resolves is **forbidden** for this cell (would flip to Recovered).

#### 5.3.2 Variant A expected terminal behavior

Orchestrator never observes CAPTURED over HTTP during the armed window → in-memory payment remains **UNKNOWN** → **no refund** → **no arrange**.  
DB: **CAPTURED + HELD**, no shipment.

#### 5.3.3 Oracle label (**Owner OQ-F3U-1**)

For **this harness cell only**, Independent Oracle classifies the terminal DB state as **`AMBIGUOUS`**.

**Rationale (cell-specific, not a new global rule):**

- The experiment **injected** caller-visible payment ambiguity (D-016 charge) and **deliberately** blocked HTTP confirmation for the trial.  
- The orchestrator did **not** confirm capture over its resolve/GET path and did **not** refund.  
- Protocol OQ-2 / RQ2 treat unresolved payment observation as **AMBIGUOUS**, not success and not clean confirmed failure.  
- JDBC still shows truthful CAPTURED (Oracle remains DB-primary and independent of the injected 503 responses).

**Non-generalization (**Specified**):** This does **not** amend I6 or invent a standing rule that every dangling `CAPTURED+HELD` in production bugs is AMBIGUOUS. Unrelated INVALID/CONFIRMED_FAILURE semantics elsewhere stay unchanged. The four Oracle labels are **not** extended.

**D-016:** Intact — successful resolve/GET still expose CAPTURED when opacity is absent.

---

## 6. Expected state transitions (summary)

| Profile | Pay | Inv | Ship | Typical Oracle |
| --- | --- | --- | --- | --- |
| F0 | CAPTURED | CONSUMED | ARRANGED | CONFIRMED_SUCCESS |
| F1-P | FAILED | RELEASED | _(none)_ | CONFIRMED_FAILURE |
| F2 | REFUNDED | RELEASED | _(none)_ | CONFIRMED_FAILURE |
| F3-Recovered | CAPTURED | CONSUMED | ARRANGED | CONFIRMED_SUCCESS |
| F3-Unresolved | CAPTURED | HELD | _(none)_ | **AMBIGUOUS** (OQ-F3U-1, this cell only) |

Timeout alone never means FAILED. Refund only from confirmed CAPTURED.

---

## 7. Trial isolation & FaultController binding (**Specified**)

**Source facts:** `FaultArmRequest` carries `trialId`, `faultProfileId`, `orderId`, `variant`. `TrialSpec` has unique `orderId` per trial. Experiments are **serial** with env lock (D-R7). Current payment injection is **request header**-scoped when enabled.

| Risk | Mitigation |
| --- | --- |
| Cross-trial budget consumption | Prefer **header-presence** opacity (no shared counter). If counter used → key by `paymentId`/`orderId` only. |
| Leak into next trial | Disarm clears runner arm map; next trial new `orderId` / workflow input **without** opaque hints; verify-before-start. |
| Other paymentId affected | Opacity/charge faults apply only when the **armed trial’s** activities attach headers to **that** payment’s resolve/GET (path id) / charge for **that** `orderId`. |
| Disarm of trial A affecting B | Serial trials only in MVP; disarm is per `trialId` in FaultController. |

`effectiveConfig` MUST record at least: `faultProfileId`, `orderId`, `trialId`, fault code(s), opacity mode (`header-presence` or counter formula), service enable flags. Optional `paymentId` after charge for verify/audit.

**Harness correlation without new production API (**Specified**):** reuse `X-Thesis-Fault` values on the armed trial’s outbound calls only. **Do not** add a new public business API header/contract without separate Owner approval. Runner must not embed Variant-A-only saga logic in FaultController.

---

## 8. Test matrix (S0 plan — not implementing)

| Layer | F0 | F1-P | F2 | F3-Recovered | F3-Unresolved |
| --- | --- | --- | --- | --- | --- |
| **Unit** (fault parse, transitions) | verify NONE | CHARGE_FAIL mapping | `ARRANGE_REJECTED` + 4xx⇒BUSINESS | F3 observe vs persist | 503 opacity parse |
| **Service IT** | — | payment (exists) | no row; same-key failure replay; key+diff-fp → IDEMPOTENCY_KEY_CONFLICT | payment F3 (exists) | header opacity; DB CAPTURED; no-header exposes |
| **Orchestration IT** | happy path | `t201_1`-style | **real** shipping header | F3 → SUCCESS | F3+opacity → AMBIGUOUS; no refund; no un-hinted getPayment escape |
| **Runner IT** | arm/verify F0 | arm CHARGE_FAIL | arm F2 | arm F3-Recovered | arm F3-Unresolved; effectiveConfig; disarm isolation |

---

## 9. OQ status table

| ID | Topic | Status | Resolution |
| --- | --- | --- | --- |
| **OQ-F2-1** | F2 ErrorCode name | **Owner decided** | `ARRANGE_REJECTED`; HTTP 409; BUSINESS; idempotent failure replay + fingerprint conflict rules |
| **OQ-F3U-1** | F3-Unresolved Oracle label | **Owner decided** | **AMBIGUOUS** for this harness cell only; not a new global dangling-CAPTURED rule |
| **OQ-F3U-2** | Opacity transport | **Owner decided** | HTTP **503** (with §5.3.1 budget/isolation rules) |
| **OD-F2 / OD-F3 / OD-FC** | S0 directions | **Owner accepted** | See D-404 |
| **OQ-AUTH** | Implementation authorization | **Open** | Needs separate D-AUTH + `NEXT_TASK=approved` before S1–S4 code |
| **Impl detail** | Exact persistence shape for F2 failure idempotency rows | **Documented as normative behavior**; coding deferred | §4.4 |

---

## 10. Explicit non-goals (TH-400 S0 / later impl)

- No protocol constant changes (`T_observe`, `T_run_max`).  
- No change to the four Oracle **labels as a set**; only cell-specific expected outcome for F3-Unresolved.  
- No design contract rev. 3.1 rewrite.  
- No F1-I, Toxiproxy, Variant C implementation, TH-208 lost-ID-without-paymentId.  
- No marking Taiga #35/#37 Done from S0 alone.  
- No Java/service/test code in this documentation task.

---

## 11. S0 readiness for final Owner ratification

| Criterion | Met? |
| --- | --- |
| F2 semantics precise vs retry/idempotency/atomicity | **Yes** (OQ-F2-1 closed) |
| F3-Recovered fully specified | **Yes** |
| F3-Unresolved consistent with D-016 + Oracle AMBIGUOUS | **Yes** (OQ-F3U-1/2 closed; §5.3.1 normative) |
| A/C identical profiles | **Yes** |
| Protocol constants / Oracle label set / rev. 3.1 unchanged | **Yes** |
| No implementation code modified | **Yes** |
| Implementation authorized | **No** (OQ-AUTH open) |

**Verdict:** S0 documentation is **ready for final Owner ratification** of D-404 + this spec revision. It is **not** an authorization to implement.

---

## 12. Document control

| Artifact | Role |
| --- | --- |
| This file | Normative TH-400 fault profile S0 spec |
| `TH_400_S0_TECHNICAL_VERIFICATION.md` | Retry budget / isolation / F2 atomicity evidence |
| `coordination/DECISION_LOG.md` → D-404 | Directions + OQ resolutions |
| `coordination/TH_400_S0_PLAN.md` | Slice pointer |
| Implementation | Requires future D-AUTH (not granted here) |
