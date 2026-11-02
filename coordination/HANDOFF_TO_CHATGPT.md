# HANDOFF_TO_CHATGPT

**Date:** 2026-11-02  
**From:** Cursor Agent  
**Task:** TH-208 lost-response-without-ID — **architecture investigation complete** (impl not approved)

---

## Deliverable

`docs/architecture/LOST_RESPONSE_WITHOUT_ID.md`

## Recommendation (PROPOSED D-208)

**B+E:** idempotent re-POST recovery (same `IdempotencyKeys`) before compensate when create ids missing + honest outcome (not false `COMPENSATED_FAILURE`). Optional later **C:** GET-by-orderId. Reject deterministic ids for MVP.

## Key finding

Temporal retry + idempotent replay **already** recovers ids in the common lost-body case. Residual gap: retry exhaustion without body → null ids → compensation skips → may still report `COMPENSATED_FAILURE` while DB holds CAPTURED/HELD.

## Constraints honored

No app code / endpoints / schema / D-201 / D-202 / protocol changes. Implementation not started.

## Owner next

Approve/reject **D-208**; or pick another NEXT_TASK. Do not treat investigation Done as impl approval.
