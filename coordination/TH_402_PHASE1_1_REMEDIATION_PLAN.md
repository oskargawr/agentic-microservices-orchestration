# TH-402 phase-1.1 — Findings remediation plan

| Field | Value |
| --- | --- |
| **Status** | OWN-P1–P4 approved · **D-AUTH-1.1 APPROVED** — remediation implementation authorized |
| **Date** | 2026-11-02 |
| **Basis** | Phase-1 audit findings · design contract rev. 3.1 (unchanged wording) · D-403 · D-AUTH phase-1 (historical) |
| **Impl auth** | **D-AUTH-1.1 = APPROVED** (remediation slice only) |
| **NEXT_TASK** | `approved` — TH-402 phase-1.1 remediation |
| **Branch** | `feature/th-402-phase1-1-remediation` |

> Do **not** silently rewrite `docs/architecture/EXPERIMENT_RUNNER_DESIGN.md` rev. 3.1. OWN-P* clarify interpretation for remediation.

---

## Owner decisions (OWN-P1–P4)

| ID | Decision | Impact on remediation |
| --- | --- | --- |
| **OWN-P1** | Durable-only `COMPLETED`; keep immutable `OracleSummary`; fail → `EXCLUDED`/`PERSISTENCE_FAILURE` + full `.partial`; no re-run / no discard oracle | Finding **B** |
| **OWN-P2** | Retain D0 — `T_run_max` from trial start (setup included) | Finding **D** — **no code change** |
| **OWN-P3** | Minimal experiment-level `ABORTED_EXPERIMENT`; never rewrite terminal trials; separate experiment status | Finding **E** |
| **OWN-P4** | Live Temporal G5 = **pre-pilot**, not merge gate; merge needs unit + required IT incl. race handling | Finding **G** (G5 scope) |

Canonical decision records: `coordination/DECISION_LOG.md` (OWN-P1…P4).

---

## Findings → remediation acceptance

| ID | Severity | Verified | Remediation acceptance (when D-AUTH-1.1 granted) |
| --- | --- | --- | --- |
| **A** | HIGH | Yes | Connection-metadata gate before destructive SQL; fail-closed; URL allowlist retained |
| **B** | MEDIUM | Yes | OWN-P1 persist path; oracle preserved; no fabricate durable COMPLETED |
| **C** | MEDIUM | Yes | Terminate race: re-describe; natural terminal ≠ HARNESS_CRASH; never cancel |
| **D** | — | Compliant | **No work** (OWN-P2) |
| **E** | MEDIUM | Yes | OWN-P3 experiment-level abort semantics |
| **F** | MEDIUM | Yes | Idempotent CSV from JSON SoT (no duplicate rows on retry) |
| **G** | MEDIUM | Yes | Test matrix below |

---

## Test matrix (OWN-P4)

| ID | Test | Merge gate? | Pilot gate? |
| --- | --- | --- | --- |
| G1 | Postgres connection-metadata mismatch rejection | **Yes** | — |
| G2 | StateReset on allowlisted lab DB | **Yes** | — |
| G3 | Cross-process env lock → `ENV_BUSY` | **Yes** | — |
| G4 | Temporal terminate race (unit / mocked) | **Yes** | — |
| G5 | Live Temporal start / terminate / natural / Oracle | **No** | **Yes** |
| G6 | Full `T_observe` after quiesce (unit sufficient for merge) | **Yes** (unit) | Live optional |
| G7 | Oracle exactly-once (unit) | **Yes** | — |
| G8 | Persist failure: no re-run; oracle kept; durable EXCLUDED/PERSISTENCE_FAILURE + `.partial` | **Yes** | — |

---

## Recommended implementation order (after D-AUTH-1.1)

1. A — JDBC connection gate + G1/G2  
2. C — terminate race + G4  
3. B — OWN-P1 persist path + G8  
4. F — CSV rebuild / idempotent export  
5. G3 — lock IT  
6. E — OWN-P3 experiment abort  
7. G5 — live Temporal (pre-pilot, post-merge OK)

**Effort (indicative):** ~2 SP merge pack (A–C, E, F + G1–4, G8); +0.5–1 SP G5 before pilot.

---

## Explicit non-goals until separately authorized

- TH-400 / F1–F3 / Variant C / phase-2  
- Rewriting design contract rev. 3.1 historical text  
- Marking Taiga #37 Done  
- Changing `T_run_max` definition (OWN-P2)

## Phase-1 commits

Preserve as-is (`feature/th-402-phase1-runner-skeleton`: `c862331`, `f2b0744`, `740f831`). Remediation = **new** commits on `feature/th-402-phase1-1-remediation` under **D-AUTH-1.1**.
