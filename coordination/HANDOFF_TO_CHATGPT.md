# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** RESEARCH / DOCUMENTATION (TH-400 S0 Owner OQ follow-up)  
**Task:** Incorporate OQ-F2-1 / OQ-F3U-1 / OQ-F3U-2; verify retry budget, isolation, F2 atomicity  
**Branch:** `docs/th-400-s0-fault-profile-spec`  
**Primary checkout:** left on prior branch; local `AGENTS.md` dirty **excluded**

---

## Owner decisions incorporated

| ID | Decision |
| --- | --- |
| OQ-F2-1 | `ARRANGE_REJECTED` · HTTP 409 · BUSINESS · idempotent failure replay · key+diff-fp → `IDEMPOTENCY_KEY_CONFLICT` |
| OQ-F3U-1 | F3-Unresolved cell → Oracle **AMBIGUOUS** (cell-specific; no new global rule; four labels unchanged) |
| OQ-F3U-2 | Opacity = HTTP **503** |

## Technical verification (summary)

1. **`resolveAttemptLimit`** = logical loop only (default 3); each activity up to **5** Temporal attempts; 503 is retryable → naive N=3 HTTP budget is unsafe.  
2. **Normative:** header-presence opacity for the trial (or explicit keyed counter covering the product); no un-hinted `getPayment` escape.  
3. **Isolation:** per-trial headers + `trialId`/`orderId` in `effectiveConfig` + serial lock; no new production API.  
4. **F2:** single TX = no shipment + failure idempotency row + op-log; replay after disarm.

## Artifacts

- `docs/architecture/TH_400_FAULT_PROFILE_SPEC.md` (revised)  
- `docs/architecture/TH_400_S0_TECHNICAL_VERIFICATION.md` (new)  
- D-404 updated · PROJECT_STATE / NEXT_TASK / this handoff  

## S0 readiness

**Ready for final Owner ratification.**  
**Not** ready to implement (OQ-AUTH / D-AUTH still required).  
No Java/tests changed. No push/PR/merge. Taiga untouched.
