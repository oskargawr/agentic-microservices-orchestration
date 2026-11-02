# TH-400 — Fault profile specification (S0)

| Field | Value |
| --- | --- |
| **Status** | **S0 design lock — awaiting Owner review** (documentation only; **not** implementation auth) |
| **Date** | 2026-11-02 |
| **Ticket** | TH-400 · Taiga **#35** |
| **Decision record** | Proposed **D-404** (directions accepted; see `coordination/DECISION_LOG.md`) |
| **Basis** | D-012 · D-016 · D-011 · D-201 · D-202 · D-R4 · D-R5 · D-403 · D-402 (unchanged) |
| **Does not amend** | `docs/protocol/EXPERIMENTAL_PROTOCOL_V0.md` constants · design contract rev. **3.1** wording · Oracle four labels |

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
| `RESOLVE_TRANSPORT_OPAQUE` | payment | F3-Unresolved | **no DB write** | resolve and/or GET return **5xx or lost response** for armed attempts only | **Proposed** (S0) |
| `ARRANGE_REJECTED` | shipping | F2 | **no shipment row** | **4xx** `ApiError` | **Proposed** code + mapping below |

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

### 4.2 Error code, HTTP, retryability

| Item | Specification | Label |
| --- | --- | --- |
| **ErrorCode** | Prefer **new** `ErrorCode.BUSINESS_REJECTED` **or** shipping-specific `ARRANGE_REJECTED` mapped in `ApiError.code` | **Proposed — Owner ratify enum name** |
| **Why not reuse** | Existing codes: `VALIDATION_ERROR` (input), `INVALID_STATE_TRANSITION` (duplicate arrange / transitions), `NOT_FOUND` — none denote intentional fault-injected arrange reject | **Source fact** |
| **HTTP status** | **409 CONFLICT** (4xx) | **Specified** (fits current shipping conflict style; **not** 503) |
| **Retryability** | Non-retryable **BUSINESS** at Temporal activity layer (`HttpOrderFulfillmentActivities`: 4xx → `BUSINESS`) | **Specified** |
| **Forbidden** | HTTP **503** / 5xx for F2 — would become retryable `HTTP_503` and risk arrange success after disarm | **Specified** |

### 4.3 Idempotency (**Specified** — critical)

| Rule | Spec |
| --- | --- |
| On F2 reject | **Do not** insert shipment. **Do** record an idempotent outcome for `(Idempotency-Key, fingerprint)` that **replays the same failure** on retries. |
| After disarm | Same key+fingerprint **must still** replay failure (no late create). New trials use new `orderId` / keys. |
| Rationale | Prevents “retry after disarm creates shipment” (**OD-F2** safety). |

**Open implementation note:** today’s shipping idempotency store replays success `ArrangeResponse` JSON only (**Source fact**). S2 must extend failure replay without changing happy-path semantics — detail left to implementation, behavior above is normative.

### 4.4 Operation log (**Specified**)

Append `operation_log` entry for the rejected arrange attempt, e.g. operation `arrange` with detail including `"fault":"ARRANGE_REJECTED","shipmentCreated":false` and **null** `shipment_id` (or equivalent), so Oracle/debug can see injection without a shipments row.

### 4.5 Expected terminal DB + Oracle

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

### 5.3 F3-Unresolved — consistency with D-016

**Problem (**Source fact**):** After F3 charge, DB is already CAPTURED. Payment `resolve`/`GET` **authoritatively** return CAPTURED when the HTTP call succeeds (D-016). `RESOLVE_INCONCLUSIVE` cannot keep the caller on UNKNOWN once CAPTURED is stored — resolve short-circuits as already-CAPTURED.

**Therefore:** F3-Unresolved **must not** redefine D-016 success-path GET/resolve semantics.

**Allowed approach (**Specified**):** add a **trial-scoped resolution transport opacity** fault that fails the **HTTP** resolve (and optionally GET) **without writing payment state**, while Oracle continues to read **JDBC** and sees CAPTURED.

| Item | Spec |
| --- | --- |
| Charge | `F3_CAPTURE_LOST_RESPONSE` (D-016 intact) |
| Additional code | `RESOLVE_TRANSPORT_OPAQUE` (**Proposed** name) on `POST …/resolve` and `GET …/payments/{id}` |
| Effect | Armed attempts: **no DB mutation**; HTTP **503** or equivalent **lost/incomplete response** (transport class — **not** a false FAILED body) |
| Attempt budget | Opaque for **all** of Variant A’s configured `resolveAttemptLimit` resolve attempts **plus** any `getPayment` calls used inside that unresolved handler for the same trial | **Specified** default |
| After budget / disarm | Subsequent resolve/GET obey normal D-016 (expose CAPTURED) |
| Variant A expected | Never observes CAPTURED via HTTP during the armed window → takes **AMBIGUOUS** path with in-memory payment still **UNKNOWN** → **skips refund** (`refund-skipped`) → **no arrange** |
| DB at terminal (A path) | payment **CAPTURED**, reservation **HELD**, no shipment |
| Authoritative contract | Unchanged when opacity not armed / after disarm |

#### Oracle label for F3-Unresolved — **Open question (Owner)**

DB-primary Oracle will see **CAPTURED + HELD** (dangling capture/hold). Invariants doc I6 ties clean `CONFIRMED_FAILURE` to no bare CAPTURED without refund. Caller-centric protocol text maps unresolved payment → **AMBIGUOUS**.

| Option | Meaning |
| --- | --- |
| **OQ-F3U-1a** | Label **AMBIGUOUS** for this harness cell (orchestrator never confirmed capture over HTTP; no refund issued) |
| **OQ-F3U-1b** | Label **INVALID** (strict I6 dangling CAPTURED+HELD) |

**S0 does not choose.** Until Owner picks, F3-Unresolved is **design-complete for injection** but **Oracle expectation blocked** on this narrow point.

**Conflict report:** Representable **without** contradicting D-016 **iff** opacity is transport-only and JDBC/Oracle remain truthful. **Blocked for full acceptance** until OQ-F3U-1 is decided. No silent D-016 rewrite.

---

## 6. Expected state transitions (summary)

| Profile | Pay | Inv | Ship | Typical Oracle |
| --- | --- | --- | --- | --- |
| F0 | CAPTURED | CONSUMED | ARRANGED | CONFIRMED_SUCCESS |
| F1-P | FAILED | RELEASED | _(none)_ | CONFIRMED_FAILURE |
| F2 | REFUNDED | RELEASED | _(none)_ | CONFIRMED_FAILURE |
| F3-Recovered | CAPTURED | CONSUMED | ARRANGED | CONFIRMED_SUCCESS |
| F3-Unresolved | CAPTURED | HELD | _(none)_ | **OQ-F3U-1** |

Timeout alone never means FAILED. Refund only from confirmed CAPTURED.

---

## 7. Test matrix (S0 plan — not implementing)

| Layer | F0 | F1-P | F2 | F3-Recovered | F3-Unresolved |
| --- | --- | --- | --- | --- | --- |
| **Unit** (fault parse, transitions) | verify NONE | CHARGE_FAIL mapping | arrange reject mapping; 4xx⇒BUSINESS | F3 observe vs persist | opaque attempt counter |
| **Service IT** | — | payment (exists) | shipping: no row; idempotent replay fail | payment F3 (exists) | resolve/GET opaque; DB CAPTURED; post-disarm exposes |
| **Orchestration IT** | happy path | `t201_1`-style | **real** shipping header (not RecordingActivities) | F3 header → SUCCESS | F3+opaque → AMBIGUOUS path; no refund |
| **Runner IT** | arm/verify F0 | arm CHARGE_FAIL | arm arrange code | arm F3-Recovered | arm F3-Unresolved; effectiveConfig recorded; disarm |

---

## 8. Remaining Owner decisions

| ID | Question | Blocking? |
| --- | --- | --- |
| **OQ-F2-1** | Ratify new `ErrorCode` name: `BUSINESS_REJECTED` vs `ARRANGE_REJECTED` (HTTP **409** already Specified) | Soft for S0 review; hard before S2 coding |
| **OQ-F3U-1** | Oracle label for F3-Unresolved terminal (AMBIGUOUS vs INVALID) | **Hard** for F3-Unresolved acceptance |
| **OQ-F3U-2** | Exact opaque transport: HTTP **503** vs connection reset / empty body (must stay non-authoritative on business state) | Soft; default proposal **503** on resolve/GET only |
| **OQ-AUTH** | Separate **D-AUTH** / `NEXT_TASK=approved` before S1–S4 Java work | **Hard** before implementation |

---

## 9. Explicit non-goals (TH-400 S0 / later impl)

- No protocol constant changes (`T_observe`, `T_run_max`).  
- No Oracle label set changes.  
- No design contract rev. 3.1 rewrite.  
- No F1-I, Toxiproxy, Variant C implementation, TH-208 lost-ID-without-paymentId.  
- No marking Taiga #35/#37 Done from S0 alone.  
- No Java/service/test code in S0.

---

## 10. Document control

| Artifact | Role |
| --- | --- |
| This file | Normative TH-400 fault profile S0 spec |
| `coordination/DECISION_LOG.md` → D-404 | Records accepted directions + pointers |
| `coordination/TH_400_S0_PLAN.md` | Slice breakdown pointer |
| Implementation | Requires future D-AUTH (not granted here) |
