# NEXT_TASK

**Status:** `awaiting_owner`  
**Last updated:** 2026-11-02  
**Awaiting:** Owner review of **S1 (F1-P)** local implementation; optional separate auth for push/PR

## Important

- **D-AUTH-S1 was GRANTED** and S1 was implemented on `feature/th-400-s1-f1p`.  
- **Push / PR / merge remain unauthorized.**  
- **Do not start S2–S4** until Owner issues a new D-AUTH.  
- This file is **not** `approved` for further implementation slices.

## Completed (S1 local)

- F1-P FaultController + Composite routing  
- Fail-closed Payment injection preflight (existing charge API; CHARGE_FAIL → FAILED)  
- VariantAAdapter `chargeFaultHint` wiring  
- CLI `--fault-profile=F1-P` + `--payment-base-url`  
- Runner README + unit/lifecycle tests  
- Mandatory regression commands (see handoff)

## Candidate next (not approved)

| Candidate | Notes |
| --- | --- |
| Push/PR S1 branch | Only with separate Owner publication auth |
| **S2 — F2 shipping** | Needs separate D-AUTH; `ARRANGE_REJECTED` etc. |
| Docs push/PR for S0 | Separate from S1 |

## Out of scope until separate auth

S2 F2 · S3 F3 · S4 unify · Variant C · phase-2 · Taiga Done marks

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-02 | PR #11 merged | Owner / Cursor |
| 2026-11-02 | S0 Owner-ratified (D-404) | Owner / Cursor |
| 2026-11-02 | D-AUTH-S1 granted; S1 implemented locally; awaiting_owner for publication / S2 | Owner / Cursor |
