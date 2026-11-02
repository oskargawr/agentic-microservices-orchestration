# TH-402 — Implementation Readiness Analysis

| Field | Value |
| --- | --- |
| **Status** | Analysis artifact — recommendations **ratified as D-403** (2026-11-02) · historical PROPOSED text below preserved |
| **Date** | 2026-11-02 |
| **Design contract** | `EXPERIMENT_RUNNER_DESIGN.md` rev. 3.1 · **D-402 approved** (design only) |
| **Readiness pack** | **D-403 approved** — see `coordination/DECISION_LOG.md` (canonical) |
| **Implementation** | **NOT authorized** (**D-AUTH** open) |
| **Ticket** | TH-402 · Taiga **#37** (New, 3 SP) |
| **Inspected HEAD** | `main` @ `31256cdc288aa071fa9f873a454afc2a1d91c46a` |
| **Verdict** | **READY WITH CONDITIONS** (at analysis time; pack since Owner-approved) |

> Labels in the body below may still say PROPOSED (historical). **Canonical approved resolutions:** **D-403**.  
> This analysis does **not** amend the approved design contract. **D-403 ≠ implementation authorization.**

---

## 1. Executive summary

The Experiment Runner **design contract is Owner-approved** (D-402: R1 / R6 / R14). Core prerequisites for a **minimal** harness path already exist: Variant A, Independent Oracle (TH-401-A Option C), protocol-v0 timings, and a payment F3 building block (D-016).

**Implementation is still blocked** until Owner (a) sets `NEXT_TASK=approved` and (b) resolves a small set of sequencing / TH-400 definition decisions. Most remaining RQ-R* items have safe MVP defaults and need only explicit Owner ratification (or conscious deferral).

| Ready now | Conditional | Not ready yet |
| --- | --- | --- |
| Lifecycle, dual status, Oracle-once, R6/R14 sequence, file result layout (logical), StateReset pattern, F0+A skeleton | RQ-R2/R3/R7/R8/R10/R11/R13 ratification; env allowlist; FaultController SPI with stub for F0/F3 | Full F1/F2 without TH-400; fair A×C without Variant C; confirmatory *n* (OWN-1); TH-401-B |

**Recommended sequence (PROPOSED):**  
**Variant A (done) → TH-402 phase-1 skeleton (F0 + optional F3 via existing payment header) → TH-400 (headers, canonical F1/F2) → TH-402 phase-2 full matrix → Variant C / TH-303 → pilot → confirmatory.**  
Requires Owner override of literal Taiga #37 “Depends on TH-400” for phase-1 (**RQ-R9**).

**Alternative sound sequence:** TH-400 first → full TH-402 → C — minimizes fault rework; delays first end-to-end harness learning.

**Verdict: READY WITH CONDITIONS** — do not start coding until Owner approves a decision pack (Section 8) and a `NEXT_TASK`.

---

## 2. Project state and sources

### 2.1 Coordination (**Source fact**)

| Artifact | State |
| --- | --- |
| `PROJECT_STATE.md` | D-402 design approved; impl not authorized |
| `NEXT_TASK.md` | `awaiting_owner` — no approved impl task |
| `DECISION_LOG.md` | D-402 **approved** (design only); D-011/012/016/201/202/208 preserved |
| `HANDOFF_TO_CHATGPT.md` | Prior D-402 recording (superseded by this readiness handoff) |

### 2.2 Engineering readiness (**Source fact**)

| Component | State vs Runner |
| --- | --- |
| Variant A + worker + console | Done — Temporal start / reset SQL patterns reusable |
| `:experiment:oracle` | Done — Option C; do not change semantics |
| Payment `X-Thesis-Fault` | Done for F3 (+ charge-fail hints) — incomplete for F1/F2 inventory/shipping |
| `:experiment:runner` / `:experiment:faults` | Directories reserved; **not** in Gradle |
| Variant C / TH-300–304 | Taiga **New** — not started |
| TH-401-B | Deferred — not blocking MVP Runner |

### 2.3 Taiga (**Source fact**)

| Ref | Subject | Status | SP |
| --- | --- | --- | --- |
| **#37** | TH-402 Experiment runner | **New** | **3** |
| **#35** | TH-400 Fault profiles F1–F3 | New | 3 |
| **#36** | TH-401 Business-state oracle | **Done** | 2 |
| **#33** | TH-303 Fairness + invalid tool-call logging | New | 2 |
| **#30–34** | Variant C slice | New | — |

**#37 description** (fetched by id): records D-402 design approved / impl not started; narrative depends on TH-400, TH-401, TH-303.  
**Conflict:** list-API sometimes omits description bodies — treat id fetch + reconciliation report as authoritative for content; status/SP confirmed New / 3.

### 2.4 Obsidian (**Source fact**)

Vault aligned with D-402 on Current Status, Architecture Overview, Decision Log, Experimental Protocol pointer, Implementation Plan. Open Questions lists remaining RQ-R*.  
**SoT reminder:** GitHub = design/impl contract; Obsidian = research knowledge; Taiga = PM status. Recommendations below are **PROPOSED**, not vault “approved”.

### 2.5 Protocol / Oracle constraints (**Approved** — must preserve)

- `T_observe=30s`, `T_run_max=120s` (D-012)
- Four Oracle labels; timeout ≠ failure (D-011)
- R6 terminate; R14 quiesce → full observe → Oracle (D-402)
- Temporal quiescence ≠ service-side drain (D-402 limitation)
- Persist-fail after Oracle: retry; no re-run; no fabricate COMPLETED (D-402)
- Harness triad: `COMPLETED` \| `EXCLUDED` \| `ABORTED_EXPERIMENT`
- protocol F1 wording allows reserve-fail **or** fail immediately after reserve before pay (§4)

---

## 3. Open RQ overview table

| ID | Topic | Blocks coding? | Owner decision needed? | PROPOSED default |
| --- | --- | --- | --- | --- |
| **RQ-R2** | File vs Postgres results | Soft | Ratify | File-first JSON + CSV; Postgres later |
| **RQ-R3** | CLI vs Spring Boot | Soft | Ratify | Library + CLI; HTTP optional later |
| **RQ-R4** | Headers vs Toxiproxy (TH-400) | Soft for F0 skeleton; **hard for F1–F3 matrix** | **Yes** before TH-400 | Service headers (extend D-016) |
| **RQ-R5** | Canonical F1 | Soft for F0; **hard for F1 cells** | **Yes** before F1 confirmatory | Charge-fail after HELD (`CHARGE_FAIL`) |
| **RQ-R7** | Serial vs concurrent | Soft | Ratify | Serial + env lock only (MVP) |
| **RQ-R8** | `amountMinor` protocol constant? | Soft | Ratify | Experiment config; test default 2599 |
| **RQ-R9** | Skeleton before TH-400 | **Sequencing** | **Yes** | Allow phase-1 F0(+F3) before TH-400 |
| **RQ-R10** | Auto-continue after incomplete | Soft | Ratify / defer | Skip incomplete; continue matrix; list archive |
| **RQ-R11** | TH-303 hard dep of #37 | Soft for A-only | Ratify | Soft until Variant C |
| **RQ-R12** | Separate confirmatory DB | Soft for pilot | Prefer yes for confirmatory | Allowlist always; `business_exp` preferred later |
| **RQ-R13** | Quiesce wait | Soft | Ratify config default | Configurable; pilot default **5s** |

---

## 4. Detailed RQ analysis (RQ-R2–R5, R7–R13)

### RQ-R2 — Persistence: file-first JSON vs PostgreSQL

| | |
| --- | --- |
| **Context** | Protocol-v0 §10 specifies `experiment/results/` layout. Design §5 recommends file-first. |
| **Options** | **A** file-first · **B** Postgres `experiment` from day one · **C** dual |
| **Pros A** | Matches protocol; no migrations; thesis archive friendly; atomic temp+rename already specified |
| **Cons A** | Weaker ad-hoc query; concurrent writers need lock discipline |
| **Pros B** | Queryable; dashboard-ready |
| **Cons B** | Migrations + ops before first IT; delays learning; risk of writing into wrong schemas |
| **Methodology** | RQ rates need durable per-trial artifacts + exclusion reasons — files suffice if immutable + schemaVersion |
| **Impl impact** | A unblocks Runner without Flyway for results |
| **Deps** | None hard; dashboard (later) may want B/C |
| **Recommendation (PROPOSED)** | **A**, then optional C when analysis needs SQL |
| **Owner?** | Ratify or override before impl |

### RQ-R3 — CLI vs Spring Boot

| | |
| --- | --- |
| **Context** | Console already covers manual UX; Runner must stay separate. |
| **Options** | **A** library + CLI · **B** Spring Boot HTTP service · **C** both |
| **Pros A** | CI-friendly; clear “batch experiment” semantics; less surface |
| **Cons A** | No remote trigger UI |
| **Pros B** | Ops remote start |
| **Cons B** | Easy to confuse with console; more security/env surface |
| **Methodology** | Confirmatory runs are planned batch jobs — CLI matches |
| **Recommendation (PROPOSED)** | **A** now; HTTP only if Owner later wants remote ops |
| **Owner?** | Ratify |

### RQ-R4 — Fault mechanism: service headers vs Toxiproxy

| | |
| --- | --- |
| **Context** | D-016 payment headers exist. Protocol allows mock **or** Toxiproxy. Taiga #35 title: “mock OR Toxiproxy”. |
| **Options** | **H** headers across inventory/payment/shipping · **T** Toxiproxy network faults · **H+T** hybrid |
| **Pros H** | Deterministic; per-request / per-orderId scope; fair A/C; extends known pattern |
| **Cons H** | Service code changes; not “real network” |
| **Pros T** | Realistic latency/loss |
| **Cons T** | Harder determinism; cross-talk risk; harder per-orderId scoping; ops cost |
| **Methodology** | Fairness requires **same effectiveConfig** for A and C — headers win for MVP RQ1/RQ2 |
| **Impl** | TH-400 delivers FaultController impls; Runner only schedules arm/disarm |
| **Deps** | Blocks full F1–F3 matrix; not F0 skeleton |
| **Recommendation (PROPOSED)** | **H** as MVP mechanism family; Toxiproxy deferred as optional threat-to-validity exploration |
| **Owner?** | **Yes** before TH-400 implementation |

### RQ-R5 — Canonical F1 definition

| | |
| --- | --- |
| **Context** | Protocol §4: fail on reserve **or** immediately after successful reserve before pay. Repo has payment `CHARGE_FAIL` after HELD; inventory fail-on-reserve **absent**. |
| **Options** | **F1-P** charge-fail after HELD · **F1-I** reserve fail · **F1-both** as separate profiles (out of MVP) |
| **Pros F1-P** | Closest existing; exercises compensation after hold; comparable with current Oracle F1 pattern docs |
| **Cons F1-P** | Not literally “inventory fail”; name must be documented honestly |
| **Pros F1-I** | Matches one protocol phrasing literally |
| **Cons F1-I** | New inventory injector; less pay-path compensation coverage |
| **Comparability A/C** | Either OK if **identical** for both variants and frozen in manifest `faultProfileDescriptor` |
| **Recommendation (PROPOSED)** | **F1-P** = canonical MVP F1; document protocol mapping; do not silently rename later |
| **Owner?** | **Yes** before any F1 confirmatory cell |

### RQ-R7 — Serial vs concurrent trials

| | |
| --- | --- |
| **Context** | Shared lab DB + Temporal; reset is destructive; design §8.2 env lock. |
| **Options** | Serial only · Limited concurrency · Full parallel matrix |
| **Methodology** | Concurrency threatens isolation, fault cross-talk, and Oracle snapshot trust |
| **Recommendation (PROPOSED)** | **Serial only** + exclusive env lock for MVP (pilot + confirmatory) |
| **Owner?** | Ratify (strong methodological preference) |

### RQ-R8 — `amountMinor` / currency

| | |
| --- | --- |
| **Context** | D-014/C-101-2: money as amountMinor+currency. Explicit non-decision: 2599 is **not** a protocol constant. Protocol freezes SKU/stock/qty. |
| **Options** | Freeze in protocol · Experiment config · Hardcode in Runner |
| **Methodology** | Must be identical for paired A/C; recorded in ExperimentDefinition |
| **Recommendation (PROPOSED)** | **Experiment config** with documented pilot default `2599` / `PLN`; do not amend protocol-v0 |
| **Owner?** | Ratify |

### RQ-R9 — Minimal F0+A skeleton before TH-400

| | |
| --- | --- |
| **Context** | Taiga #37 depends on TH-400. Design §11 allows optional phase-1 if Owner overrides. |
| **Options** | **Strict:** TH-400 → TH-402 · **Staged:** TH-402-phase1 (F0[+F3]) → TH-400 → TH-402-phase2 · **Parallel** (risky) |
| **Pros staged** | Early validation of lifecycle, R6/R14, Oracle wiring, persistence, reset gate — before inventing F1/F2 |
| **Cons staged** | Temporary FaultController stub; #37 dep annotation needed; risk of mid-flight API churn if SPI poorly frozen |
| **Technical debt** | Acceptable if SPI + trial schema frozen now; only F1/F2 adapters fill later |
| **Recommendation (PROPOSED)** | **Staged** with Owner annotation on #37: “phase-1 allowed without TH-400 Done” |
| **Owner?** | **Yes** (sequencing gate) |

### RQ-R10 — Resume / auto-continue

| | |
| --- | --- |
| **Context** | Design §5.5: never re-execute durable COMPLETED/EXCLUDED; new trialId/orderId for retries. |
| **Options** | Auto-continue matrix · Require operator ack · Stop experiment on first incomplete |
| **MVP need** | Skip incomplete + continue remaining cells is enough; ack UX can wait |
| **Recommendation (PROPOSED)** | Auto-skip incomplete; continue; list in archive; block only on quiesce/disarm/env health failures |
| **Owner?** | Ratify or defer to impl detail under design §5.5 |

### RQ-R11 — TH-303 dependency hardness

| | |
| --- | --- |
| **Context** | TH-303 = fairness + invalid tool-call logging for Variant C / RQ3. #37 acceptance mentions “tools”. |
| **Options** | Hard dep of all TH-402 · Soft until C · Split acceptance |
| **Recommendation (PROPOSED)** | **Soft:** Runner schema reserves tool metric fields (nullable); enforce when C/TH-303 exist. A-only pilot acceptance ≠ full #37 import text |
| **Owner?** | Ratify soft-dep interpretation of #37 |

### RQ-R12 — Separate confirmatory DB

| | |
| --- | --- |
| **Context** | Lab today: Postgres `business` @ `:5433`. Design requires allowlist before destructive reset. |
| **Options** | Same DB + allowlist · Separate DB name/`business_exp` · Separate Compose project |
| **Methodology** | Separate name reduces accidental wipe of ad-hoc console data; allowlist is the hard safety rule |
| **Recommendation (PROPOSED)** | **Allowlist mandatory from first Runner code**; separate confirmatory DB name **preferred** before confirmatory phase (can share `business` for early pilot with explicit env id) |
| **Owner?** | Ratify allowlist now; DB split timing can wait until pre-confirmatory |

### RQ-R13 — Quiesce wait after terminate

| | |
| --- | --- |
| **Context** | D-402 / R14 require Temporal quiescence then full `T_observe`. Quiesce **wait** is not a protocol constant. |
| **Options** | Freeze 5s · Configurable with default 5s · Poll-until-terminal only (0 fixed wait) |
| **Constraint** | Must not change `T_observe`/`T_run_max`. Must not claim service drain. |
| **Recommendation (PROPOSED)** | Configurable `quiesceWaitMs` default **5000**; poll Temporal until terminal or wait elapsed → else `EXCLUDED` / `WORKFLOW_NOT_QUIESCED` |
| **Owner?** | Ratify default (tuning later without protocol bump) |

---

## 5. Implementation sequence variants

### Variant S1 — Staged harness-first (**RECOMMENDED · PROPOSED**)

```text
TH-401-A (done) → TH-402 phase-1 (lifecycle, ResultStore, StateReset, Oracle, Variant A, F0, optional F3 via D-016 adapter)
  → TH-400 (headers; F1-P, F2 shipping, standardize F3 arming)
  → TH-402 phase-2 (full F0–F3 matrix wiring)
  → Variant C (TH-300…) + TH-303
  → Pilot A×C → (OWN-1) confirmatory → dashboard
```

| | |
| --- | --- |
| **Hard deps** | Phase-1: Oracle + Variant A. Phase-2: TH-400. A×C: Variant C. |
| **Soft deps** | TH-303 for tool metrics; TH-401-B optional |
| **Benefits** | Early proof of R6/R14, dual status, persistence, env gate |
| **Risks** | SPI churn if phase-1 freezes poorly; Taiga #37 dep conflict until Owner annotates |
| **Rework** | Low if FaultController + trial schema frozen in phase-1 |

### Variant S2 — Faults-first (matches literal Taiga #37)

```text
TH-400 (complete F1–F3) → TH-402 full → Variant C + TH-303 → pilot → confirmatory
```

| | |
| --- | --- |
| **Benefits** | No stub FaultController; one Runner integration pass |
| **Risks** | Late discovery of harness lifecycle issues; longer calendar before first Oracle-at-TERMINAL automation |
| **Rework** | Minimal on faults; possible larger Runner rewrite if lifecycle assumptions wrong |

### Variant S3 — Variant C before Runner (not recommended)

| | |
| --- | --- |
| **Risks** | C without harness invites console-driven / ad-hoc comparison; fairness controls missing |
| **Verdict** | **Reject** for MVP methodology |

### Comparison

| Criterion | S1 staged | S2 faults-first |
| --- | --- | --- |
| Time-to-first automated F0 trial | Fast | Slow |
| Alignment with Taiga #37 deps | Needs Owner override | Native |
| Fault API stability | Needs SPI freeze | Higher |
| Methodological safety | High if serial+allowlist | High |

**Recommendation (PROPOSED):** adopt **S1**, with Owner decision on RQ-R9.

---

## 6. Recommended realization plan (PROPOSED — not approved)

### Phase 0 — Owner decision pack (this analysis)

Resolve Section 8 checklist; set `NEXT_TASK`.

### Phase 1 — TH-402 skeleton (if RQ-R9 approved)

1. Gradle `:experiment:runner` (library + CLI).  
2. Experiment/trial model + file ResultStore (atomic writes).  
3. StateReset + env allowlist + lock.  
4. VariantAdapter A + Temporal terminate (R6) + quiesce + T_observe (R14).  
5. Oracle once; dual harness/oracle status.  
6. FaultController SPI + F0 no-op + optional F3 payment-header adapter (interim).  
7. Integration test: F0 × A only on allowlisted lab DB.

**Out of phase 1:** F1/F2 production injectors, Variant C, dashboard, Postgres results schema, Toxiproxy.

### Phase 2 — TH-400

Canonical F1-P, F2 shipping/arrange-fail, standardize F3 arming (variant-agnostic), verify/disarm.

### Phase 3 — TH-402 completion

Wire F1–F3; CSV export; pairing strategy; harden resume; prep tool metric nullable fields.

### Phase 4 — Variant C + TH-303

Same FaultController + Runner adapters; fairness logging.

### Phase 5 — Experiments

Pilot → OWN-1 freeze → confirmatory (prefer separate DB name).

---

## 7. Risks — technical and methodological

| Risk | Class | Notes / mitigation |
| --- | --- | --- |
| Post-terminate in-flight HTTP mutates DB after Temporal quiesce | **Methodological limitation** (D-402) · threat to validity | Full T_observe; EXCLUDE if not quiesced; serial trials; document residual; no drain protocol in MVP |
| Unequal A vs C fault wiring | **Owner decision** (RQ-R4) + TH-400 | Shared FaultController; forbid A-only hints in confirmatory |
| Concurrent trials / console contamination | **Blocking if ignored** | Serial + env lock; operator rule |
| Non-deterministic reset / wrong DB | **Blocking if ungated** | Allowlist fail-closed |
| Persist crash after Oracle | **Approved recovery** | In-memory retry; no re-run |
| Incomplete artifacts / resume mistakes | **Impl discipline** | Immutable COMPLETED/EXCLUDED; new trialId |
| F1 definition drift over time | **Owner decision** (RQ-R5) | Freeze descriptor in manifest |
| Quiesce wait too short/long | **Tunable** (RQ-R13) | Config; not protocol constant |
| TH-303 treated as hard gate for all Runner work | **Conflict** | Soft-dep (RQ-R11) |
| 3 SP underestimate for full #37 | **PM** | Phase-1 vs full acceptance; Owner may split tickets later — **not** changing SP in this task |
| Design doc still uncommitted on remote | **Ops** | Owner commit when ready |

**Not blocking implementation start (phase-1):** OWN-1 confirmatory *n*; TH-401-B; Toxiproxy; Postgres results; dashboard; Variant C.

---

## 8. Decisions requiring Owner approval

Before any `NEXT_TASK=approved` for coding, Owner should explicitly decide:

| # | Decision | PROPOSED default |
| --- | --- | --- |
| **D-R9** | Allow TH-402 phase-1 before TH-400 Done? (annotate #37) | **Yes** — staged S1 |
| **D-R4** | TH-400 mechanism family | **Service headers** |
| **D-R5** | Canonical MVP F1 | **Charge-fail after HELD (F1-P)** |
| **D-R2** | Results persistence MVP | **File-first** |
| **D-R3** | Process shape | **Library + CLI** |
| **D-R7** | Concurrency | **Serial + env lock** |
| **D-R8** | amountMinor | **Experiment config** (default 2599 PLN) |
| **D-R11** | TH-303 vs #37 | **Soft** until C |
| **D-R10** | Incomplete cells | **Skip & continue** |
| **D-R12** | Confirmatory DB | **Allowlist now**; separate DB before confirmatory |
| **D-R13** | Quiesce wait default | **5s configurable** |
| **D-SEQ** | Approve sequence S1 vs S2 | **S1** |
| **D-AUTH** | Authorize implementation via `NEXT_TASK=approved` | **Required separately** — not implied by D-402 |

Items D-R2/R3/R7/R8/R10/R13 can be batch-ratified as “accept design defaults”.  
**Must not be silent:** D-R9, D-R4, D-R5, D-SEQ, D-AUTH.

---

## 9. Consciously deferred beyond MVP Runner

| Item | Why defer |
| --- | --- |
| Postgres `experiment` schema | Not needed for protocol archive / first ITs |
| Toxiproxy / network chaos | Determinism cost; optional later study |
| TH-401-B API cross-check | Optional exclusion path; A Oracle sufficient |
| Confirmatory *n* (OWN-1) | TH-501 gate |
| Dashboard / Grafana | After exports exist |
| Variant B / RQ4 | D-009 optional |
| Distributed service drain protocol | Explicitly out of D-402 |
| Concurrent multi-Runner | Methodological hazard |
| F1-I + F1-P as two profiles | Scope creep |
| Operator ack UI for resume | Skip&continue enough |

---

## 10. Implementation readiness checklist

### Contract / methodology

- [x] Design rev. 3.1 Owner-approved (D-402 R1)
- [x] R6 / R14 / quiesce limitation / persist-after-Oracle recorded
- [x] Oracle semantics frozen (TH-401-A)
- [x] Protocol timings frozen
- [ ] Owner decision pack (Section 8) ratified
- [ ] `NEXT_TASK=approved` for a concrete slice

### Engineering prerequisites

- [x] Variant A runnable path
- [x] IndependentOracle API usable
- [x] Reset SQL pattern exists in ITs
- [x] F0 possible without TH-400
- [ ] FaultController SPI agreed (even if stub)
- [ ] Env allowlist policy chosen
- [ ] Module placement (runner CLI) ratified
- [ ] F1/F2 injectors (needed only for full matrix)

### Process / PM

- [ ] RQ-R9 vs Taiga #37 dependency resolved by Owner
- [ ] TH-303 soft-dep accepted or #37 acceptance split
- [ ] No status/SP change claimed as “impl started” without approval

---

## 11. Verdict

### **READY WITH CONDITIONS**

| Condition | Required for |
| --- | --- |
| Owner ratifies decision pack (esp. R9/R4/R5/SEQ) | Any coding |
| Explicit `NEXT_TASK=approved` | Any coding |
| TH-400 Done (or scoped) | Full F0–F3 / confirmatory fairness |
| Variant C + soft TH-303 | A vs C MVP comparison |
| Allowlist (+ preferred separate confirmatory DB) | Safe destructive reset / confirmatory integrity |

**Not READY** for unsupervised full #37 acceptance as originally imported (tools + all faults + C) without TH-400 and Variant C.  
**READY** (conditionally) for a **bounded phase-1** if Owner chooses S1 and approves NEXT_TASK.

---

## Related

- [`EXPERIMENT_RUNNER_DESIGN.md`](EXPERIMENT_RUNNER_DESIGN.md) (rev. 3.1 — **do not amend**)  
- [`../protocol/EXPERIMENTAL_PROTOCOL_V0.md`](../protocol/EXPERIMENTAL_PROTOCOL_V0.md)  
- `coordination/DECISION_LOG.md` (D-402) · `coordination/TH_402_READINESS_HANDOFF.md`
