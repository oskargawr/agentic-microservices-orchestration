# PROJECT_STATE

**Last updated:** 2026-11-03 (G5-3 round-trip fix implemented; live re-run NOT authorized)  
**Updated by:** Cursor Agent  
**MODE this session:** IMPLEMENTATION (TrialRecord `ArmedFaultRecord` typing; non-live tests PASS)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **TH-402 G5** | Impl on `main` (PR #16/#17). **G5-2 live PASS**. **G5-3 live ran; acceptance INVALID** (historical evidence retained) |
| **G5-3 root cause** | `TrialRecord.faultArmedRecord` was `Object` → Jackson read as `LinkedHashMap` → `Objects.equals(measured, durable)` false despite intact JSON |
| **Fix (local, uncommitted)** | Type field as `ArmedFaultRecord`; regression tests added; `assertUnchanged` unchanged |
| **Not authorized** | Live G5 re-run · commit/push/merge · EXP-* · #37 Done · S4-D · Variant C |

## Engineering

| | |
| --- | --- |
| **Branch** | `main` @ `55a84fbe9b795ce3137b4a6831800d35f21670e0` (+ local fix WT) |
| **G5-2 / G5-3 evidence** | Untouched under `$HOME/thesis-evidence/g5/` |
| **EXP-*** | **None** |
| **Taiga #37** | **≠ Done** |

## Next three actions

1. Owner: review fix / authorize commit+PR  
2. Owner: separate auth for **G5-3 live re-run** after merge  
3. Do not mark #37 Done; do not invent EXP-*  
