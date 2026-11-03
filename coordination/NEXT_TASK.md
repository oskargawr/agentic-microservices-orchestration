# NEXT_TASK

**Status:** `awaiting_owner`  
**Last updated:** 2026-11-03 (post-merge G5 reconciliation / D-409)  
**Authorized by:** — (no new implementation authorized)  
**Live G5 execution:** **NOT** authorized  
**Merge:** N/A (PR #16 already on `main`)  
**Basis:** D-409 · D-AUTH-G5 (immutable) · discovery `coordination/TH_402_G5_DISCOVERY.md`

## Current gate

TH-402 **G5 implementation** is on `main` (`801e343` / PR #16). Recommended next research milestone:

| Proposal | Content | Auth needed? |
| --- | --- | --- |
| **Live G5 execution** | Opt-in `THESIS_RUN_G5` for independent **G5-2** and/or **G5-3** (real Temporal + protocol timings) | **Yes** — separate Owner authorization (not covered by D-AUTH-G5 or D-409) |
| **TH-400 S4-D** | Live Temporal fault-profile lab (F1–F3) | **Not** proposed now; remains deferred / unauthorized |

Do **not** run live G5 or mark Taiga #37 Done until Owner approves a dedicated live-execution authorization.

## Explicit exclusions (still)

S4-D · Variant C / TH-303 · Oracle changes · protocol timing/semantics · inventing EXP-* · rewriting D-AUTH-G5 · treating G5 merge as live evidence

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-03 | **D-AUTH-G5 APPROVED** (impl only) → G5 test/lab wiring | Owner |
| 2026-11-03 | PR #16 opened / remediated → `awaiting_owner` | Cursor |
| 2026-11-03 | PR #16 **MERGED** `801e343` · D-409 recorded · NEXT = live G5 decision | Cursor / Owner merge |
