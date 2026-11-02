# NEXT_TASK

**Status:** `approved` (implementation complete on feature branch; awaiting merge)  
**Last updated:** 2026-11-02  
**Approved by:** Project Owner (TH-401-A Independent Oracle Core)

## Task ID and title

**TH-401-A** — Independent Oracle Core (Option C)

## Objective

Implement DB-primary, read-only Independent Oracle in `experiment/oracle` per frozen TH-011 / D-011 / D-012 and Owner-approved Option C.

## Locked scope

- **In:** module `experiment/oracle`; RO JDBC snapshots; classifier; tests S1–S14 + IND-1…6
- **Out:** TH-401-B HTTP cross-check; Experiment Runner; TH-400; Variant C; LLM; Temporal dependency; protocol / D-201 / D-208 edits; new oracle labels or pattern values beyond `{SUCCESS,F0,F1,F2,NONE}`

## Classification (Option C)

1. I1–I4/I7 fail → `INVALID`
2. I5 success package → `CONFIRMED_SUCCESS` / `SUCCESS`
3. F0–F2 + I6 → `CONFIRMED_FAILURE`
4. UNKNOWN/PENDING payment → `AMBIGUOUS` / `NONE`
5. Known dangling compensation → `AMBIGUOUS` / `NONE` + reason `COMPENSATION_INCOMPLETE`
6. Other partial → `AMBIGUOUS` / `NONE`

## Branch

`feature/th-401-independent-oracle`

## Reference

- `docs/architecture/INDEPENDENT_ORACLE_DESIGN.md` (§13 audit: `OPTION_C_COMPATIBLE_WITH_FROZEN_PROTOCOL`)
- `docs/protocol/BUSINESS_INVARIANTS_AND_ORACLE.md`

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-02 | analysis TH-401 / .1 / .2 | Cursor |
| 2026-11-02 | **implementation approved** (TH-401-A) | Project Owner |
| 2026-11-02 | implementation on feature branch | Cursor |
