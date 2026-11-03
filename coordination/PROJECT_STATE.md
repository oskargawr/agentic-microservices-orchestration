# PROJECT_STATE

**Last updated:** 2026-11-03 (post-merge G5 reconciliation / D-409)  
**Updated by:** Cursor Agent  
**MODE this session:** DOCUMENTATION (coordination only; no code; no live G5)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Runner** | TH-402 phase-1 + 1.1 + F0–F3 matrix driver + **G5 opt-in lab** on `main` |
| **TH-400 S0–S4-A/B/C** | On `main` (PR #15 / D-408) |
| **TH-400 S4-D** | **Deferred / unauthorized** |
| **TH-402 G5** | **Implementation on `main`** (PR #16 / D-409); **live execution NOT authorized**; ticket **NOT Done** |
| **G5 nature** | Pre-pilot gate (OWN-P4), **not** a merge gate; G5-2 and G5-3 are **independent** live scenarios |
| **Not authorized now** | Live G5 · S4-D · Variant C / TH-303 · Oracle/protocol changes · #35/#37 Done · EXP-* |

## Engineering

| | |
| --- | --- |
| **Branch** | `main` @ `801e343b36058094a91c5f89bcc57c945a860b0a` |
| **Last merge** | PR #16 → D-409 |
| **PR-head CI** | Run **#70** success on `b6a04233233fc233459566a1441d75b0d737f105` |
| **Post-merge CI** | Run **#71** success on `801e343` |
| **D-AUTH-G5** | Immutable (impl auth; live/merge-not-authorized at decision time) |
| **Oracle / protocol / R6/R14 / OWN-P1** | Unchanged |
| **EXP-*** | **None** |

## Next three actions

1. Owner: separate decision whether to authorize **live G5** (`THESIS_RUN_G5`, G5-2 and/or G5-3)  
2. Do **not** treat merge as live G5 evidence  
3. Do **not** mark Taiga #37 Done; do **not** authorize S4-D by implication  
