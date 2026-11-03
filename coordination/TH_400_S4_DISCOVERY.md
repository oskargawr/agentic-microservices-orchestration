# TH-400 S4 — Discovery & proposal (Variant A closure / matrix readiness)

| Field | Value |
| --- | --- |
| **Status** | **discovery / proposal only** — **NOT approved** · **no D-AUTH-S4** |
| **Date** | 2026-11-03 |
| **Base** | `main` @ merge `bcc38c7` (PR #14 S3) |
| **Does NOT** | Authorize code · mark #35 Done · include Variant C / TH-303 · change Oracle |

> Labels: **Source fact** · **Synthesis** · **Hypothesis** · **Open question** · **Proposal**

---

## 1. Proposed S4 objective (**Proposal**)

Make TH-400 **scientifically usable for Variant A pilot cells** by closing the gap between “profiles implemented in isolation” and “reproducible, comparable F0–F3 experiment matrix with disclosed limitations” — **without** Variant C.

Historical S0 pointer (`TH_400_S0_PLAN.md`): *“S4 — Cross-service verify/disarm / A×C parity polish”*.  
**Synthesis:** A×C parity polish **cannot** be completed without TH-303 / Variant C (explicitly out of MVP mandatory = A vs C later). S4 should therefore **reinterpret** that pointer as: **cross-profile runner matrix readiness + evidence quality for Variant A**, preserving OD-FC so future C reuses the same `FaultController`/`effectiveConfig`.

---

## 2. Problem statement & motivation

### Problem (**Source fact** + **Synthesis**)

S0–S3 delivered canonical header injection for **F0, F1-P, F2, F3-Recovered, F3-Unresolved** on Variant A, with CompIT / unit / mocked-lifecycle coverage. Gaps remain for thesis credibility:

1. **No recorded EXP-* runs** (`EXPERIMENT_LOG` empty) — profiles exist; protocol pilot evidence does not.  
2. **CLI arms one profile per process** (`RunnerMain` switches `f0AndF1P` / `f0AndF2` / `f0AndF3*`); no single Driver for a full matrix experiment definition.  
3. **Live Temporal lab E2E** for F1–F3 **not** performed (S3-E optional; same class for F1/F2). CompIT uses `TestWorkflowEnvironment` + Boot jars + Postgres — strong for semantics, weaker for production Temporal operational claims.  
4. **Retry budgets** verified behaviorally, not quantitatively vs logical limit × Temporal `maxAttempts`.  
5. **Carried engineering limitations** from S1–S2 (probe residue / forge-URL class preflight, CLI not gating on expected Oracle outcome, shipping `noRollbackFor`, etc.) still affect how results must be disclosed.  
6. Nested unrelated `HTTP_503` cause-chain remains a **theoretical residual** (not expanded into a framework).

### Motivation (thesis)

The thesis evaluates reliability of agentic orchestration of distributed processes. For Variant A MVP cells, the reader needs: reproducible fault cells, independent Oracle outcomes, honest limitations, and enough operational evidence that results are not solely unit-mock artifacts.

---

## 3. Scope / out of scope

### In scope (**Proposal** — subject to Owner D-AUTH-S4)

| ID | Slice | Intent |
| --- | --- | --- |
| **S4-A** | Unified multi-profile `FaultController` wiring + experiment matrix driver (Variant A only) | One experiment definition can schedule F0…F3 cells serially under env lock |
| **S4-B** | ResultStore / CLI acceptance polish | Optional exit/reporting gates for expected Oracle class per profile; durable matrix summary |
| **S4-C** | Evidence pack for thesis | Documented limitation register; quantitative retry **measurement plan** or bounded instrumentation tests where cheap |
| **S4-D** | Optional lab live Temporal (**S3-E class**, extendable to F1/F2) | Real cluster smoke for selected cells — **not** assumed mandatory |

### Explicitly out of scope

- Variant C / TH-303 / A×C confirmatory comparison  
- Oracle API / four-label classifier changes  
- Protocol-v0 constant changes (`T_observe`, `T_run_max`)  
- Toxiproxy / F1-I  
- Java cleanup #68  
- Marking Taiga #35/#37 Done  
- Treating CompIT green as live Temporal proof  

### S3-E vs S4 (**Open question** for Owner)

| Option | Meaning |
| --- | --- |
| **A** | S3-E ⊂ S4-D (lab optional slice inside S4) |
| **B** | S3-E separate 1 SP ticket before/after S4 |
| **C** | Defer all live Temporal to TH-402 G5 / pre-pilot |

**Recommendation (Proposal):** **Option A or B** — do **not** equate S4 with “only S3-E”. Matrix driver (S4-A) has higher thesis leverage than a single live F3 run; live Temporal remains valuable disclosure for operational validity.

---

## 4. Implementation slices & SP estimates (**Proposal**)

| Slice | SP (est.) | Notes |
| --- | --- | --- |
| S4-A Matrix driver + unified controller map | **3–5** | Engineering core |
| S4-B CLI / acceptance reporting | **1–2** | Low risk |
| S4-C Thesis evidence / limitation register (+ optional quantitative retry IT) | **2–3** | Mix docs + narrow tests |
| S4-D Live Temporal lab (optional) | **1–2** | Env-dependent; fail-hard CI optional |
| **Total mandatory (A–C)** | **~6–10** | Owner picks ceiling |
| **+ optional D** | **+1–2** | Separate auth flag |

---

## 5. Dependencies on S0–S3 (**Source fact**)

| Dep | Role |
| --- | --- |
| D-404 / fault profile spec | Normative F0–F3 semantics |
| S1–S3 on `main` | Controllers, services, CompIT, harness gates |
| TH-402 phase-1 runner | TrialRunner, ResultStore, env lock, serial trials |
| TH-401 Oracle | Unchanged classifier; F3 harness gate |
| D-R7 serial + env lock | Matrix safety |
| OD-FC | Same effectiveConfig for future C (no C code in S4) |

---

## 6. Acceptance criteria (per slice) (**Proposal**)

### S4-A
- [ ] Single CompositeFaultController (or equivalent) registers F0+F1-P+F2+F3-* concurrently.  
- [ ] ExperimentDefinition can list multiple `faultProfileId`s; runner executes serially with reset between trials.  
- [ ] Each trial persists `ArmedFaultRecord.effectiveConfig` + Oracle summary.  
- [ ] F0 regression still green.

### S4-B
- [ ] Documented expected Oracle class per profile (F0 SUCCESS, F1-P/F2 CONFIRMED_FAILURE, F3-R SUCCESS, F3-U AMBIGUOUS + gate).  
- [ ] CLI or post-run report flags cell mismatch without changing Oracle.  
- [ ] No silent “COMPLETED” when F3-Unresolved gate would have excluded (already present; extend to other profiles if authorized).

### S4-C
- [ ] Written limitation register (S1–S3 carry-forwards + S4) suitable for thesis Methods/Limitations.  
- [ ] Either: (i) quantitative retry assertion for **one** bounded policy, or (ii) explicit Owner-accepted disclosure that budgets remain behavioral-only.  
- [ ] Nested HTTP_503 residual documented as non-blocking theoretical risk (no framework).

### S4-D (if authorized)
- [ ] At least one live Temporal path for a chosen cell (recommend F0 + one fault) with archived artifacts.  
- [ ] Explicitly **not** claimed as full confirmatory matrix.

---

## 7. Required tests (**Proposal**)

| Layer | Tests |
| --- | --- |
| Unit | Multi-profile CompositeFaultController routing; matrix scheduling order; CLI expected-outcome helper |
| Runner lifecycle (mocked) | Multi-profile experiment: arm/verify/disarm isolation across consecutive profiles |
| CompIT / IT | Existing F1–F3 CompIT remain green; optional quantitative retry IT if S4-C chooses instrumentation |
| Lab (S4-D) | Manual or gated IT against real Temporal — not silent-skip |

---

## 8. Live Temporal necessary?

| Question | Answer |
| --- | --- |
| For S4-A/B/C merge? | **No** (Proposal) — CompIT + mocked runner sufficient if limitations disclosed |
| For strong thesis operational claim? | **Yes, recommended** at least once (S4-D or G5 pre-pilot) |
| For TH-400 ticket Done? | **Open question** — Owner must define Done vs “profiles on main” |

---

## 9. Expected experimental evidence & metrics (**Proposal**)

| Evidence | Metric / artifact |
| --- | --- |
| Cell outcomes | Oracle classification × profile × repetition |
| Harness integrity | EXCLUDED reasons; disarm failures; env lock |
| Timing | `t0`, fault arm, workflow stop, oracle (existing schema) |
| Retry (if S4-C) | Attempt counts / exhausted-503 path markers (bounded) |
| Lab (if S4-D) | Temporal workflow IDs, run IDs, commit SHA, protocol id |

**Not claimed without runs:** confirmatory statistics, A×C deltas, LLM reliability claims.

---

## 10. Risks & unresolved research questions

| ID | Item | Type |
| --- | --- | --- |
| RQ-S4-1 | Is TH-400 “Done” when Variant A F0–F3 matrix driver exists, or only after live lab + N pilot reps? | **Open question** |
| RQ-S4-2 | S3-E inside S4 vs separate vs defer to G5 | **Open question** |
| RQ-S4-3 | How much quantitative retry evidence is required for thesis Methods? | **Open question** |
| RQ-S4-4 | CLI Oracle-exit gates: engineering convenience vs protocol coupling | **Open question** |
| R-S4-1 | Preflight forge-URL / probe residue may contaminate lab cells if StateReset skipped | Risk (S1/S2 carry-forward) |
| R-S4-2 | Shipping `noRollbackFor` broadness remains disclosure item | Risk (S2) |
| R-S4-3 | Expanding scope into Variant C under S4 label | Process risk — **forbidden** here |

---

## 11. Required Owner decisions

1. **D-AUTH-S4** scope: which of S4-A…D are in / out.  
2. **S3-E placement** (A/B/C above).  
3. **TH-400 Done criteria** for Taiga #35 (matrix? lab? pilot N?).  
4. Whether quantitative retry work is mandatory or disclosure-only.  
5. Whether S4 may touch CLI exit codes / reporting (no Oracle classifier change).  
6. Explicit **not** authorizing Variant C / #68 / #35 Done via S4 alone.

---

## 12. Suggested Taiga structure (**Proposal**)

Keep epic **#35 TH-400** In Progress. Add child stories (do not Done #35):

| Story | SP | Depends |
| --- | --- | --- |
| TH-400-S4-A Matrix driver | 3–5 | S3 merged |
| TH-400-S4-B Reporting / CLI gates | 1–2 | S4-A |
| TH-400-S4-C Evidence & limitations | 2–3 | S0–S3 |
| TH-400-S4-D Live Temporal lab (optional) | 1–2 | S4-A or standalone |
| (existing) #68 Java cleanup | 5 | Separate D-AUTH — not S4 |

---

## 13. Prioritization matrix (**Synthesis**)

| Slice | Scientific value | Eng. risk | Effort | Thesis importance |
| --- | --- | --- | --- | --- |
| **S4-A** Matrix driver | **High** | Medium | 3–5 SP | **Critical** for comparable cells |
| **S4-C** Evidence / limitations | **High** | Low | 2–3 | **Critical** for honest thesis |
| **S4-D** Live Temporal lab | Medium–High | Medium–High (env) | 1–2 | High for operational validity; optional for merge |
| **S4-B** CLI / reporting | Medium | Low | 1–2 | Medium (operator safety) |
| Pure S3-E alone (no matrix) | Medium | Medium | 1 | Insufficient as sole S4 |
| Variant C in S4 | High long-term | High | large | **Out of scope** (separate TH-303) |

**Recommended order if Owner wants lean S4:** S4-A → S4-C → (optional S4-B) → (optional S4-D).

---

## 14. Gaps (do not invent)

| Gap | Status |
| --- | --- |
| Normative S4 design beyond one-line S0 pointer | **Missing** — this discovery is the first elaboration |
| Owner Done definition for #35 | **Missing** |
| Pilot EXP-* dataset | **Missing** |
| Quantitative retry SoT in protocol | **Missing** (protocol timings unchanged; measurement is engineering add-on) |
| Live F1/F2/F3 Temporal evidence | **Missing** |
| Taiga MCP in this environment | **Unavailable** — status from vault snapshots (#35/#37 In Progress; stale “S3 open”) |

---

## 15. Stop condition

No implementation until Owner sets `NEXT_TASK=approved` with **D-AUTH-S4** (named slices). This file alone is not authorization.
