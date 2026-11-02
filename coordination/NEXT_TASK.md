# NEXT_TASK

**Status:** `approved` (implementation in progress on feature branch)  
**Last updated:** 2026-11-02  
**Approved by:** Project Owner (TH-202 implementation decisions D-202-1…D-202-5)

## Task ID and title

**TH-202** — Retries and Timeouts — documented and tested

## Objective

Document and test Variant A Temporal activity retry/timeout policy per Owner-approved plan. Keep ActivityOptions numerics unchanged (D-202-1). No transport wrapping (D-202-2). Defer lost-response-without-ID (D-202-3). No workflow execution timeout (D-202-4). Dedicated retries doc (D-202-5).

## Branch

`feature/th-202-retries-timeouts-documented`

## Locked constraints

- No protocol / invariants / oracle / D-201 semantic changes
- No F0–F3 harness
- Preserve `stash@{0}`; TH-201 / TH-206 remain Done / Closed
- **Not Done** until merge + post-merge verification

## Reference

- `coordination/TH-202_INVESTIGATION.md`
- `coordination/DECISION_LOG.md` (D-202)
- `docs/architecture/VARIANT_A_RETRIES_TIMEOUTS.md`

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-02 | approved (investigation first) | Project Owner |
| 2026-11-02 | investigation complete | Cursor |
| 2026-11-02 | **implementation approved** (D-202-1…5) | Project Owner |
