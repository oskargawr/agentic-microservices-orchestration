# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** DOCUMENTATION (TH-400 S0 final ratification sync)  
**Task:** Record formal Owner ratification of D-404 / OQ-F2-1 / OQ-F3U-1 / OQ-F3U-2  
**Branch:** `docs/th-400-s0-fault-profile-spec`  
**Primary checkout:** `feature/th-402-phase1-1-remediation` — dirty `AGENTS.md` **preserved / excluded**

---

## Ratified decisions (binding design; not impl auth)

| ID | Outcome |
| --- | --- |
| **OQ-F2-1** | `ARRANGE_REJECTED` · HTTP 409 · BUSINESS · atomic idempotent failure replay · key+diff-fp → `IDEMPOTENCY_KEY_CONFLICT` · no shipment row |
| **OQ-F3U-1** | F3-Unresolved cell → Oracle **AMBIGUOUS**; identify cell via **independent trial/profile metadata** + DB; **not** workflow/HTTP/orchestrator claims; not a global dangling-CAPTURED rule |
| **OQ-F3U-2** | HTTP **503** opacity via **header-presence** for the armed trial across **all** Temporal retries; no DB mutation; D-016 intact |

Also ratified earlier: **OD-F2 / OD-F3 / OD-FC** (unified FaultController + headers; F3 split; F2 pre-insert reject).

## Git state (at handoff authorship)

| | |
| --- | --- |
| Docs branch | `docs/th-400-s0-fault-profile-spec` |
| Worktree | `.worktrees/th400-s0` |
| Base | `origin/main` @ `90af3a9` (PR #11) |
| Push / PR / merge | **none** in this task |

## Files changed this ratification sync

- `docs/architecture/TH_400_FAULT_PROFILE_SPEC.md`  
- `coordination/DECISION_LOG.md` (D-404)  
- `coordination/PROJECT_STATE.md`  
- `coordination/NEXT_TASK.md`  
- `coordination/HANDOFF_TO_CHATGPT.md`  
- `coordination/TH_400_S0_PLAN.md`  
- (prior commits also: `TH_400_S0_TECHNICAL_VERIFICATION.md`)

**Not changed:** Java/tests · protocol · rev. 3.1 · `AGENTS.md` · Taiga

## Limitations

- Implementation of S1–S4 **not** authorized.  
- `NEXT_TASK` remains `awaiting_owner` (not `approved`).  
- Docs branch not published unless Owner separately authorizes.  
- Obsidian/Taiga may still lag GitHub docs branch until a knowledge-sync task.

## Next Owner action

1. Review final S0 diff / this handoff.  
2. Optionally authorize push/PR of the docs branch.  
3. If proceeding: issue **D-AUTH** for **S1 (F1-P)** only — arm `CHARGE_FAIL` after HELD via FaultController; do not start F2/F3 impl in the same auth unless explicitly expanded.
