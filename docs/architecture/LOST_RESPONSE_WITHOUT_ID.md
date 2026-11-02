# Lost response after commit without returned resource ID (TH-208)

| Field | Value |
| --- | --- |
| **Status** | Architecture investigation complete — **implementation NOT approved** |
| **Date** | 2026-11-02 |
| **Deferred by** | D-202-3 |
| **Related** | D-201; D-015; protocol-v0 F3 / timeout≠failure; TH-400; TH-401 |
| **Labels** | Source fact · Synthesis · Design rationale · Hypothesis · Open question · PROPOSED |

This document does **not** change D-201, D-202, invariants, or the experimental protocol. No application code, endpoints, or schema changes were made in this investigation.

---

## 1. Current behavior and concrete failure sequence

### 1.1 How IDs enter the workflow (Source fact)

`OrderFulfillmentWorkflowImpl` stores resource IDs **only after** a successful activity return:

| Step | Activity | ID stored from response |
| --- | --- | --- |
| Reserve | `activities.reserve` | `reservationId` |
| Charge | `activities.charge` | `paymentId` |
| Arrange | `activities.arrange` | `shipmentId` |
| Consume / release / refund / cancel | keyed by prior IDs | — |

HTTP activities: `HttpOrderFulfillmentActivities` (RestClient POST with `Idempotency-Key`).

Compensation (`runCompensation`) only acts when IDs are non-null, after authoritative GET:

- refund only if payment GET shows `CAPTURED`
- release only if reservation GET shows `HELD`
- cancel only if shipment GET shows `ARRANGED`

### 1.2 What services do on success (Source fact)

For **create** mutations (`reserve`, `charge`, `arrange`):

1. Advisory lock on idempotency key.
2. If key exists → **replay** stored JSON response (includes resource id + state).
3. Else insert business row (`UUID.randomUUID()` id) + idempotency row + `operation_log` **in one transaction**.
4. Return response body to client.

Unique `order_id` (payment, shipment) / unique HELD reservation per order (inventory) prevent duplicate business rows.

**Implication (Synthesis):** If the DB transaction commits and the HTTP body is lost, a **later POST with the same Idempotency-Key** returns the original response **including the id**. Temporal activity retries already use the same keys via `IdempotencyKeys`.

### 1.3 Failure sequence of interest (Synthesis)

```text
1. Workflow starts charge (key = {orderId}:charge).
2. Payment service commits CAPTURED (or UNKNOWN) payment row + idempotency record.
3. Response body never reaches the activity worker (timeout / connection drop).
4a. Temporal retries → replay → activity succeeds → paymentId known.  [RECOVERED]
4b. All attempts fail to deliver body → ActivityFailure; workflow paymentId still null.
5. finishWithCompensation runs with paymentId == null → refund-skipped:none.
6. Outcome defaults to COMPENSATED_FAILURE if pendingOutcome unset.
```

Step **4a** is the common case under D-202 retry policy. Step **4b+6** is the residual reliability / semantic gap (D-202-3).

### 1.4 Distinct from F3 fault header (Source fact)

`ThesisFault.F3_CAPTURE_LOST_RESPONSE`: persist `CAPTURED`, **return** body with `paymentId` and observed `UNKNOWN`. Orchestrator **has** the id. Protocol F-PAY-AMB / UNKNOWN path applies. **Not** the same as “no id returned.”

---

## 2. Affected operations and services

| Operation | Service | Idempotency key | Creates id? | Public GET by id | GET by orderId API | Unique order constraint |
| --- | --- | --- | --- | --- | --- | --- |
| `reserve` | inventory | `{orderId}:reserve` | Yes `reservationId` | Yes | **No** | Unique HELD per order |
| `release` / `consume` | inventory | `{reservationId}:…` | No (needs id) | Yes | **No** | — |
| `charge` | payment | `{orderId}:charge` | Yes `paymentId` | Yes | **No** | Unique `order_id` |
| `resolve` | payment | none | No | Yes | **No** | — |
| `refund` | payment | `{paymentId}:refund` | No (needs id) | Yes | **No** | — |
| `arrange` | shipping | `{orderId}:arrange` | Yes `shipmentId` | Yes | **No** | Unique `order_id` |
| `cancel` | shipping | `{shipmentId}:cancel` | No (needs id) | Yes | **No** | — |

**Highest risk create ops:** `reserve`, `charge`, `arrange` (id minted server-side; follow-ups need id).

**Lower risk:** `resolve` (needs `paymentId` already); compensate mutations (need prior ids).

**Code paths inspected (Source fact):**

- `orchestration/variant-a/.../OrderFulfillmentWorkflowImpl.java`
- `orchestration/variant-a/.../HttpOrderFulfillmentActivities.java`
- `orchestration/variant-a/.../ActivityPolicies.java`
- `contracts/.../IdempotencyKeys.java`, `IdempotencyRules.java`
- `services/{inventory,payment,shipping}/.../*Service.java`, `*Repository.java`, `*Controller.java`
- `services/*/db/migration/V1__*_schema.sql`
- `docs/architecture/VARIANT_A_RETRIES_TIMEOUTS.md`, `VARIANT_A_HAPPY_PATH.md`
- `docs/protocol/BUSINESS_INVARIANTS_AND_ORACLE.md`, `EXPERIMENTAL_PROTOCOL_V0.md`

---

## 3. Recovery alternatives and trade-offs

### Option A — Rely on Temporal retries + idempotent replay only (status quo)

| Dimension | Assessment |
| --- | --- |
| Idempotency | Already correct for create ops (Source fact) |
| Races | Advisory lock + unique order keys (Source fact) |
| Ownership / auth | Unchanged |
| Compensation | Safe **when** id eventually obtained; unsafe labeling when not (see §3.E) |
| UNKNOWN | Unrelated; F3 with id still works |
| Oracle | DB-primary still sees truth by `orderId` (Source fact) |
| Complexity | None |
| Gap | Exhausted retries without body → no id; false `COMPENSATED_FAILURE` possible (**Synthesis**) |

### Option B — Explicit idempotent re-POST recovery before compensate (no new endpoints)

After forward `ActivityFailure`, if expected create ids are missing, workflow (or compensation prelude) re-invokes `reserve`/`charge`/`arrange` with the **same** keys solely to obtain replayed bodies, then compensates with GETs.

| Dimension | Assessment |
| --- | --- |
| Idempotency | Uses existing replay semantics (**Design rationale**) |
| Races | Same as today |
| Compensation | Enables refund/release/cancel when row exists |
| UNKNOWN | If replayed charge shows UNKNOWN/CAPTURED, follow D-201 (no refund unless CAPTURED confirmed via GET) |
| Oracle | Unchanged |
| Complexity | Low–medium (workflow/test only; **no** new HTTP API) |
| Limitation | If network still down, recovery fails → need honest outcome (§3.E) |

### Option C — Lookup by `orderId` (new read APIs)

Expose `GET ...?orderId=` or `/by-order/{orderId}` for payment / reservation / shipment. DB already unique on order for payment/shipment; inventory has order index + unique HELD.

| Dimension | Assessment |
| --- | --- |
| Idempotency | Read-only; orthogonal |
| Races | Read committed snapshot; compensate still GET-by-id after discovery |
| Auth / ownership | Experimental localhost: orderId knowledge ≈ capability; document as harness-trusted |
| Compensation | Strong discovery even without replay |
| Oracle | Aligns with oracle’s orderId-centric snapshot (**Synthesis**) |
| Complexity | Medium (controllers + repo queries + contract OpenAPI + tests) |
| Constraint note | Requires **new endpoints** (and likely thin repo methods) — **not** implementable under “no new endpoints” if Owner forbids API growth; fine as **follow-up** |

### Option D — Deterministic resource IDs

Derive `paymentId`/`reservationId`/`shipmentId` from `orderId` (+ operation) so orchestrator knows ids a priori.

| Dimension | Assessment |
| --- | --- |
| Idempotency | Still required |
| Compatibility | Breaks current `UUID.randomUUID()` inserts; migration/risk |
| Complexity | High relative to benefit given Option B |
| Recommendation | **Not preferred** for MVP |

### Option E — Honest terminal labeling without discovery

If ids remain unknown after recovery attempts: **do not** emit workflow `COMPENSATED_FAILURE`. Prefer `AMBIGUOUS` or fail with a distinguishable non-compensated signal (align with U-201-5 / timeout≠failure). Does **not** free HELD/CAPTURED resources alone.

| Dimension | Assessment |
| --- | --- |
| Oracle | Matches “timeout ≠ confirmed failure” better than false compensated success (**Synthesis**) |
| Compensation | Incomplete cleanup possible → harness/oracle must still read DB |
| Complexity | Low (workflow outcome rules) |
| Complements | Options B and/or C |

### Option F — Idempotency-store read API

`GET /idempotency/{key}` returning stored body.

Redundant with Option B’s re-POST; extra surface. **Not preferred** if B is available.

---

## 4. Recommended design

**Primary recommendation (PROPOSED):** **Option B + Option E**, sequenced as one implementation ticket after Owner approval.

1. **Recovery prelude (B):** Before compensation, when a create-step may have committed without returning an id, perform at most one intentional idempotent re-POST per missing create (`reserve` / `charge` / `arrange` as applicable) using existing `IdempotencyKeys`.
2. **Authoritative reconcile:** Existing GET-before-mutate compensation rules (D-201) unchanged — never refund without CAPTURED; never release without HELD.
3. **Honest outcome (E):** If ids still missing **or** required compensation cannot be evidenced, do **not** report `COMPENSATED_FAILURE`. Prefer `AMBIGUOUS` when payment may be unresolved/CAPTURED-without-refund path, or `COMPENSATION_INCOMPLETE` / equivalent distinguishable failure when cleanup was required but impossible. Exact mapping is an Owner-facing detail in proposed D-208 (below).
4. **Do not** treat missing response as payment `FAILED`.

**Secondary / later (PROPOSED, optional):** **Option C** (GET by orderId) as harness/oracle convenience and Variant C parity — especially useful for TH-401/TH-400 without depending on Temporal retry success. Can land after or with B+E; not required to close the D-202-3 gap if B+E are solid.

**Not recommended now:** Option D (deterministic ids); Option F alone.

**Why multiple approaches remain available:** B+E fix orchestrator safety without protocol change; C improves observation and fairness tooling. Keeping C optional avoids blocking on API expansion.

---

## 5. Required tests (for future implementation)

Prefer `TestWorkflowEnvironment` + controllable HTTP/activity double that **commits side effect in the double’s memory/DB mock then fails the first N returns**, then optionally succeeds on recovery re-POST.

| ID | Scenario | Expected |
| --- | --- | --- |
| T-208-1 | Charge commits; first responses lost; Temporal retry replay returns id | Workflow continues or compensates with known `paymentId`; not false FAILED |
| T-208-2 | Charge commits; all attempts lose body; **no** recovery prelude (today) | Documents current gap OR, after impl, recovery prelude runs |
| T-208-3 | After B: recovery re-POST returns CAPTURED id → compensate refund+release | `COMPENSATED_FAILURE` only if compensation completes; GET confirms CAPTURED before refund |
| T-208-4 | After B: recovery returns UNKNOWN observed / CAPTURED in GET | No refund until CAPTURED confirmed; UNKNOWN→AMBIGUOUS rules preserved |
| T-208-5 | After B+E: recovery cannot obtain ids | **Not** `COMPENSATED_FAILURE`; AMBIGUOUS or incomplete per D-208 |
| T-208-6 | Reserve lost-id analogous to charge | Release only if HELD after id recovery |
| T-208-7 | Arrange lost-id | Cancel only if ARRANGED after id recovery |
| T-208-8 | Distinguish F3 header (id present, UNKNOWN observed) from true lost-id | Existing U-201-2 / F3 tests remain green |

True lost-response-after-commit must **not** be faked as “business FAILED.”

---

## 6. Impact on oracle and experiment harness

| Component | Impact |
| --- | --- |
| **Oracle (TH-401)** | Remains DB-primary by `orderId` (**Source fact**). Independent of workflow-reported outcome. Lost-id makes workflow/oracle divergence more visible — harness should prefer oracle labels for RQ1/RQ2. |
| **Protocol F3** | Still the **observed UNKNOWN with id** fault; keep separate from lost-id scenario in TH-400 profiles (**Synthesis**). |
| **Harness** | Should record both workflow outcome and oracle outcome; flag mismatch when workflow says compensated but DB shows CAPTURED+HELD. |
| **T_observe / T_run_max** | Unchanged (D-202-4). Recovery prelude must fit under activity schedule budgets or use compensation options. |

---

## 7. Proposed decision-log entry (**PROPOSED — not approved**)

### D-208 — Lost-response-without-returned-ID recovery (PROPOSED)

| Field | Value |
| --- | --- |
| **Status** | **proposed** (awaiting Owner) |
| **Context** | Closes D-202-3 deferral for Variant A reliability |
| **Decision (proposed)** | Adopt **idempotent re-POST recovery (B)** before compensation when create ids are missing, plus **honest non-COMPENSATED_FAILURE labeling (E)** when discovery fails. Optionally schedule **GET-by-orderId (C)** as a follow-up for harness/oracle ergonomics. Do not adopt deterministic ids (D) for MVP. |
| **Non-goals** | No change to D-201 compensation order; no refund without CAPTURED; no release without HELD; no treating timeout as FAILED; no protocol bump required for B+E |
| **Related** | D-202-3; D-201; TH-208; TH-400; TH-401 |

Open mapping question for Owner: when recovery fails and DB may hold CAPTURED, is workflow `AMBIGUOUS` mandatory vs `COMPENSATION_INCOMPLETE`?

---

## 8. Suggested implementation subtasks (not approved)

| Subtask | Depends | Notes |
| --- | --- | --- |
| TH-208.1 Workflow recovery prelude + outcome honesty (B+E) | Owner approves D-208 | Core fix; estimate **2 SP** |
| TH-208.2 Tests T-208-1…8 | TH-208.1 | Incl. true lost-response double; **1 SP** |
| TH-208.3 Docs update (`VARIANT_A_*`, retries doc cross-link) | TH-208.1 | **0.5 SP** |
| TH-208.4 (optional) GET-by-orderId APIs + activities (C) | D-208 optional clause | **1–2 SP**; useful for TH-401 |
| TH-400 profile | TH-208.1+ | Add distinct lost-id fault vs F3 header |

**Total suggested (B+E only):** ~**3–3.5 SP**.  
**With C:** ~**4.5–5.5 SP**.

Do **not** start until Owner approves D-208 / implementation brief.

---

## 9. Risks and unresolved questions

1. **Outcome taxonomy:** AMBIGUOUS vs COMPENSATION_INCOMPLETE when CAPTURED may exist but id unknown — Owner choice (Open question).
2. **Partial progress:** reserve id known, charge id lost — recovery must be per-missing-id (Synthesis).
3. **Idempotency key mismatch:** recovery must use exact `IdempotencyKeys` helpers; wrong key → duplicate risk / 409.
4. **Variant C:** same HTTP surface; recommendation should apply to shared activities for fairness (Hypothesis).
5. **Inventory unique HELD:** released reservation may allow new HELD later; GET-by-orderId must define which row (latest vs HELD-only) if Option C is built (Open question).

---

## 10. Coordination / ticket state

- Investigation deliverable: this file.
- Implementation: **not approved**.
- D-208: **PROPOSED** only in `coordination/DECISION_LOG.md`.
