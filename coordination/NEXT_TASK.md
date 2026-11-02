# NEXT_TASK

**Status:** `approved` (implementation in progress)  
**Last updated:** 2026-11-02  
**Approved by:** Project Owner (D-208 + TH-208 B+E)

## Task ID and title

**TH-208** — Lost-response-without-returned-ID recovery (Options **B+E** only)

## Objective

Implement bounded idempotent re-POST recovery before compensation when create resource IDs may be missing after technical activity failure, plus honest terminal labeling per D-208 taxonomy.

## Locked scope

- **In:** B (recovery) + E (AMBIGUOUS / COMPENSATION_INCOMPLETE / COMPENSATED_FAILURE rules); tests T-208-1…8
- **Out:** Option C GET-by-orderId; Options D/F; TH-203/204/400; protocol/F3 definition changes
- Preserve D-201 compensation order and GET-before-mutate
- Reuse exact `IdempotencyKeys` + original payloads
- Recovery: **one** attempt per missing create id (`maximumAttempts=1`)

## Branch

`feature/th-208-lost-id-recovery-be`

## Reference

- `docs/architecture/LOST_RESPONSE_WITHOUT_ID.md`
- `coordination/DECISION_LOG.md` (D-208 **approved**)

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-02 | investigation complete | Cursor |
| 2026-11-02 | **implementation approved** (D-208) | Project Owner |
