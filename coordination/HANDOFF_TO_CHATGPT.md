# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** RESEARCH / DOCUMENTATION (TH-400 S0)  
**Task:** Owner-approved specification lock for fault profiles (no code)  
**Branch:** `docs/th-400-s0-fault-profile-spec` (from `origin/main` @ `90af3a9`)  
**Worktree:** `.worktrees/th400-s0` (primary checkout left on prior branch with local `AGENTS.md` dirty — **not** included)

---

## Context

PR #11 merged TH-402 phase-1 + phase-1.1 into `main`. Owner accepted three TH-400 design directions and authorized **S0 documentation only**.

## Delivered

| Artifact | Path |
| --- | --- |
| Normative S0 spec | `docs/architecture/TH_400_FAULT_PROFILE_SPEC.md` |
| Plan pointer | `coordination/TH_400_S0_PLAN.md` |
| Decision | **D-404** in `coordination/DECISION_LOG.md` |
| State / next / handoff | `PROJECT_STATE.md` · `NEXT_TASK.md` · this file |

## Accepted directions (D-404)

1. **F2:** business reject on shipping `arrange` before insert; no shipment row; compensate refund+release.  
2. **F3:** **F3-Recovered** + **F3-Unresolved**.  
3. **Unified FaultController** + headers; A/C identical profiles.

## Key technical bindings (for review)

- **F2:** HTTP **409**, non-retryable BUSINESS (not 503); idempotent **failure replay** so post-disarm retries cannot create a shipment; ErrorCode name **Proposed** (`BUSINESS_REJECTED` / `ARRANGE_REJECTED`) → **OQ-F2-1**.  
- **F3-Recovered:** existing `F3_CAPTURE_LOST_RESPONSE` + normal resolve → SUCCESS / CONFIRMED_SUCCESS.  
- **F3-Unresolved:** D-016 charge unchanged + **trial-scoped resolve/GET transport opacity** (Proposed `RESOLVE_TRANSPORT_OPAQUE`); DB stays CAPTURED for Oracle JDBC; does **not** fake DB or redefine successful GET/resolve. Oracle label for CAPTURED+HELD terminal → **OQ-F3U-1** (AMBIGUOUS vs INVALID) — **blocks full F3-Unresolved acceptance**.  
- **F1-P:** D-R5 preserved; arm `CHARGE_FAIL` after HELD.  
- Protocol constants / Oracle label **set** / rev. **3.1** unchanged. **No Java.**

## Confirmations

- No implementation code or tests modified.  
- No push/PR/merge in this task.  
- Taiga #35/#37 not marked Done.  
- `AGENTS.md` unrelated local edit excluded.

## Next (Owner)

1. Review S0 spec; decide **OQ-F2-1**, **OQ-F3U-1**, optional **OQ-F3U-2**.  
2. Separate auth for push/PR of docs branch if desired.  
3. Separate **D-AUTH** before S1 (recommended first impl slice: F1-P).
