# NEXT_TASK

**Status:** `approved`  
**Last updated:** 2026-11-02  
**Approved by:** Project Owner (**D-AUTH-1.1** — phase-1.1 remediation only)

## Active task

**TH-402 phase-1.1** — findings remediation (A/B/C/E/F + merge-gate tests)

| Field | Value |
| --- | --- |
| Ticket | TH-402 · Taiga **#37** (phase-1.1 ≠ full Done) |
| Decisions | D-402 · D-403 · D-AUTH · **OWN-P1–P4** · **D-AUTH-1.1 APPROVED** |
| Plan | `coordination/TH_402_PHASE1_1_REMEDIATION_PLAN.md` |
| Branch | `feature/th-402-phase1-1-remediation` (from phase-1 HEAD `740f831`) |

### In scope

- A connection-metadata gate + G1/G2  
- C terminate race + G4  
- B OWN-P1 persist + G8  
- F idempotent CSV  
- G3 lock IT  
- E OWN-P3 experiment abort  
- Merge gates G1–G4, G6–G8  

### Out of scope

G5 live Temporal (pre-pilot) · TH-400 · phase-2 · Variant C · design rewrite · #37 Done · push/PR/merge  

### Preserve

Phase-1 commits `c862331` · `f2b0744` · `740f831` unchanged.

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-02 | D-AUTH APPROVED — phase-1 | Owner |
| 2026-11-02 | OWN-P1–P4 APPROVED; NEXT_TASK awaiting_owner phase-1.1 | Owner |
| 2026-11-02 | **D-AUTH-1.1 APPROVED — phase-1.1 remediation** | Project Owner |
