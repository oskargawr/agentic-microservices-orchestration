# DECISION_LOG

Canonical research narrative also lives in Obsidian  
`Praca magisterska - Orkiestracja agentowa/09 - Project Management/Decision Log.md`.  
**This file** is the handoff-oriented log with explicit `proposed | approved | rejected | superseded` and decision-maker.

**Rule:** Never set status to `approved` without Project Owner explicit instruction.

---

## D-AUTH-S4 — TH-400 S4 (matrix readiness A/B/C) implementation authorization

| Field | Value |
| --- | --- |
| **Date** | 2026-11-03 |
| **Status** | **approved** (Owner D-AUTH-S4); **merge / auto-merge NOT authorized** |
| **Decision-maker** | Project Owner |
| **Context** | Post-S3 merge `bcc38c7` · discovery `TH_400_S4_DISCOVERY.md` |
| **Evidence** | Owner message „TH-400 S4 — Owner Authorization & Implementation” (D-AUTH-S4 APPROVED) |
| **Related** | D-407 · D-404 · D-AUTH-S3 · TH-400 · Taiga #35 · TH-402 |
| **SP** | S4-A…C (est. ~6–10); **S4-D deferred** |

### Authorized

1. **S4-A** — Unified multi-profile `FaultController` wiring + Variant A experiment matrix driver (serial under env lock).  
2. **S4-B** — ResultStore / CLI expected-vs-actual reporting polish (Oracle classifier **unchanged**).  
3. **S4-C** — Thesis evidence / limitation register + **one** bounded quantitative retry measurement.

### Explicitly NOT authorized

- **S4-D** live Temporal lab (deferred)  
- Variant C / TH-303  
- Oracle classifier / API changes  
- Protocol-v0 timing constants (`T_observe`, `T_run_max`) / protocol semantics changes  
- Toxiproxy / F1-I  
- Java cleanup (#68)  
- Taiga #35 / #37 **Done**  
- **Merge / auto-merge** of the resulting PR (push/open PR requires separate auth if not already granted with this task)

### Binding notes

- Owner table shorthand **SUCCESS** for F0 / F3-Recovered means Oracle label **`CONFIRMED_SUCCESS`**.  
- Push/open PR for this implementation branch is in-scope for the authorized workstream; **merge remains separate**.

---

## D-407 — TH-400 S3 (F3) merged to main (status)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-03 |
| **Status** | **recorded** (factual merge status — not a new design decision) |
| **Decision-maker** | Project Owner (merge authorization) |
| **Context** | D-AUTH-S3 granted; PR #14 merged after audit + review remediation |
| **Evidence** | PR [#14](https://github.com/oskargawr/agentic-microservices-orchestration/pull/14) · tip `c3f03e4` · remediation `1a85363` · merge `bcc38c7` · tests on `main`: variant-a 38 / runner 78 / payment 14 |
| **Related** | D-AUTH-S3 · D-404 · OQ-F3U-1/2 · TH-400 · Taiga #35 |
| **Does NOT** | Complete TH-400 · mark #35/#37 Done · authorize S4 / S3-E · change classifier / protocol |

### Limitations carried forward

No live Temporal S3-E · nested unrelated `HTTP_503` cause-chain residual · retry verification behavioral (not quantitative) · earlier S1/S2 carry-forwards (probe residue / forge-URL class · CLI no Oracle-outcome exit gate · F1/F2 live E2E absent).

---

## D-AUTH-S3 — TH-400 S3 (F3-Recovered + F3-Unresolved) implementation authorization

| Field | Value |
| --- | --- |
| **Date** | 2026-11-03 |
| **Status** | **approved** (Owner D-AUTH-S3); **completed via merge** (see D-407) |
| **Decision-maker** | Project Owner |
| **Context** | After S2 merge + `TH_400_S3_READINESS.md` discovery |
| **Evidence** | Owner D-AUTH-S3 impl auth; clean history + push + open PR #14; merge → `bcc38c7` |
| **Related** | D-404 · OQ-F3U-1/2 · D-016 · D-201 · D-R4/R5/R7 · TH-400 · Taiga #35 · PR #14 · D-407 |
| **SP** | **9** (S3-A…D); S3-E live E2E optional **1 SP** separate |

### Authorized (D-AUTH-S3-1…5)

1. Dual profiles `F3-Recovered` + `F3-Unresolved` (no Recovered-only final scope).  
2. Oracle API/classifier **unchanged**; harness acceptance gate for Unresolved (profile + `AMBIGUOUS` + DB CAPTURED/HELD/no arranged).  
3. `RESOLVE_TRANSPORT_OPAQUE` via `X-Thesis-Fault` → HTTP **503**; no payment DB mutation; header-presence (no shared counter).  
4. Propagate fault hint on resolve **and** fallback `getPayment`.  
5. Compensation safety: experimental path must not be defeated by unhinted GET; preserve D-201; Unresolved cell leaves CAPTURED+HELD.

### Authorization boundary (push vs merge)

- Implementation + clean S3-only history + **push** + **open PR**: authorized (Owner).  
- **Merge** required **separate** Owner auth (push ≠ merge); completed → D-407 / PR #14 @ `bcc38c7`.

### Explicitly excluded (unchanged by merge)

Variant C / TH-303 · Oracle changes · protocol semantic rewrite · Java cleanup (#68) · Taiga #35/#37 **Done** · S4 / S3-E without new D-AUTH.

---

## D-406 — TH-400 S2 (F2) merged to main (status)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Status** | **recorded** (factual merge status — not a new design decision) |
| **Decision-maker** | Project Owner (merge authorization) |
| **Context** | D-AUTH-S2 granted; PR #13 merged after audit PASS WITH LIMITATIONS |
| **Evidence** | PR [#13](https://github.com/oskargawr/agentic-microservices-orchestration/pull/13) · tip `17a3f79` · merge `b4ab3fa` |
| **Related** | D-AUTH-S2 · D-404 · OQ-F2-1 · TH-400 · Taiga #35 |
| **Does NOT** | Complete TH-400 · mark #35/#37 Done · authorize S3–S4 · change classifier / protocol |

### Limitations carried forward

Broad `noRollbackFor` on arrange · no injection-off IT · no live Temporal F2 E2E · preflight probe residue / forge-URL class · CLI no `CONFIRMED_FAILURE` exit gate.

---

## D-AUTH-S2 — TH-400 S2 (F2 shipping) implementation authorization

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Status** | **approved** (Owner: „zatwierdzam”); **completed via merge** (see D-406) |
| **Decision-maker** | Project Owner |
| **Context** | TH-400 · Taiga #35 · S2 F2 after S1 merge `c732558` · proposal + feasibility review |
| **Evidence** | Owner D-AUTH-S2; push/PR auth; merge auth → PR #13 @ `b4ab3fa` |
| **Related** | D-404 · OQ-F2-1 · D-405 · D-406 · TH-400 · `TH_400_S2_PROPOSAL.md` · `TH_400_S2_FEASIBILITY_REVIEW.md` |
| **SP** | **5** (narrow allowlist) |

### Authorized

1. `ErrorCode.ARRANGE_REJECTED` + Shipping `X-Thesis-Fault` injection + failure idempotency replay (idempotency **before** injection; `noRollbackFor` so failure rows commit).  
2. Orchestration: `arrangeFaultHint` on input/activities/HTTP/workflow **including recovery**.  
3. Runner: `F2FaultController` + shipping fail-closed preflight (409 + `code=ARRANGE_REJECTED`) + `VariantAAdapter` F2 → arrange hint.  
4. Listed tests + F0/F1-P regression.

### Explicitly excluded

Oracle classifier edits · S0 doc rewrites · F3 · Variant C · Payment production changes · Taiga #35/#37 **Done** · push/PR/merge without separate Owner auth.

### Semantics frozen (OQ-F2-1)

HTTP **409** · BUSINESS · no shipment row · same key+fingerprint failure replay (incl. after disarm) · fingerprint mismatch → `IDEMPOTENCY_KEY_CONFLICT`.

---

## D-405 — TH-400 S1 (F1-P) merged to main (status)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Status** | **recorded** (factual merge status — not a new design decision) |
| **Decision-maker** | Project Owner (merge authorization) |
| **Context** | D-AUTH-S1 granted; PR #12 merged after audit PASS WITH LIMITATIONS |
| **Evidence** | PR [#12](https://github.com/oskargawr/agentic-microservices-orchestration/pull/12) · head `4ed5d57` · merge `c732558` · tests 46/0/0 |
| **Related** | D-404 · D-AUTH-S1 · TH-400 · Taiga #35 |
| **Does NOT** | Complete TH-400 · mark #35/#37 Done · authorize S2–S4 · change OQ-F2-1 / classifier / protocol |

### Limitations carried forward

Probe residue until StateReset · no live preflight↔Payment IT · no live Temporal F1-P E2E · CLI does not gate exit on `CONFIRMED_FAILURE` · expected `OraclePattern.F2` with `CONFIRMED_FAILURE` for F1-P FAILED+RELEASED.

---

## D-404 — TH-400 S0 fault-profile specification (Owner-ratified)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Status** | **approved / Owner-ratified** (documentation complete) · coding **not** authorized |
| **Decision-maker** | Project Owner |
| **Context** | TH-400 · Taiga #35 · S0 fault-profile lock (docs only) |
| **Evidence** | Owner S0 lock · Owner OQ follow-up · **Owner formal ratification** (2026-11-02) — TH-400 S0 Final Ratification & Documentation Sync |
| **Normative spec** | `docs/architecture/TH_400_FAULT_PROFILE_SPEC.md` |
| **Verification** | `docs/architecture/TH_400_S0_TECHNICAL_VERIFICATION.md` |
| **Related** | D-012 · D-016 · D-011 · D-R4 · D-R5 · D-403 · D-402 · D-202 · TH-400 |
| **Does NOT authorize** | Java/service/test implementation · S1–S4 · Variant C · protocol-v0 / rev. 3.1 rewrites · Taiga #35 Done |

### History (preserved)

1. **Directions accepted:** OD-F2 / OD-F3 / OD-FC (S0 lock).  
2. **OQ follow-up proposed then decided:** OQ-F2-1 / OQ-F3U-1 / OQ-F3U-2.  
3. **Formal ratification (this entry):** Owner confirms the OQ outcomes as binding design decisions for TH-400.

### Ratified Owner directions

| ID | Direction |
| --- | --- |
| **OD-F2** | F2 = deterministic **business** failure on Shipping `arrange` **before** shipment creation (no shipment row); A compensates refund(CAPTURED)+release(HELD). |
| **OD-F3** | F3 = two profiles: **F3-Recovered** and **F3-Unresolved**. |
| **OD-FC** | Unified Runner **FaultController** + service-header injection; identical profiles for A and future C. |

### Ratified OQ decisions (2026-11-02)

| ID | Ratified outcome |
| --- | --- |
| **OQ-F2-1** | ErrorCode **`ARRANGE_REJECTED`**; HTTP **409**; non-retryable BUSINESS; atomic idempotent failure replay for same key+fingerprint (incl. after disarm); same key+different fingerprint → **`IDEMPOTENCY_KEY_CONFLICT`**; no shipment row. |
| **OQ-F3U-1** | F3-Unresolved cell Oracle expectation = **`AMBIGUOUS`**. Cell-specific only. Oracle identifies the cell via **independent harness trial/profile metadata** + DB-primary state — **not** workflow outcome, HTTP observations, or orchestrator-supplied classification. Do **not** generalize to all dangling CAPTURED; do **not** change the four-label set. |
| **OQ-F3U-2** | Transport opacity = HTTP **503** with **header-presence** for the armed trial across **all** Temporal Activity retries (no nested-retry burn-through to F3-Recovered). Must not mutate payment DB state or redefine D-016. |

### Preserved constraints

D-R4 · D-R5 · D-016 · D-012 OQ-2 · Oracle four-label **set** unchanged · design rev. 3.1 unchanged.

### Documented technical bindings (S0; impl still needs D-AUTH)

F2 single-TX atomicity (no row + failure idempotency + op-log) · F3-Unresolved header-presence opacity · trial isolation via per-trial headers + serial lock · getPayment must not bypass opacity on this cell · Oracle independence for F3-Unresolved metadata.

### Explicit non-authorization

**D-404 does not authorize TH-400 implementation.** Separate Owner **D-AUTH** + `NEXT_TASK=approved` required before S1–S4 code.

---

## D-402 — Experiment Runner design contract (rev. 3.1)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Status** | **approved** (design contract only) |
| **Decision-maker** | Project Owner |
| **Context** | TH-402 · Taiga #37 · `docs/architecture/EXPERIMENT_RUNNER_DESIGN.md` rev. 3.1 |
| **Evidence** | Owner instruction — Record Owner Approval of Experiment Runner Design rev. 3.1 (2026-11-02) |
| **Related** | TH-402; D-012; D-011; D-201; D-202-4; D-208; TH-401-A Option C |

### Approved (R1 / R6 / R14)

| ID | Decision |
| --- | --- |
| **R1** | Accept rev. 3.1 as the **approved design contract / baseline** for future TH-402 implementation. |
| **R6** | When `T_run_max` is reached and the workflow is still RUNNING: use Temporal **workflow termination**. Do **not** use cancellation as a substitute. Termination is a **harness-control** action, **not** business compensation, and **does not** establish a business outcome. |
| **R14** | After forced termination: establish required Temporal quiescence → wait **full** approved `T_observe = 30s` → then evaluate Independent Oracle. If quiescence cannot be established → `EXCLUDED` (e.g. `WORKFLOW_NOT_QUIESCED`); do not evaluate as a business outcome; block next trial. |

### Approved clarifications (recorded in design §3.5 / §5.5)

1. **Temporal quiescence ≠ service-side drain:** `workflowQuiesced=true` describes Temporal execution state only; it does **not** prove already-dispatched HTTP / service-side effects have drained. No new distributed drain protocol. Insufficient trust → technical exclusion + block next trial.
2. **Forced-path sequence:** `T_run_max` → terminate → required quiescence → full `T_observe` (30s) → Oracle.
3. **Persistence after Oracle:** if Oracle succeeds but persist fails → keep result in memory and retry persist; **do not** re-run the business trial; **do not** reuse the same `trialId` for a new execution; **do not** overwrite an existing artifact with a new execution; if durable persist cannot recover → technical harness failure (do not fabricate COMPLETED).

### Explicit non-authorization

**This approval does NOT authorize implementation** of the Runner, FaultController, persistence, CLI, lifecycle, TH-400, or Variant C. No change to Oracle/protocol semantics beyond recording the R14 observe sequence. Separate Owner `NEXT_TASK=approved` required before coding.

**Follow-up:** Readiness RQ pack and sequence S1 were Owner-approved later the same day as **D-403** (still without implementation authorization / D-AUTH).

---

## D-403 — TH-402 readiness decision pack + sequence S1

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Status** | **approved** (readiness / sequencing only) |
| **Decision-maker** | Project Owner |
| **Context** | Owner ratification of recommendations in `docs/architecture/TH_402_IMPLEMENTATION_READINESS.md` |
| **Evidence** | Owner instruction — Record Owner-Approved Readiness Decisions (2026-11-02) |
| **Related** | D-402; TH-402; TH-400; TH-303; D-016; D-011; D-012 |
| **Does NOT include** | Originally excluded D-AUTH; later granted as **D-AUTH** for phase-1 only (see below) |

### Approved resolutions (D-R* / D-SEQ)

| ID | Approved resolution |
| --- | --- |
| **D-R2** | File-first JSON + CSV for MVP. PostgreSQL results storage deferred. |
| **D-R3** | Runner as reusable library + CLI. No Spring Boot HTTP service in MVP. |
| **D-R4** | Service-header fault injection as the MVP mechanism family. Extend D-016. Toxiproxy deferred. |
| **D-R5** | Canonical MVP F1 = charge failure after successful HELD reservation (**F1-P**). Explicitly **not** an inventory reserve failure. |
| **D-R7** | Sequential trials only, protected by an exclusive environment lock. |
| **D-R8** | `amountMinor` and currency are experiment configuration, not protocol constants. Pilot default: `2599` / `PLN`. |
| **D-R9** | Allow TH-402 **phase-1** before TH-400 is Done — explicit Owner-approved staged-work exception to the prior Taiga #37 dependency narrative for phase-1 only. |
| **D-R10** | Skip incomplete cells and continue the matrix, except when safety / env health / disarm / quiescence conditions require blocking. |
| **D-R11** | TH-303 is a **soft** dependency for the A-only phase. Full A×C acceptance still requires Variant C and relevant TH-303 capabilities. |
| **D-R12** | Fail-closed environment/database allowlist is **mandatory** from the first Runner implementation. Separate confirmatory DB is preferred and **required before confirmatory** execution. |
| **D-R13** | Configurable quiescence wait; pilot default **5000 ms**. Not a protocol constant; does **not** change `T_observe` or `T_run_max`. |
| **D-SEQ** | Adopt staged sequence **S1**. |

### Approved sequence S1

1. **TH-402 phase-1** — bounded F0 × Variant A Runner skeleton (lifecycle, file results, StateReset, allowlist, env lock, Variant A adapter, Oracle once, R6/R14, F0 FaultController). Optional F3 via D-016 is **not** mandatory phase-1 acceptance.  
2. **TH-400** — service-header faults; canonical F1-P, F2, F3.  
3. **TH-402 phase-2** — full F0–F3 matrix integration.  
4. **Variant C + TH-303** — agent variant + fairness/tool instrumentation.  
5. **Pilot A×C**, then confirmatory after separate confirmatory env + OWN-1.

### Acceptance boundaries (mandatory)

| Level | Meaning |
| --- | --- |
| **Phase-1 acceptance** | Bounded F0×A path works with allowlist, lock, reset, lifecycle, R6/R14, Oracle, durable persistence (+ approved persist-failure recovery), reproducible IT evidence. **≠** full TH-402 Done. |
| **Full TH-402 acceptance** | Requires TH-400, F0–F3 matrix, design-required Runner functions, and Variant C / TH-303 for full A×C comparison acceptance. |

### Preserved constraints (unchanged)

D-402 / D-016 / D-011 / D-012 / D-201 / D-202 / D-208 meanings preserved: `T_observe=30s`, `T_run_max=120s`, R6 terminate, R14 quiesce→observe→Oracle, quiescence ≠ service drain, timeout ≠ failure, Oracle four labels, no Runner compensation APIs, no refund without CAPTURED, no release without HELD, no fabricated COMPLETED after persist failure.

---

## D-AUTH — TH-402 phase-1 implementation authorization

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Status** | **approved** (exclusive slice) |
| **Decision-maker** | Project Owner |
| **Authorized slice** | **TH-402 phase-1** — bounded F0 × Variant A Runner skeleton |
| **Evidence** | Owner instruction — TH-402 phase-1 Owner Authorization & Implementation (2026-11-02) |
| **Basis** | D-402 · D-403 · design contract rev. 3.1 · D-SEQ = S1 |
| **Related** | TH-402 · Taiga #37 |

### Scope (authorized)

Library + CLI Runner; trial lifecycle statuses COMPLETED/EXCLUDED/ABORTED_EXPERIMENT; serial + env lock; file-first JSON+CSV ResultStore; StateReset + fail-closed DB allowlist; Variant A Temporal adapter; Independent Oracle once after R6/R14 sequence; FaultController with **F0 no-op**; quiesce wait default 5000 ms (configurable).

### Explicitly NOT authorized by D-AUTH

TH-400 (F1-P/F2/F3), mandatory F3, Variant C, TH-303, full F0–F3 matrix, Postgres results, Spring Boot HTTP Runner, Toxiproxy, confirmatory env/runs, OWN-1, dashboard, protocol/design-contract changes, saga/business-contract changes.

### Acceptance note

Phase-1 completion ≠ full TH-402 Done; do **not** mark Taiga #37 Done solely for phase-1.

---

## OWN-P1 … OWN-P4 — TH-402 phase-1.1 Owner clarifications

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Status** | **approved** (clarifications only) |
| **Decision-maker** | Project Owner |
| **Context** | Resolves open questions from the TH-402 phase-1.1 findings-resolution plan (post phase-1 audit) |
| **Evidence** | Owner instruction — Record Owner Decisions OWN-P1–P4 (2026-11-02) |
| **Related** | D-402 · D-403 · D-AUTH · TH-402 · Taiga #37 |
| **Does NOT include** | Originally excluded D-AUTH-1.1; later granted as **D-AUTH-1.1** (see below) |

### OWN-P1 — Persistence semantics (**approved**)

**Durable-only `COMPLETED`.**

- `OracleSummary` is **immutable** once computed.
- `harnessStatus=COMPLETED` requires **successful durable JSON** persistence.
- If persistence retries are exhausted: preserve `OracleSummary`; durable result = `EXCLUDED` / `PERSISTENCE_FAILURE`.
- Preserve a complete `.partial` artifact containing the Oracle-confirmed candidate result.
- Never re-run the workflow or Oracle because of persistence failure.
- Never discard the computed Oracle result.

*Reconciliation note:* Clarifies the D-402 §5.5 / §9.2 tension (“prefer COMPLETED after retry” vs “do not fabricate COMPLETED”) without rewriting historical design-contract wording. Durable COMPLETED only after successful JSON write; otherwise durable EXCLUDED + oracle + `.partial`.

### OWN-P2 — `T_run_max` boundary (**approved**)

**Retain D0.** Deadline starts at **trial start** (`t0`); setup/reset time is included. **No** code or protocol changes required for this item.

### OWN-P3 — `ABORTED_EXPERIMENT` (**approved**)

Implement **minimal experiment-level** semantics in phase-1.1:

- `EXCLUDED` = individual trial.
- `ABORTED_EXPERIMENT` = experiment as a whole.
- Operator interruption or shutdown may produce `ABORTED_EXPERIMENT`.
- Never rewrite an already terminal `COMPLETED` or `EXCLUDED` trial as `ABORTED_EXPERIMENT`.
- Persist experiment-level status **separately** from trial status.
- Do **not** introduce new enum values.

### OWN-P4 — Live Temporal integration (**approved**)

- **G5** (real Temporal worker/service IT: start / terminate / natural completion / Oracle) = **pre-pilot gate**, **not** a merge gate.
- **Before merge:** unit tests + required ITs, including Temporal **race handling** (unit/mocked path).
- **Before any actual pilot:** real Temporal integration covering start, terminate, natural completion, and Oracle.

### Authorization state after OWN-P*

| Item | Status |
| --- | --- |
| D-403 | APPROVED |
| D-AUTH (phase-1) | APPROVED (historical; skeleton commits untouched) |
| OWN-P1–P4 | **APPROVED** |
| **D-AUTH-1.1** | **APPROVED** (see below) |

Plan SoT: `coordination/TH_402_PHASE1_1_REMEDIATION_PLAN.md`.

---

## D-AUTH-1.1 — TH-402 phase-1.1 remediation authorization

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Status** | **approved** (exclusive remediation slice) |
| **Decision-maker** | Project Owner |
| **Authorized slice** | **TH-402 phase-1.1** — findings remediation A/B/C/E/F + merge-gate tests G1–G4, G6–G8 |
| **Evidence** | Owner instruction — D-AUTH-1.1 IMPLEMENTATION AUTHORIZATION (2026-11-02) |
| **Basis** | OWN-P1–P4 · `TH_402_PHASE1_1_REMEDIATION_PLAN.md` · D-402 · D-403 · D-AUTH phase-1 (historical) |
| **Related** | TH-402 · Taiga #37 |

### Scope (authorized)

A JDBC connection-metadata gate + G1/G2; C Temporal terminate race + G4; B OWN-P1 persistence + G8; F idempotent CSV; G3 env-lock IT; E OWN-P3 experiment-level `ABORTED_EXPERIMENT`. Merge-gate tests G1–G4, G6–G8.

### Explicitly NOT authorized

G5 live Temporal (pre-pilot only) · TH-400 / F1–F3 · Variant C · phase-2 · rewrite design contract rev. 3.1 · change `T_run_max` (OWN-P2) · mark #37 Done · merge/push/PR without separate Owner approval · rewrite phase-1 commits `c862331`/`f2b0744`/`740f831`.

---

## D-208 — Lost-response-without-returned-ID recovery (B+E)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 (proposed) · **2026-11-02 (Owner approved)** |
| **Status** | **approved** |
| **Decision-maker** | Project Owner |
| **Context** | Closes D-202-3 deferral; investigation `docs/architecture/LOST_RESPONSE_WITHOUT_ID.md` |
| **Evidence** | Owner Authorization — D-208 and TH-208 Implementation (2026-11-02) |
| **Decision** | Adopt **Option B** (bounded idempotent re-POST recovery per missing create id before compensation) + **Option E** (honest terminal labeling). **Defer Option C** (GET-by-orderId) to a separate future task. **Reject** Options D and F for MVP. |
| **Outcome taxonomy (mandatory)** | `AMBIGUOUS` — authoritative state cannot be established / uncertain whether side effect occurred; `COMPENSATION_INCOMPLETE` — resource authoritatively known and requires compensation that could not be completed/confirmed; `COMPENSATED_FAILURE` — all required compensations authoritatively confirmed. Never `COMPENSATED_FAILURE` if CAPTURED/HELD/ARRANGED may remain without confirmed compensation. Never classify UNKNOWN as FAILED. |
| **Non-goals** | No D-201 order change; no protocol-v0 / F3 definition change; no GET-by-orderId in TH-208; no TH-203/204/400 in this task |
| **Related** | D-202-3; D-201; TH-208; TH-400 (later); TH-401 |

---

## D-202 — TH-202 retries/timeouts implementation decisions (Owner pack)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Status** | **approved** |
| **Decision-maker** | Project Owner |
| **Context** | Owner reviewed `TH-202_INVESTIGATION.md` and locked implementation scope |
| **Evidence** | Owner Decision — TH-202 Implementation Approved (2026-11-02) |
| **Related** | TH-202; D-201; D-012 (`T_observe`/`T_run_max`); protocol-v0 unchanged |

### D-202-1 — ActivityOptions

Keep **all existing** numeric `ActivityPolicies` values unchanged (forward 10s/60s; compensation 10s/90s; max attempts 5; initial 200ms; max interval 2s; backoff 2.0; non-retryable `BUSINESS` + `COMPENSATION_INCOMPLETE`). These are **engineering defaults**, not values derived from experimental protocol constants.

### D-202-2 — Transport failure classification

Do **not** introduce transport-failure wrapping or change exception classification in TH-202. Document current behavior; distinguish verified facts from assumptions.

### D-202-3 — Lost response without returned ID

Defer lost-response-after-commit-without-returned-ID to a separate F3-related task. No orderId-based payment discovery, new lookup endpoints, or D-201 semantic changes in TH-202. Record as an explicit reliability limitation.

### D-202-4 — Workflow execution timeout

Do **not** add an explicit workflow execution timeout in TH-202. Keep `T_observe=30s` and `T_run_max=120s` as protocol/harness limits only — do not conflate with Temporal `ActivityOptions`.

### D-202-5 — Documentation

Create dedicated `docs/architecture/VARIANT_A_RETRIES_TIMEOUTS.md`; keep `VARIANT_A_HAPPY_PATH.md` as process overview with a short reference.

---

## D-201 — Variant A saga compensation policy pack (TH-201)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-01 (proposed) · **2026-11-02 (Owner approved)** |
| **Status** | **approved** |
| **Decision-maker** | Project Owner |
| **Context** | TH-201 saga compensation; Owner approved U-201-1…U-201-5 with clarifications |
| **Decision** | Adopt the approved pack below for Variant A compensation implementation |
| **Evidence** | Owner instruction 2026-11-02 (“Owner decision: APPROVED”); prior brief 2026-11-01; TH-011 §6; `CompensationOrder`; `TH-201_IMPLEMENTATION_PLAN.md` |
| **Consequences** | `NEXT_TASK` TH-201 = `approved`; implementation may proceed after coordination handoff review; TH-206 remains Proposed |
| **Related** | TH-201; TH-011; TH-400 (fault profiles); protocol-v0 unchanged |

### Approved pack (U-201-1…U-201-5 preserved)

| ID | Decision |
| --- | --- |
| **U-201-1** | After consume failure: full compensation cancel → refund CAPTURED → release HELD |
| **U-201-2** | Bounded payment resolve; if still UNKNOWN: no arrange, no refund, release HELD if safe, business outcome AMBIGUOUS; never treat UNKNOWN as FAILED |
| **U-201-3** | One deterministic Temporal workflow; saga try/catch; separate compensation activities; compensation executable if main cancellation scope is cancelled |
| **U-201-4** | Service-level fault tests in TH-201; full F0–F3 profiles remain TH-400 |
| **U-201-5** | Structured outcomes SUCCESS / COMPENSATED_FAILURE / AMBIGUOUS; unhandled technical errors or incomplete compensation must not be reported as COMPENSATED_FAILURE |

### Owner clarifications (2026-11-02, approved with D-201)

- Unresolved UNKNOWN payment remains **AMBIGUOUS** even if the HELD inventory reservation is safely released.
- Releasing inventory does **not** establish that payment was not captured.
- Never refund unless **CAPTURED** is confirmed.
- Never report **COMPENSATED_FAILURE** if any required compensation activity failed or remained unresolved.
- Unhandled technical errors and incomplete compensation must remain **distinguishable** from business-level compensated failure.
- Tests must verify **`operation_log` ordering** in addition to final database states.

**Explicit non-decisions:** no new invariants; oracle unchanged; `amountMinor=2599` not a protocol constant; TH-205 unmodified; TH-206 remains Proposed/unapproved.

---

## D-COORD-001 — Three-party file-based coordination

| Field | Value |
| --- | --- |
| **Date** | 2026-10-22 |
| **Status** | approved |
| **Decision-maker** | Project Owner (task request to set up workflow) |
| **Context** | Need reliable handoffs among Owner, ChatGPT advisor, Cursor executor without custom APIs |
| **Alternatives** | Custom bot/API; ChatGPT-only; Cursor-only notes |
| **Recommendation** | Markdown under `coordination/` + existing Git when available |
| **Decision** | Adopt `coordination/` files and protocol in README |
| **Evidence** | Owner brief “Set Up a Three-Party Thesis Workflow” |
| **Consequences** | Cursor starts/ends substantial thesis work via these files; no auto-approval of advisor advice |
| **Related** | Operating Protocol (vault); AGENTS.md |

---

## D-009 — MVP-first scope: A vs C mandatory

| Field | Value |
| --- | --- |
| **Date** | 2026-10-22 (proposed) · **2026-10-23 (Owner approved)** |
| **Status** | **approved** (Owner) |
| **Decision-maker** | Project Owner |
| **Supervisor** | Expressed support for the **overall topic and research idea**; **no objections** raised. This is **not** documented as explicit approval of every methodological detail (metrics, fault set, model choice, n, etc.). |
| **Context** | Lit. review showed B/RQ4 inflate risk; fair comparison needs shared services + end-state oracle |
| **Alternatives** | Full A/B/C mandatory; A vs B only; A vs C |
| **Recommendation** | Mandatory A vs C; B and RQ4 OPTIONAL after MVP Complete |
| **Decision** | **Proceed with mandatory MVP = A vs C**; Variant B and RQ4 remain OPTIONAL; OUT_OF_SCOPE list retained |
| **Evidence** | Owner instruction 2026-10-23 (“Supervisor Alignment… proceed with A vs C”); prior lit. synthesis 2026-10-22; vault meeting note |
| **Consequences** | No CORE tickets for B/RQ4; MVP gate excludes them; unblocks repo bootstrap and TH-011+ |
| **Related RQ / tickets** | RQ1–3; TH-010 (completed); Backlog CORE/OPTIONAL; TH-011 next |

---

## D-008 — Research question priority

| Field | Value |
| --- | --- |
| **Date** | 2026-10-22 (proposed) · **2026-10-23 (Owner approved, aligned with D-009)** |
| **Status** | **approved** (Owner) |
| **Decision-maker** | Project Owner |
| **Supervisor** | Topic/idea support only — not a line-by-line RQ ratification |
| **Context** | Four RQs not equally feasible in two semesters |
| **Recommendation** | RQ1+RQ2 main; RQ3 secondary metrics; RQ4 exploratory/optional |
| **Decision** | RQ1+RQ2 primary; RQ3 secondary; RQ4 optional / out of mandatory MVP |
| **Related** | D-009; TH-010; Research Questions.md |

---

## D-016 — TH-103 Payment F3 fault-injection header (test-gated)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-01 |
| **Status** | **approved** (Owner TH-103 brief) |
| **Decision-maker** | Project Owner |
| **Decision** | Deterministic F3 via `X-Thesis-Fault: F3_CAPTURE_LOST_RESPONSE` when `thesis.payment.fault-injection-enabled=true`: persist CAPTURED, return UNKNOWN; resolve/GET expose capture; independent of A/C |
| **Related** | TH-103 · protocol-v0 F3 · D-012 |

---

## D-015 — TH-101.1 contract hardening (idempotency, resolve, OpenAPI split)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-01 |
| **Status** | **approved** (Owner TH-101.1 brief) |
| **Decision-maker** | Project Owner |
| **Context** | Harden contracts before TH-102 |
| **Decision** | Header-only `Idempotency-Key` (same key+payload replay; conflict → 409); payment resolve rules (inconclusive keeps UNKNOWN/PENDING); per-service OpenAPI + shared `components.yaml`; stable `ErrorCode`s; charge amount must be strictly positive |
| **Confirmed** | C-101-1, C-101-2, C-101-3 (see D-014) |
| **Related** | TH-101.1 · TH-102 next |

---

## D-014 — TH-101 shared domain contracts

| Field | Value |
| --- | --- |
| **Date** | 2026-11-01 |
| **Status** | **approved** (Owner tasked TH-101 execution; C-101-1..3 confirmed 2026-11-01) |
| **Decision-maker** | Project Owner |
| **Context** | Lock identical API/state surface for A and C before service implementation |
| **Decision** | Add Gradle module `:contracts` (DTOs, enums, transition guards, idempotency helpers) + OpenAPI + state diagrams; services depend on module; **no** HTTP business endpoints, persistence, Temporal, LLM, or runner in this ticket |
| **Confirmed (Owner)** | **C-101-1** orchestration calls inventory `consume` after confirmed `arrange`; **C-101-2** money as `amountMinor`+currency; **C-101-3** HTTP prefix `/api/v1` |
| **Evidence** | `:contracts` tests green; `./gradlew build` SUCCESS; branch `feature/th-101-domain-contracts` (+ TH-101.1 hardening) |
| **Related** | TH-101 · TH-101.1 · TH-011 · TH-012 · next TH-102..104 |

---

## D-013 — TH-100 infra bootstrap (Postgres schemas + Temporal server)

| Field | Value |
| --- | --- |
| **Date** | 2026-11-01 |
| **Status** | **approved** (Owner brief) |
| **Decision-maker** | Project Owner |
| **Context** | Reconcile backlog TH-100 (monorepo + Temporal local + build) with Owner request (Postgres schema-per-service + build/run; no domain/workflows/LLM/runner) |
| **Decision** | Extend existing `agentic-orchestration-thesis/` Git scaffold; Gradle multi-module stubs; Compose Postgres on **5433** with schemas `inventory`/`payment`/`shipping`; Temporal auto-setup on **7233**; no business APIs or workflows |
| **Also confirmed** | OWN-3 CONSUMED; OWN-4 timing provisional; F3 mandatory (doc); A-vs-C MVP unchanged |
| **Evidence** | `./gradlew build`; `scripts/verify-infra.sh`; inventory `/actuator/health` UP with DB |
| **Related** | TH-100 · TH-101+ next for contracts/services |

---

## D-012 — Experimental protocol v0 + OQ-1..3 resolutions (TH-012)

| Field | Value |
| --- | --- |
| **Date** | 2026-10-24 |
| **Status** | **approved** (Owner tasked TH-012 execution; design draft freeze pending OWN-3/4 veto) |
| **Decision-maker** | Project Owner (task) · Cursor documented |
| **OQ-1** | Success path: reservation `HELD` → `CONSUMED` on arrange; stock `onHand`/`held` decremented |
| **OQ-2** | Fault F3 ambiguous payment mandatory; timeout ≠ failure; **no refund unless CAPTURED confirmed** |
| **OQ-3** | `T_observe=30s`, `T_run_max=120s`; unresolved UNKNOWN → oracle `AMBIGUOUS` (RQ2 ambiguous, not INVALID) |
| **DB** | PostgreSQL planned for local services |
| **Evidence** | `docs/protocol/EXPERIMENTAL_PROTOCOL_V0.md` + amended invariants doc |
| **Open for Owner** | Confirmatory *n* (OWN-1); model (OWN-2); veto windows on OWN-3/4 |
| **Related** | TH-012 done · TH-400 · TH-501 · RQ1–RQ3 |

---

## D-011 — Business invariants + DB-primary oracle (TH-011)

| Field | Value |
| --- | --- |
| **Date** | 2026-10-24 |
| **Status** | **approved** (Owner executed TH-011 task; design freeze for drafting) |
| **Decision-maker** | Project Owner (task execution) · Cursor documented |
| **Context** | Need shared business correctness for fair A vs C; LLM text must not define success |
| **Alternatives** | API-only oracle; DB-only; hybrid; treat timeout as failure |
| **Decision** | Minimal order process (reserve→pay→ship); outcomes `CONFIRMED_SUCCESS` / `CONFIRMED_FAILURE` / `AMBIGUOUS` / `INVALID`; invariants I1–I7; timeout ≠ failure; payment `UNKNOWN` explicit; **primary oracle = local DB**, optional API cross-check |
| **Evidence** | Spec `agentic-orchestration-thesis/docs/protocol/BUSINESS_INVARIANTS_AND_ORACLE.md` |
| **Open** | OQ-1..OQ-3 in spec (consume-on-ship; UNKNOWN in fault set; harness wait T) |
| **Related** | TH-011 done · TH-012 · TH-401 · RQ1–RQ2 |

---

## D-010 — Thesis application monorepo root

| Field | Value |
| --- | --- |
| **Date** | 2026-10-23 |
| **Status** | **approved** |
| **Decision-maker** | Project Owner (bootstrap task) · Cursor executed |
| **Context** | Workspace `cursor_space` mixes ZPR materials, Obsidian vault, Taiga infra — unsuitable as a single app Git root |
| **Alternatives** | Git at `cursor_space/`; nested app under `infra/`; separate directory |
| **Decision** | Initialize Git monorepo at `agentic-orchestration-thesis/` (sibling to `coordination/`, `infra/`, vault) |
| **Evidence** | Ambiguity rule in Owner bootstrap brief; inspection of nested contents |
| **Consequences** | Application code/history lives only under that tree; vault/Taiga/coordination stay outside app repo |
| **Related** | TH-100 (Temporal/services still future); TH-010 complete |

---

## Earlier decisions (mirrored; see vault for narrative)

| ID | Summary | Status | Decision-maker |
| --- | --- | --- | --- |
| D-001 | Working title PL/EN unchanged | proposed → treat as accepted for now (supervisor liked topic) | Owner / supervisor topic support |
| D-002 | Knowledge base in Obsidian vault beside ZPR | approved | Owner (setup brief) |
| D-003 | No full app in initial setup session | approved / satisfied | Owner (setup brief) |
| D-004 | Candidate A/B/C with possible narrowing | superseded by D-009 | — |
| D-005 | Prefer Java / Spring Boot / Temporal / local LLM | **proposed** (stack not locked by this session) | — |
| D-006 | Citation style after university requirements | approved | Owner (setup brief) |
| D-007 | Operating Protocol for agent sessions | approved | Owner (protocol brief) |

---

## Template for new entries

```markdown
## D-XXX — Title

| Field | Value |
| --- | --- |
| Date | YYYY-MM-DD |
| Status | proposed \| approved \| rejected \| superseded |
| Decision-maker | Owner / — |
| Context | |
| Alternatives | |
| Recommendation | |
| Decision | |
| Rationale / evidence | |
| Consequences | |
| Related RQ / tickets | |
```
