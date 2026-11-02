# TH-400 S2 — Technical Feasibility Review (F2 Shipping)

**Mode:** READ-ONLY / DESIGN ONLY (historical review; D-AUTH later granted)  
**Date:** 2026-11-02  
**Base inspected:** `origin/main` @ `c732558` (PR #12 merged)  
**D-AUTH-S2:** **GRANTED** (Owner „zatwierdzam”; see `DECISION_LOG` D-AUTH-S2) — impl on `feature/th-400-s2-f2`  
**Related proposal:** `coordination/TH_400_S2_PROPOSAL.md`

Labels used below: **Source fact** · **Proposed design** · **Open question**

---

## 1. Current arrange path (Source fact)

### Call chain (Variant A trial today)

1. **Runner** `VariantAAdapter.start` builds `OrderFulfillmentInput` with optional `chargeFaultHint` / `resolveFaultHint` only (`VariantAAdapter.java` ~53–65).  
2. **Workflow** `OrderFulfillmentWorkflowImpl.runForward`: reserve → charge(…, chargeFaultHint) → **`activities.arrange(orderId)` with no fault argument** (L106). Same in `handleUnresolvedPayment` (L137).  
3. **Recovery** `recoverMissingCreateIds`: if `arrangeAttempted && shipmentId == null` → **`recoveryActivities.arrange(state.orderId)` again with no hint** (L270–275). Charge recovery **does** pass `state.chargeFaultHint` (L255–256).  
4. **Activity API** `OrderFulfillmentActivities.arrange(UUID orderId)` — no fault param (`OrderFulfillmentActivities.java` L29–30).  
5. **HTTP** `HttpOrderFulfillmentActivities.arrange` posts arrange with **`faultHint = null`** always (L63–70), though the shared `post(…, faultHint)` helper already knows how to set `X-Thesis-Fault` (L157–170).  
6. **ShippingController.arrange** — reads only `Idempotency-Key` + body; **no** fault header (L29–34).  
7. **ShippingService.arrange** (`ShippingService.java` L46–75):  
   - advisory lock on key;  
   - load idempotency `FOR UPDATE`; if present → **`replay` as `ArrangeResponse`** (always success-shaped);  
   - else insert shipment → persist idempotency **`http_status=200`** + op-log → return ARRANGED.  
8. **Schema** (`V1__shipping_schema.sql`): `idempotency_records.http_status` already exists; `operation_log.shipment_id` **nullable**; unique `order_id` on shipments.  
9. **Errors:** `ShippingException` → `ShippingExceptionHandler` → `ApiError` + status (`ShippingExceptionHandler.java` L17–20). Shared `ErrorCode` today has **no** `ARRANGE_REJECTED` (`ErrorCode.java`).  
10. **Shipping config:** no `thesis.shipping.fault-injection-enabled` (`application.yml` — only datasource/actuator).

### Compensation after arrange failure (Source fact)

`HttpOrderFulfillmentActivities.toFailure`: HTTP 4xx → non-retryable `BUSINESS` (L183–190).  
`t201_2` already proves arrange-fail → refund CAPTURED + release HELD (`OrderFulfillmentCompensationIT`).  
Oracle `isF2`: RELEASED + (REFUNDED|FAILED) + no arranged shipment → `CONFIRMED_FAILURE` / pattern F2 (`OutcomeClassifier.java` L263–278). Classifier **need not change** for clean F2.

---

## 2. Fault hint propagation

### Can Runner pass F2 exclusively to arrange today?

**No (Source fact).** There is:

- no `arrangeFaultHint` on `OrderFulfillmentInput` (javadoc mentions only payment hints);  
- no arrange fault parameter on activities;  
- shipping HTTP never reads `X-Thesis-Fault`;  
- `chargeFaultHint` is payment-only and **must not** be reused for shipping (different service, codes, semantics).

### Minimum required orchestration changes (Proposed design)

| # | File | Change |
| --- | --- | --- |
| 1 | `orchestration/variant-a/.../model/OrderFulfillmentInput.java` | Add `String arrangeFaultHint` (nullable); keep 4-arg ctor → nulls; extend compact ctors carefully |
| 2 | `orchestration/variant-a/.../OrderFulfillmentActivities.java` | `arrange(UUID orderId, String faultHint)` |
| 3 | `orchestration/variant-a/.../HttpOrderFulfillmentActivities.java` | Pass `faultHint` into existing `post(..., faultHint, ...)` for arrange |
| 4 | `orchestration/variant-a/.../OrderFulfillmentWorkflowImpl.java` | Forward + unresolved-payment arrange: pass `input.arrangeFaultHint()`; **SagaState** store `arrangeFaultHint`; **recovery arrange must pass the same hint** (mirror charge) |
| 5 | Tests implementing `OrderFulfillmentActivities` | e.g. `RecordingActivities.java` — signature update |
| 6 | Call sites constructing input | Compensation/HappyPath/LostId/Retry/Order tests; **`orchestration/console/.../ConsoleRunService.java`** (null hint) |

**Runner (not orchestration, but required):** `VariantAAdapter` — map armed F2 → `arrangeFaultHint` (e.g. `ARRANGE_REJECTED`); F0/F1-P → null arrange hint; do not set charge hint for F2.

### Critical recovery invariant (Source fact + Proposed design)

Today recovery re-POSTs arrange **without** a fault header. After a committed F2 failure row, **idempotency-first replay** (see §3) still returns the failure even without the header — good.  
If the first F2 attempt **crashed before** committing the failure row, recovery without header would **create a shipment** — unacceptable for F2. Therefore D-AUTH must require **hint on recovery arrange**, not only on the forward path.

---

## 3. Failure idempotency design (Proposed design; grounded in Source fact)

### Existing behavior (Source fact)

- Success path stores `http_status=200` + JSON `ArrangeResponse`.  
- Replay always `objectMapper.readValue(..., ArrangeResponse.class)`.  
- Fingerprint mismatch → `IdempotencyConflictException` → HTTP 409 `IDEMPOTENCY_KEY_CONFLICT`.  
- No failure-body replay path exists.

### Smallest compatible extension (Proposed design) — **no schema migration required**

Reuse `idempotency_records (http_status, response_body)`:

| First outcome | `http_status` | `response_body` |
| --- | --- | --- |
| Success | 200 | `ArrangeResponse` JSON |
| F2 reject | 409 | `ApiError` JSON with `code=ARRANGE_REJECTED` |

**Arrange algorithm (order is mandatory):**

```
@Transactional
arrange(key, request, faultHeader):
  requireKey; fp = fingerprint(request)
  advisoryLock(key)
  existing = findIdempotencyForUpdate(key)
  if existing.isPresent():
      assertPayloadCompatible(existing.fp, fp)   // else IDEMPOTENCY_KEY_CONFLICT
      if existing.http_status == 200:
          return deserialize ArrangeResponse     // happy replay — unchanged
      else:
          throw ShippingException from stored ApiError / status
          // MUST NOT re-evaluate fault injection; MUST NOT insert shipment
  // --- first attempt only ---
  if injectionEnabled && faultHeader maps to ARRANGE_REJECTED:
      insertIdempotency(key, "arrange", fp, 409, ApiError JSON)
      appendOperationLog(arrange, shipment_id=null, orderId, key,
          {"fault":"ARRANGE_REJECTED","shipmentCreated":false})
      throw ShippingException(ARRANGE_REJECTED, 409, ...)
  // happy path unchanged: insert shipment → 200 idempotency → op-log
```

### Requirements mapping

| Requirement | How |
| --- | --- |
| First F2 → 409 + `ARRANGE_REJECTED` | Throw after failure idempotency+op-log insert; controller advice already maps `ShippingException` |
| Same key+fp replay after disarm | Existing row hit **before** injection check → throw stored failure |
| Same key+diff fp | Existing `assertPayloadCompatible` |
| No shipment row | Never call `repo.insert` on F2 path |
| Atomicity | Single `@Transactional` method (already used) |
| Success replay unchanged | Branch on `http_status==200` |

### Locking / uniqueness / crashes (Proposed design)

| Concern | Behavior |
| --- | --- |
| Lock | Existing `pg_advisory_xact_lock(hashtext(key))` |
| Unique shipment | `uq_shipments_order` unused on F2 path |
| Crash before commit | No shipment, no idempotency; retry under armed F2 rejects again and commits |
| Crash after commit | Durable 409 replay forever for key+fp |
| Disarm then retry same key+fp | Replay failure (injection off irrelevant) |

### Controller change (Proposed design)

`ShippingController.arrange`: add optional `@RequestHeader(value="X-Thesis-Fault", required=false) String fault` and pass into service — **same header name as payment**, new shipping fault enum/code mapping (not payment `ThesisFault`).

### Contracts (Proposed design)

Add `ErrorCode.ARRANGE_REJECTED`. Optionally OpenAPI components update if present in module docs.

---

## 4. Shipping preflight (fail-closed)

### Probe design (Proposed design)

Analogous to S1 payment probe:

- Unique `probeOrderId`, unique idempotency key (`f2-preflight-{uuid}`).  
- `POST /api/v1/shipments/arrange` with `X-Thesis-Fault: ARRANGE_REJECTED` (or agreed token).  
- Success criterion: **HTTP 409** + body `code == ARRANGE_REJECTED` (not merely any 409).  
- Assert no shipment for probe orderId (optional JDBC/GET-by-order if available; today GET is by shipmentId only — rely on 409 semantics + later StateReset).

### Does 409+ARRANGE_REJECTED prove injection enabled?

**Yes, against real Shipping after S2**, if that code is **only** produced by the injection path (Proposed design constraint: do not use `ARRANGE_REJECTED` for other arrange failures).  
**Source fact today:** code does not exist yet — preflight is meaningless until shipping implements it.

### Durable residue (Source fact of StateReset + Proposed probe)

Leaves: `shipping.idempotency_records` row + `operation_log` (null shipment_id); **no** shipments row if F2 path correct.  
`StateReset` deletes shipping `operation_log`, `idempotency_records`, `shipments` (L55–58) — **cleans probe**.  
Oracle loads by **trial `orderId`** only — probe UUID does not enter trial snapshot (**Source fact** `OracleSnapshotRepository`).

### False positives / fail-closed

| Case | Behavior |
| --- | --- |
| Injection off | Happy arrange → **200 ARRANGED** (creates shipment for probe orderId!) — treat as **not confirmed**; StateReset still wipes |
| Wrong/fake base URL | Forged 409+JSON → **false positive** (same trust model as S1 payment URL) |
| Timeout / transport / non-409 / wrong code / malformed body | Fail-closed → `FAULT_VERIFY_FAILED` |
| IDEMPOTENCY_KEY_CONFLICT 409 | Must **not** count as injection proof — require `ARRANGE_REJECTED` specifically |

**Recommendation:** Implement fail-closed shipping preflight in Runner verify for profile `F2` only; require `--shipping-base-url`; document URL trust; optionally DELETE is unnecessary given StateReset ordering (reset runs **before** arm/verify today — **Source fact** `TrialRunner` reset→arm→verify).  

**Ordering note (Source fact + Open question):** Today verify runs **after** reset, so probe residue remains for the trial duration (like S1 payment probe). Isolated by orderId. Acceptable if documented; optional post-verify cleanup is **out of minimal scope**.

**Caveat:** Probe with injection **off** creates a real ARRANGED shipment for probe orderId until reset of **next** trial — same class of residue as S1. Prefer documenting; do not weaken fail-closed to docs-only.

---

## 5. FaultController / runner (Proposed design)

| Item | Spec |
| --- | --- |
| Profile id | Canonical **`F2`** only |
| Registration | Extend `CompositeFaultController` with `F2FaultController` (+ keep F0, F1-P) |
| `effectiveConfig` | `faultProfileId=F2`, `trialId`, `orderId`, `mechanism=HEADER`, `header=X-Thesis-Fault`, `code=ARRANGE_REJECTED`, `service=shipping` |
| Lifecycle | Existing TrialRunner: reset → arm → **verify (shipping preflight)** → start → … → finally disarm |
| Adapter | F2 → `arrangeFaultHint=ARRANGE_REJECTED`, charge/resolve hints null; F1-P unchanged; F0 all null |
| Isolation | Per-trial workflow input; serial trials + env lock (**D-R7** Source fact) |
| Verify fail | `FAULT_VERIFY_FAILED`, no workflow start |
| Disarm fail | Existing `blockFurtherTrials=true` |
| CLI | `--fault-profile=F2` requires `--shipping-base-url`; F0 default; F1-P still needs payment URL |

F0/F1-P must not call shipping preflight.

---

## 6. Test matrix

| # | Layer | Case | Infra |
| --- | --- | --- | --- |
| T1 | Contracts unit | `ErrorCode.ARRANGE_REJECTED` present | none |
| T2 | Shipping unit/IT | First F2 → 409 + code + **no** shipment row | shipping+DB |
| T3 | Shipping IT | Replay same key+fp after “disarm” (injection off) still 409 | shipping+DB |
| T4 | Shipping IT | Same key+diff fp → `IDEMPOTENCY_KEY_CONFLICT` | shipping+DB |
| T5 | Shipping IT | Happy-path arrange + success replay unchanged | shipping+DB |
| T6 | Shipping IT | Injection off + header ignored → ARRANGED | shipping+DB |
| T7 | Orchestration IT | Real header F2 → refund+release, no shipment (extend/alongside `t201_2`) | services+Temporal test env |
| T8 | Orchestration unit | Recovery arrange uses hint / idempotent failure (no late create) | Temporal test |
| T9 | Runner unit | F2 arm/verify/disarm; effectiveConfig; F0/F1-P no shipping probe | none |
| T10 | Runner unit | Adapter F2→arrange hint only; F1-P→charge only | none |
| T11 | Runner lifecycle | Verify fail does not start; disarm isolation | mocked adapter |
| T12 | Oracle unit/fixture | Clean F2 snapshot → `CONFIRMED_FAILURE` (pattern F2) | none |
| T13 | Regression | F0 lifecycle + F1-P tests + `t201_1` + payment confirmedFailure | as today |
| T14 | Optional live E2E | Runner `--fault-profile=F2` + Temporal | **serial** lab; not default gate |

**Without shared Temporal:** T1–T6, T9–T13.  
**Need Temporal test env / serial lab:** T7–T8, T14.

---

## 7. Exact affected files (implementation allowlist draft)

### Must change

**Contracts:** `ErrorCode.java` (+ tests/OpenAPI if applicable)  
**Shipping:** `ShippingController`, `ShippingService`, new fault enum/properties (mirror payment), `application.yml` / `application-test.yml`, shipping IT  
**Orchestration (explicit):** files listed in §2 table (input, activities, HTTP activities, workflow, RecordingActivities, console input site, variant-a tests as needed)  
**Runner:** `F2FaultController`, Composite/CLI wiring, `VariantAAdapter`, shipping preflight verifier/transport, README, runner tests  

### Must not change (unless separate auth)

Oracle `OutcomeClassifier` · S0 normative spec text · protocol-v0 · rev. 3.1 · Payment Service · inventory · Variant C / TH-303

---

## 8. Risks and unresolved questions

| ID | Item | Type |
| --- | --- | --- |
| R1 | Orchestration **must** change — runner-only D-AUTH is **insufficient** | Source fact |
| R2 | Recovery arrange without hint can create shipment if F2 TX never committed | Source fact → design must include hint on recovery |
| R3 | Probe with injection off creates ARRANGED row until next reset | Source fact / limitation |
| R4 | Fake `shipping-base-url` false positive | Same as S1 |
| R5 | Header token string: use `ARRANGE_REJECTED` vs separate `ThesisShippingFault` enum value | **Open question** for D-AUTH wording (recommend enum + same string as ErrorCode name) |
| R6 | Temporal activity interface change → worker rebuild; console compatibility | Proposed / ops |
| R7 | Story points: proposal said 3; orchestration+idempotency+preflight suggests **5 SP** (or 3 with hard scope freeze) | Proposed |
| R8 | Whether D-AUTH allows console + test signature churn as drive-by | Owner decision |

---

## 9. Recommended story points

**5 SP** (contracts + shipping failure idempotency + orchestration hint plumbing including recovery + runner F2/preflight + tests).  
If Owner freezes orchestration allowlist and defers live E2E, still **≥ 3 SP**; **3 SP alone is optimistic**.

---

## 10. Recommendation for D-AUTH-S2

**READY FOR A NARROWLY SCOPED D-AUTH-S2 — with mandatory orchestration allowlist.**

D-AUTH should explicitly authorize:

1. `ErrorCode.ARRANGE_REJECTED` + shipping header injection + failure idempotency replay (idempotency **before** injection check).  
2. Orchestration files in §2 (including **recovery arrange hint**).  
3. Runner F2 controller + shipping fail-closed preflight + adapter mapping.  
4. Listed tests; F0/F1-P regression.  

D-AUTH should explicitly **exclude:** Oracle classifier edits, S0 doc rewrites, F3, Variant C, Taiga Done, payment changes.

**D-AUTH-S2 remains ungranted until the Owner explicitly approves it.**

---

## Appendix — Verified vs proposed summary

| Topic | Status |
| --- | --- |
| Arrange has no fault channel today | **Source fact** |
| Idempotency success-only replay | **Source fact** |
| Schema already has `http_status` + nullable `shipment_id` in op-log | **Source fact** |
| StateReset clears shipping probe tables | **Source fact** |
| Oracle isolation by orderId | **Source fact** |
| Failure idempotency + preflight + F2 controller | **Proposed design** |
| Header token naming | **Open question** |
