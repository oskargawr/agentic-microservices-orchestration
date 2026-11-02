# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** DOCUMENTATION / COORDINATION ONLY  
**Task:** Record Owner decisions OWN-P1–P4 + phase-1.1 remediation plan  
**Authorization:** D-403 APPROVED · D-AUTH phase-1 APPROVED (historical) · OWN-P1–P4 **APPROVED** · **D-AUTH-1.1 NOT APPROVED**

---

## Summary

Owner resolved audit open questions OWN-P1–P4. Decisions recorded in Git coordination + Obsidian. Remediation plan updated with acceptance criteria. **No code, no commits, no push/PR, no Taiga changes.** Phase-1 implementation commits untouched. `NEXT_TASK=awaiting_owner` for phase-1.1.

## OWN-P* (approved)

| ID | Decision |
| --- | --- |
| **OWN-P1** | Durable-only `COMPLETED`; immutable OracleSummary; fail → EXCLUDED/PERSISTENCE_FAILURE + `.partial`; no re-run / no discard oracle |
| **OWN-P2** | Retain D0 — `T_run_max` from trial start (setup included); no code/protocol change |
| **OWN-P3** | Minimal experiment-level `ABORTED_EXPERIMENT`; never rewrite terminal trials; separate experiment status |
| **OWN-P4** | G5 live Temporal = pre-pilot gate, not merge gate; merge needs unit + required IT incl. race handling |

## Authorization state

| Item | Status |
| --- | --- |
| D-403 | APPROVED |
| D-AUTH (phase-1) | APPROVED (skeleton only; historical) |
| OWN-P1–P4 | APPROVED |
| D-AUTH-1.1 | **NOT APPROVED** |
| NEXT_TASK | **`awaiting_owner`** — phase-1.1 remediation |

## Phase-1 baseline (unchanged)

| | |
| --- | --- |
| Branch | `feature/th-402-phase1-runner-skeleton` |
| Commits | `c862331` · `f2b0744` · `740f831` — **do not rewrite** |
| Module | `:experiment:runner` |

## Paths updated this session (docs only)

### Git coordination / plan

- `coordination/DECISION_LOG.md` — OWN-P1…P4 section  
- `coordination/PROJECT_STATE.md`  
- `coordination/NEXT_TASK.md` → `awaiting_owner`  
- `coordination/HANDOFF_TO_CHATGPT.md` (this file)  
- `coordination/TH_402_PHASE1_1_REMEDIATION_PLAN.md` (**new**)

### Obsidian (meaningful sync)

- `…/00 - Dashboard/Current Status.md`  
- `…/00 - Dashboard/Open Questions.md`  
- `…/06 - Implementation Log/Implementation Plan.md`  
- `…/09 - Project Management/Decision Log.md`  
- `…/04 - System Design/Architecture Overview.md` (Runner section)  
- `…/09 - Project Management/Weekly Research Log.md`

### Explicitly NOT modified

- Any `experiment/runner/**` Java/source  
- `docs/architecture/EXPERIMENT_RUNNER_DESIGN.md` rev. 3.1  
- Taiga #37 status / SP / dependencies  
- Git commits / push / PR  

## Recommended next (Owner)

Grant **D-AUTH-1.1** with named slice: phase-1.1 remediation per `TH_402_PHASE1_1_REMEDIATION_PLAN.md` (A/B/C/E/F + merge-gate tests; G5 deferred to pre-pilot).
