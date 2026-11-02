# Independent Oracle — architecture analysis & implementation proposal (TH-401)

| Field | Value |
| --- | --- |
| **Status** | TH-401-A **implemented** on `feature/th-401-independent-oracle` (Option C); TH-401-B deferred |
| **Date** | 2026-11-02 · amended (TH-401.1 / .2 audit · TH-401-A impl) |
| **Ticket** | TH-401 (Taiga #36) |
| **Depends on** | TH-011 / D-011 · protocol-v0 / D-012 · TH-102–104 · D-201 · D-208 |
| **Non-goals** | No application code · no API changes · no protocol edits · no D-201/D-208 edits · no Experiment Runner |
| **Label legend** | **Source fact** · **Protocol requirement** · **Recommendation** · **Owner decision (open)** · **Conflict** |

> Oracle labels for RQ1/RQ2 come **only** from durable business state. Temporal status, workflow `BusinessOutcome`, console hints, and LLM text are **never** sufficient for success.

---

## 1. Executive summary

TH-401 should deliver a **read-only, DB-primary** evaluator that, at harness **TERMINAL**, returns exactly one frozen oracle label:

`CONFIRMED_SUCCESS` | `CONFIRMED_FAILURE` | `AMBIGUOUS` | `INVALID`

(**Protocol requirement:** TH-011 / D-011 / protocol-v0 — four labels unchanged.)

**Source fact:** Services persist authoritative rows keyed by `orderId` in schemas `inventory` / `payment` / `shipping`, with atomic `idempotency_records` + entity + `operation_log` on successful mutations.

**Recommendation (TH-401.1):** Map workflow `COMPENSATION_INCOMPLETE` to oracle **`AMBIGUOUS` + `pattern=COMPENSATION_INCOMPLETE`** when I1–I4/I7 pass but F0–F2 / I5 / I6 cleanliness fail; use **`INVALID`** only when invariants are actually violated. Reject mapping incomplete compensation to `CONFIRMED_FAILURE`. Clarify that protocol `AMBIGUOUS` means *cannot confirm SUCCESS or CONFIRMED_FAILURE*, **not** “DB state unknown”.

**Owner decision (open):** approve TH-401 implementation / NEXT_TASK (process), not a protocol amendment — see **Protocol Compatibility — Final Audit** (`OPTION_C_COMPATIBLE_WITH_FROZEN_PROTOCOL`).

---

## 2. Data model (confirmed)

### 2.1 Tables (**Source fact**)

| Schema | Table | Oracle role |
| --- | --- | --- |
| `inventory` | `reservations`, `stock`, `operation_log`, `idempotency_records` | States HELD/RELEASED/CONSUMED; I1; forensics |
| `payment` | `payments`, `operation_log`, `idempotency_records` | Ground-truth payment state; F3 `detail` JSON |
| `shipping` | `shipments`, `operation_log`, `idempotency_records` | ARRANGED/CANCELLED; no row ⇒ conceptual NONE |

Unique: one payment / one shipment per `order_id`; at most one **HELD** reservation per `order_id`.

### 2.2 Correlation

| ID | In service DB? | Classifier input? |
| --- | --- | --- |
| `orderId` | Yes | **Yes — primary** |
| `sku` | Via reservation / protocol seed | Yes (I1) |
| `workflowId` / `trialId` | No | Metadata only |

### 2.3 Read path (**Recommendation**)

RO JDBC repositories by `orderId`; optional HTTP GET cross-check. Do **not** use console `BusinessConsistencyCheck` (depends on workflow result IDs — **Source fact**).

---

## 3. Classification reconciliation (TH-401.1)

### 3.1 Four distinctions (**Protocol requirement** + **Source fact**)

| Situation | Meaning | Oracle direction |
| --- | --- | --- |
| **Invariant violation** | Illegal combination (I1–I4, I7 fail) | `INVALID` |
| **Known resource, compensation required but not confirmed** | e.g. still `CAPTURED` / `HELD` / `ARRANGED` after process stopped | **Not** `CONFIRMED_FAILURE` (I6). Not SUCCESS. See §3.2 |
| **Unknown side-effect / unresolved payment** | `UNKNOWN`/`PENDING` payment; client timeout without authoritative resolution | `AMBIGUOUS` (D-012); timeout ≠ failure |
| **No durable row** | Mutation never committed for that `orderId` (atomic TX) | Authoritative **absence** of that entity’s side effect — may support F0/F1 patterns; **not** automatic AMBIGUOUS |

**Semantic note (**Recommendation**):** In TH-011, `AMBIGUOUS` = *observable state does not allow confirmed success or confirmed failure* (§2). It does **not** require that entity fields are null/unknown. A fully known dangling `CAPTURED` can still be `AMBIGUOUS` under that definition.

### 3.2 Options for mapping `COMPENSATION_INCOMPLETE`

**Source fact (Variant A):** `COMPENSATION_INCOMPLETE` is an `ApplicationFailure` type thrown when GET still shows `ARRANGED` / `CAPTURED` / `HELD` after compensation attempts (`OrderFulfillmentWorkflowImpl.assertNoOutstandingCompensationRequired`). It is **not** a `BusinessOutcome` enum value (`SUCCESS` \| `COMPENSATED_FAILURE` \| `AMBIGUOUS` only).

#### Option A — map to `INVALID`

| | |
| --- | --- |
| Idea | Treat any known incomplete compensation as invariant/correctness failure |
| RQ1 | Increases `RQ1_invalid`; never counts as success |
| RQ2 | Does **not** count as `RQ2_safe_recovery`; also **not** `RQ2_ambiguous` — incomplete recovery looks like correctness bugs |
| Fit to protocol | Stretches `INVALID` (“invariant violated / illegal combination”) when I1–I4 still **pass** (e.g. `CAPTURED`+`HELD` satisfies I4) |
| Pros | Sharp separation from UNKNOWN |
| Cons | Conflates “recovery didn’t finish” with “business rules broken”; weakens INVALID as illegal-state signal |

#### Option B — map to `CONFIRMED_FAILURE` + `pattern=COMPENSATION_INCOMPLETE`

| | |
| --- | --- |
| Idea | Count incomplete cleanup as confirmed failed/compensated termination |
| RQ1 | No success (OK) |
| RQ2 | **Falsely inflates `RQ2_safe_recovery`** — protocol defines safe recovery as clean F0–F2 |
| Fit to protocol | **Contradicts I6 and §2.2** (`CONFIRMED_FAILURE` forbids dangling HELD/ARRANGED/bare CAPTURED) |
| Verdict | **Reject** under current frozen protocol (would need protocol change — out of scope) |

#### Option C — protocol-aligned hybrid (**Recommendation**)

Keep the four labels. Classify from **snapshot**, not from workflow throw type:

1. If I1–I4 or I7 fail → **`INVALID`** (true invariant break).  
   Example: `CAPTURED` + `RELEASED` without `REFUNDED` (I4).
2. Else if I5 success package holds → **`CONFIRMED_SUCCESS`**.
3. Else if snapshot matches F0/F1/F2 and I6 → **`CONFIRMED_FAILURE`**.
4. Else if payment `UNKNOWN` / unresolved `PENDING` → **`AMBIGUOUS`**, `pattern=UNKNOWN_PAYMENT`.
5. Else if known dangling compensatable state (`CAPTURED` without refund, `HELD` without release, `ARRANGED` without cancel) → **`AMBIGUOUS`**, `pattern=COMPENSATION_INCOMPLETE`.
6. Else → **`AMBIGUOUS`**, `pattern=OTHER` (or `INVALID` only if a listed invariant fails).

| | |
| --- | --- |
| RQ1 | Incomplete ≠ success; I1–I4 breaks still feed `RQ1_invalid` |
| RQ2 | Incomplete → `RQ2_ambiguous`, **not** safe recovery — matches “ambiguous at deadline ≠ success and ≠ invariant violation” when I1–I4 hold |
| Fit to protocol | Uses §2.3 “partial → AMBIGUOUS or INVALID **per invariants**”; preserves I6 meaning |
| Relation to Owner brief | Does **not** equate incomplete with “unknown side effect”: mandatory `pattern` + evidence distinguish `COMPENSATION_INCOMPLETE` vs `UNKNOWN_PAYMENT`. Does **not** use `CONFIRMED_FAILURE` |

**Workflow signal mapping (analytical, not classifier input):**

| Workflow-side | Typical oracle if DB matches |
| --- | --- |
| `SUCCESS` | `CONFIRMED_SUCCESS` |
| `COMPENSATED_FAILURE` | `CONFIRMED_FAILURE` |
| `AMBIGUOUS` / UNKNOWN path | `AMBIGUOUS` |
| throw `COMPENSATION_INCOMPLETE` | `AMBIGUOUS`+`COMPENSATION_INCOMPLETE` **or** `INVALID` if I1–I4/I7 also fail |

**Owner decision (open) — Q2:** Accept Option C, or insist on Option A (INVALID for all incomplete), understanding RQ2 metric impact.

---

## 4. Per-operation durable footprint (**Source fact**)

All mutating success paths commit **entity change + idempotency row + operation_log** in one transaction (inventory/payment/shipping services). Failure **before** that commit leaves **no** new idempotency success row for that attempt.

| Operation | After success | After error before side effect | After commit + lost HTTP body | Can oracle distinguish? | When `AMBIGUOUS`? |
| --- | --- | --- | --- | --- | --- |
| **reserve** | `reservations` HELD; `stock.held`↑; idempotency; log `reserve` | e.g. insufficient stock: **no** reservation, **no** success idempotency (**Source fact**) | Row HELD exists; client may lack `reservationId` | Yes via `order_id` / HELD unique; recovery re-POST replays id (TH-208) | Not from missing row. AMBIGUOUS only if broader snapshot requires it (e.g. paired UNKNOWN payment) |
| **charge** | `payments` row (CAPTURED/FAILED/UNKNOWN per fault); idempotency; log with optional `persistedState`/`observedResponse` | Validation / fault-not-applicable: typically **no** row | Row exists (incl. F3 CAPTURED with observed UNKNOWN in response/log) | Yes by `order_id`. F3: DB CAPTURED ≠ client UNKNOWN | Payment state `UNKNOWN`/`PENDING` at TERMINAL → AMBIGUOUS. Lost body with CAPTURED row → classify from CAPTURED (not “unknown whether charged”) |
| **arrange** | `shipments` ARRANGED; idempotency; log | Conflict / error before insert: **no** row | Row ARRANGED exists | Yes by `order_id` | Missing id in workflow ≠ AMBIGUOUS for oracle if row present; if no row after technical attempt, absence = no arrange committed |
| **consume** | reservation CONSUMED; `on_hand`↓ `held`↓; idempotency; log | Wrong state / not found: no consume transition | CONSUMED committed; client may lack confirmation | Yes by reservation/`order_id` | Mid-flight ARRANGED+CAPTURED+**HELD** (consume not done) at TERMINAL → not SUCCESS; see §5 (usually AMBIGUOUS / incomplete package, not F0–F2) |
| **refund** | payment REFUNDED; idempotency; log | Not CAPTURED / not found: no refund | REFUNDED exists | Yes | N/A alone; incomplete if still CAPTURED |
| **release** | reservation RELEASED; `held`↓; idempotency; log | Not HELD: error, no release | RELEASED exists | Yes | Incomplete if still HELD when failure path ended |
| **cancel** | shipment CANCELLED; idempotency; log | Not found / bad state | CANCELLED exists | Yes | Incomplete if still ARRANGED |

### 4.1 TH-208 lost-ID (**Source fact** + **Recommendation**)

- Workflow without id after exhausted retry+recovery → may report `AMBIGUOUS` (D-208).
- Oracle **must** still read by `orderId`: if the create committed, the row is there; classify from entity state.
- Divergence workflow AMBIGUOUS vs oracle `CONFIRMED_FAILURE` / `AMBIGUOUS`+incomplete / `INVALID` is expected and valuable for RQ mismatch analysis.
- **Do not** treat “workflow lacked id” as proof the side effect is unknown when the DB row exists.
- **Do not** treat “no row” as timeout-ambiguity for that resource: under atomic commit, no row ⇒ that create did not persist for this `orderId`.

---

## 5. Consume vs arrange (**Conflict** documented, not resolved by code/protocol change)

| Layer | What it says |
| --- | --- |
| **Protocol** (TH-011 §3.1 diagram; OQ-1) | `HELD → CONSUMED` labeled on “arrange success”; I5 requires CONSUMED+CAPTURED+ARRANGED |
| **Implementation** (**Source fact**) | Variant A: `arrange` then separate `consume` activity (C-101-1); shipping only inserts ARRANGED |

**Interpretation (**Recommendation**):**

- Protocol §2.1 / I5 specify the **required terminal success snapshot**, not a mandate that consume be the same HTTP call as arrange.
- Diagram “arrange success → CONSUMED” is a **process narrative** that the implementation splits into two activities.
- Oracle at TERMINAL cares about **end state**, not activity boundaries.

**Intermediate risk (**Source fact**):** If the run stops after `ARRANGED` + `CAPTURED` but reservation still `HELD` (consume not committed):

- Not `CONFIRMED_SUCCESS` (I5).
- Not clean F0–F2 (`ARRANGED` dangling; often still `HELD`).
- I3 may **pass** (ARRANGED with CAPTURED); I4 may **pass** (CAPTURED+HELD).
- → Under Option C: **`AMBIGUOUS`** (pattern e.g. `OTHER` or `COMPENSATION_INCOMPLETE` if failure/compensation path; or `PARTIAL_SUCCESS_PACKAGE`).

**False classification risk:** Labeling that snapshot `CONFIRMED_SUCCESS` would be wrong; labeling `CONFIRMED_FAILURE` would violate I6. Oracle **must** consider this intermediate terminal snapshot explicitly in tests (S14 below).

Compensation note: D-201 releases only `HELD`, never `CONSUMED`. After consume, failure paths cannot “un-consume” via release — protocol/orchestration assumption; oracle encodes allowed terminals accordingly.

---

## 6. Proposed interface (unchanged proposal)

```text
evaluate(orderId, sku, workflowId?, trialId?) → OracleResult
```

`OracleResult.classification` ∈ four frozen labels; `pattern` carries `COMPENSATION_INCOMPLETE` / `UNKNOWN_PAYMENT` / F0–F2 / SUCCESS / PARTIAL_SUCCESS_PACKAGE / OTHER without adding a fifth top-level label.

---

## 7. Scenario matrix (updated for Option C)

| ID | Scenario | Inv | Pay | Ship | Expected oracle | Basis |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | Full success | CONSUMED | CAPTURED | ARRANGED | `CONFIRMED_SUCCESS` | Protocol I5 |
| S2 | Abort before resources | ∅ | ∅ | ∅ | `CONFIRMED_FAILURE` F0 | Protocol §2.2 |
| S3 | After capture, compensated | RELEASED | REFUNDED | ∅/CANCELLED | `CONFIRMED_FAILURE` F2 | Protocol |
| S4 | After arrange, compensated before consume | RELEASED | REFUNDED | CANCELLED | `CONFIRMED_FAILURE` | Clean I6 |
| S5 | Post-charge abort, clean | RELEASED | REFUNDED | ∅ | `CONFIRMED_FAILURE` | Protocol |
| S6 | Incomplete: CAPTURED remains, HELD | HELD | CAPTURED | ∅ | `AMBIGUOUS` + `COMPENSATION_INCOMPLETE` | Option C; I4 may pass |
| S6b | Incomplete: CAPTURED + RELEASED, no refund | RELEASED | CAPTURED | ∅ | `INVALID` (I4) | Protocol I4 |
| S7 | Incomplete: ARRANGED remains | HELD | CAPTURED | ARRANGED | `AMBIGUOUS` + `COMPENSATION_INCOMPLETE` | Not F0–F2; I3 may pass |
| S8 | F3 DB CAPTURED, observed UNKNOWN; still HELD | HELD | CAPTURED | ∅ | `AMBIGUOUS` + incomplete **or** UNKNOWN path if state were UNKNOWN | **Source fact:** DB is CAPTURED |
| S9 | Payment UNKNOWN at TERMINAL | (any consistent) | UNKNOWN | ∅ | `AMBIGUOUS` + `UNKNOWN_PAYMENT` | D-012 |
| S10 | TH-208 lost id; DB has CAPTURED+HELD | HELD | CAPTURED | ∅ | Same as S6 from DB | Independence from workflow ids |
| S11 | Synthetic log≠entity | prefer entity | | | Classify on entity; flag log warning | Recommendation Q4 |
| S12 | WF SUCCESS, DB wrong | HELD | CAPTURED | ∅ | not SUCCESS | Independence |
| S13 | WF failure, DB clean F2 | RELEASED | REFUNDED | ∅ | `CONFIRMED_FAILURE` | Independence |
| S14 | Stop between arrange & consume | HELD | CAPTURED | ARRANGED | `AMBIGUOUS` (+ partial pattern) | §5 intermediate |

---

## 8. Independence tests

IND-1…6 as before, with IND-4 updated: dangling CAPTURED+HELD → **`AMBIGUOUS` + `COMPENSATION_INCOMPLETE`**, never `CONFIRMED_FAILURE` (and `INVALID` only if I4/other invariants fail).

---

## 9. Answers to Q1–Q7

### Q1 — Multiple reservation rows per `orderId`

| | |
| --- | --- |
| **Recommendation** | Harness MVP: **one reserve attempt per `orderId`**. Classifier: if >1 HELD → I2 `INVALID`; if multiple non-HELD rows, prefer unique HELD if any, else latest `updated_at`, and record evidence |
| **Justification** | Partial unique index only on HELD (**Source fact**); multi-row is out of MVP process |
| **Consequences** | Avoids ambiguous picks; experiments stay single-saga |
| **Owner decision?** | Soft yes if experiments might re-reserve; otherwise harness convention enough |

### Q2 — Incomplete compensation label

| | |
| --- | --- |
| **Recommendation** | **Option C** (§3.2) |
| **Justification** | Preserves I6 / F0–F2 / RQ2_safe_recovery; uses protocol AMBIGUOUS correctly; `pattern` separates incomplete from UNKNOWN |
| **Consequences** | RQ2_ambiguous includes incomplete compensation; INVALID stays for real invariant breaks |
| **Owner decision?** | **Yes — required** before implementation |

### Q3 — TERMINAL after arrange, before consume

| | |
| --- | --- |
| **Recommendation** | `AMBIGUOUS` + `PARTIAL_SUCCESS_PACKAGE` (not SUCCESS, not CONFIRMED_FAILURE) |
| **Justification** | I5 incomplete; I6 blocks CONFIRMED_FAILURE while ARRANGED/HELD remain; I3/I4 may still pass → not automatic INVALID |
| **Consequences** | Forces harness/tests to cover S14; no false success |
| **Owner decision?** | Yes if Owner prefers INVALID for any non-terminal-success with ARRANGED |

### Q4 — Entity vs `operation_log` contradiction

| | |
| --- | --- |
| **Recommendation** | Entity tables **authoritative** for classification; log mismatch → evidence warning / optional harness exclusion `ORACLE_DISAGREEMENT` only if API cross-check disagrees with DB — not silent INVALID solely from log text |
| **Justification** | Protocol: DB-primary oracle; logs are forensics (D-201 ordering tests) |
| **Consequences** | Simpler classifier; logs remain for RQ3/debug |
| **Owner decision?** | Mild — recommend accept without formal D-id |

### Q5 — SP / split

| | |
| --- | --- |
| **Recommendation** | See §10 — **two** implementation slices after design approval, not seven tickets |
| **Justification** | Independent value: (1) classifier+DB fixtures; (2) optional API cross-check later with harness |
| **Consequences** | Fits ~2 SP Taiga if scope = slice 1; slice 2 optional |
| **Owner decision?** | Yes on scope of first approved NEXT_TASK |

### Q6 — Module path

| | |
| --- | --- |
| **Recommendation** | `experiment/oracle` (as TH-011 foreshadowed) |
| **Justification** | Separates RQ oracle from Variant A runtime; same jar usable for A and C |
| **Consequences** | New Gradle module |
| **Owner decision?** | Mild |

### Q7 — Approve analysis / NEXT_TASK

| | |
| --- | --- |
| **Recommendation** | Treat this doc as design input; set `NEXT_TASK` only when Owner approves Q2 + implementation slice |
| **Justification** | Coordination rules: no impl without `approved` |
| **Consequences** | No code until then |
| **Owner decision?** | **Yes — required** |

---

## 10. Minimal implementation split (**Recommendation**)

Do **not** pre-create seven tickets. After Owner approval:

| Slice | Content | Why separate? |
| --- | --- | --- |
| **TH-401-A** (core) | Module + RO JDBC snapshots + invariant/outcome classifier + S1–S14 + IND tests (no Temporal/LLM) | Delivers usable oracle for RQ1/RQ2 |
| **TH-401-B** (optional) | HTTP API cross-check + disagreement handling; JSON schema hook for harness | Needs harness timing; separable acceptance |

Out of scope until separate approval: Experiment Runner, TH-400 faults, Variant C, Option C GET-by-orderId, protocol edits.

---

## 11. Gaps / risks (residual)

| Item | Type |
| --- | --- |
| NEXT_TASK approval to implement TH-401 | Owner process decision (protocol already allows Option C) |
| Preferring Option A would **change** RQ2 invalid vs ambiguous mix vs protocol’s partial-state rule | Owner preference only |
| Protocol diagram vs split consume activity | Conflict (documented §5) |
| Console ID-coupled business check | Anti-pattern for TH-401 |
| No `trialId` in service DB | OK with external archive |
| Payment charge always inserts a state row when fault applies | Absence of row ≠ “charge returned UNKNOWN” |
| Harness TERMINAL / no silent deletes | Assumption until TH-402+ (§13.3) |

---

## 12. References

- `docs/protocol/BUSINESS_INVARIANTS_AND_ORACLE.md`
- `docs/protocol/EXPERIMENTAL_PROTOCOL_V0.md`
- `coordination/DECISION_LOG.md` (D-011, D-012, D-201, D-208, D-016)
- `docs/architecture/LOST_RESPONSE_WITHOUT_ID.md`
- `OrderFulfillmentWorkflowImpl`, `BusinessOutcome`
- Flyway `V1__*_schema.sql` + `*Service.java` operation_log/idempotency

---

## 13. Protocol Compatibility — Final Audit

**Audit date:** 2026-11-02  
**Scope:** Option C vs frozen TH-011 / D-011 / protocol-v0 (D-012). No code or protocol edits.

### Verdict

```text
OPTION_C_COMPATIBLE_WITH_FROZEN_PROTOCOL
```

Option C does **not** require amending the four oracle outcomes, I1–I7, F0–F2, or RQ1/RQ2 formulas. Implementing TH-401 still needs Owner `NEXT_TASK` approval (process), not a formal protocol decision.

---

### 13.1 Evidence for the six required theses

#### (1) Can `AMBIGUOUS` mean a **known** resource state that is neither SUCCESS nor CONFIRMED_FAILURE?

**Yes.**

| Source | Section | Exact wording |
| --- | --- | --- |
| `BUSINESS_INVARIANTS_AND_ORACLE.md` | §2 outcome table | `AMBIGUOUS` — *“Observable state does not allow confirmed success or confirmed failure”* |

The definition is about **inability to confirm** the two positive terminal classes, not about missing/null fields. A fully observed dangling `CAPTURED`/`HELD`/`ARRANGED` snapshot is an “observable state.”

#### (2) May incomplete compensation with I1–I4/I7 still passing be `AMBIGUOUS` (not `INVALID`)?

**Yes — explicitly allowed.**

| Source | Section | Exact wording |
| --- | --- | --- |
| Same doc | §2.3 | *“Partial state not matching F0–F2 or success → `AMBIGUOUS` or `INVALID` **per invariants**”* |
| Same doc | §4 | `INVALID` tied to invariant **Fail** column (I1–I4, I7 illegal combinations). I6 constrains when `CONFIRMED_FAILURE` may be claimed; it is not “dangling ⇒ INVALID” by itself |
| protocol-v0 | §2.3 OQ-3 | UNKNOWN → `AMBIGUOUS`, *“**not** as `INVALID` unless other invariants break”* — same separation of ambiguous vs invariant break |

Therefore: known incomplete compensation that still **passes** I1–I4/I7 → **`AMBIGUOUS`**; if I4 fails (e.g. `CAPTURED`+`RELEASED` without `REFUNDED`) → **`INVALID`**. This is Option C.

#### (3) Does `CONFIRMED_FAILURE` require clean F0–F2 compensation?

**Yes.**

| Source | Section | Exact wording |
| --- | --- | --- |
| Same doc | §2.2 | Acceptable snapshots are **exactly** F0/F1/F2; *“others → `INVALID` or `AMBIGUOUS`”* |
| Same doc | §2.2 Design decision | *“`CONFIRMED_FAILURE` requires **no** `HELD` reservation and **no** `ARRANGED` shipment left dangling…”* |
| Same doc | §4 I6 | *“`CONFIRMED_FAILURE` ⇒ reservation not `HELD` ∧ shipment not `ARRANGED` ∧ payment not left as bare `CAPTURED` without refund”* |
| protocol-v0 | §1.1 | *“Safe unresolved / compensated termination”* = oracle `CONFIRMED_FAILURE` **(clean F0–F2)** |
| protocol-v0 | §1 RQ2 | `RQ2_safe_recovery` = share with `CONFIRMED_FAILURE` **matching F0–F2** |

Option B (incomplete → `CONFIRMED_FAILURE`) is **incompatible** with the frozen protocol. Option C correctly refuses that mapping.

#### (4) How does the protocol define partial state?

| Source | Section | Exact wording |
| --- | --- | --- |
| `BUSINESS_INVARIANTS_AND_ORACLE.md` | §2.3 | *“Partial state not matching F0–F2 or success → `AMBIGUOUS` or `INVALID` per invariants”* |

No separate enum named “partial”; it is any TERMINAL snapshot that is neither the §2.1 success package nor a §2.2 F0–F2 pattern. Disambiguation between `AMBIGUOUS` and `INVALID` is **by invariant pass/fail**.

#### (5) Can ARRANGED + CAPTURED + HELD at TERMINAL be `AMBIGUOUS`?

**Yes (when I1–I4/I7 pass).**

| Check | Result |
| --- | --- |
| §2.1 success? | **No** — requires reservation **`CONSUMED`**, not `HELD` |
| §2.2 F0–F2? | **No** — `ARRANGED` and `HELD` violate cleanliness / F patterns |
| I3 | Pass if payment is `CAPTURED` |
| I4 | Pass if `CAPTURED` with `HELD` |
| §2.3 | → `AMBIGUOUS` or `INVALID` **per invariants** → here **`AMBIGUOUS`** |

#### (6) Does Option C change RQ1/RQ2 counting vs the approved protocol?

**No change to formulas or label meanings.**

| Metric (protocol-v0 §1) | Option C behavior |
| --- | --- |
| `RQ1_completion` | Still only `CONFIRMED_SUCCESS` |
| `RQ1_invalid` | Still only `INVALID` (real I1–I4/I7 breaks) |
| `RQ2_safe_recovery` | Still only `CONFIRMED_FAILURE` matching F0–F2 |
| `RQ2_ambiguous` | Includes UNKNOWN **and** partial/incomplete snapshots labeled `AMBIGUOUS` |

protocol-v0 §1 note: *“Ambiguous at deadline ≠ success and ≠ invariant violation”* — Option C places incomplete-but-invariant-legal snapshots in `AMBIGUOUS`, which matches that note.  
**Contrast:** Option A (always `INVALID` for incomplete) **would** shift counts from `RQ2_ambiguous` into `RQ1_invalid` / non-ambiguous INVALID share and would conflict with §2.3 “per invariants.”

D-011 / D-012 (**Decision log**): four outcomes; DB-primary oracle; UNKNOWN → AMBIGUOUS not INVALID unless invariants break — unchanged by Option C.  
D-201 / D-208: workflow taxonomy (`COMPENSATED_FAILURE` / `AMBIGUOUS` / `COMPENSATION_INCOMPLETE` failure type) remains distinct from oracle labels; Option C only defines the **oracle** side.

**Explanatory `pattern` field:** TH-011 §7.2 freezes `pattern ∈ {SUCCESS, F0, F1, F2, NONE}`. To stay strictly inside that set without a protocol edit, encode incomplete compensation as **`pattern=NONE`** plus evidence (e.g. `compensationIncomplete=true` / reason code). Extra pattern tokens are optional non-normative harness metadata, not required for Option C’s outcome mapping.

---

### 13.2 Technical controls — absence of record

| Requirement | Status | Evidence / assumption |
| --- | --- | --- |
| Oracle reads **primary** PostgreSQL, not a replica | **Met by current infra** | `infra/docker-compose.yml`: single `postgres:16-alpine` service, host `:5433`; no replica service. Apps/JDBC use `localhost:5433` (**Source fact**). D-011: *“primary oracle = local DB”* |
| Evaluate only at **TERMINAL**, after required work settles | **Protocol requirement; harness assumption** | protocol-v0 §2.3: after workflow stop, wait up to `T_observe=30s`, then at `T_run_max` run oracle **once**. TH-011 §4 I4 note: oracle only at harness TERMINAL. Experiment runner not built yet — must enforce this when implemented |
| No row ⇒ no durable create only if TX atomicity holds and no unauthorized deletes | **Partial: code yes; ops assumption** | Services commit entity+idempotency+log atomically (**Source fact**). Tests/harness may `DELETE`/`TRUNCATE` between runs (intentional). Confirmatory runs must not silently delete mid-run; treat unexpected deletes as technical exclusion (protocol-v0 §8.1) |

---

### 13.3 What is still needed from Owner (not protocol amendment)

1. Approve implementation slice TH-401-A as `NEXT_TASK` (coordination).  
2. Optionally prefer Option A anyway — that **would** be a formal protocol/metrics reinterpretation and is **not** recommended given §2.3.  
3. Confirm harness will call oracle only post-TERMINAL against the same primary DB instance.

---

## Document history

| Date | Change |
| --- | --- |
| 2026-11-02 | Initial TH-401 analysis |
| 2026-11-02 | TH-401.1: classification A/B/C, durable ops matrix, consume/arrange, Q1–Q7 answers, minimal split |
| 2026-11-02 | TH-401.2: Protocol Compatibility — Final Audit → `OPTION_C_COMPATIBLE_WITH_FROZEN_PROTOCOL` |
