# PROJECT_STATE

**Last updated:** 2026-11-03 (post-merge S4 reconciliation)  
**Updated by:** Cursor Agent  
**MODE this session:** ANALYSIS / DOCUMENTATION (no implementation)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Runner** | TH-402 phase-1 + 1.1 + F0–F3 **matrix driver** on `main` |
| **TH-400 S0–S4-A/B/C** | On `main` (S4 merge `5982c1a` / PR #15) |
| **TH-400 S4-D** | **Deferred / unauthorized** (live Temporal fault-profile lab) |
| **TH-400 overall** | **In Progress** (≠ Done) — harness ready; **no** EXP-* campaign |
| **TH-402 overall** | Phase-1 + 1.1 on main · G5 pre-pilot **open** · ticket **NOT Done** |
| **Not authorized now** | G5 impl · S4-D · Variant C · Oracle changes · #35/#37 Done · #68 |

## Engineering

| | |
| --- | --- |
| **Branch** | `main` @ `5982c1a0d30929cfbaaa9e1e0c45700521e961b3` |
| **Last merge** | PR #15 → D-408 |
| **Post-merge CI** | Run **#65** success on `5982c1a` (incl. `ConsoleRealTemporalE2EIT` skipped=0) |
| **D-AUTH-S4** | Immutable (impl auth; merge-not-authorized at decision time) |
| **Oracle / protocol** | Unchanged |
| **EXP-*** | **None** |

## Next three actions

1. Owner: review `coordination/TH_402_G5_DISCOVERY.md`  
2. Separate D-AUTH if G5 pre-pilot is next  
3. Do **not** mark Taiga #35/#37 Done; do **not** claim S4-D done  
