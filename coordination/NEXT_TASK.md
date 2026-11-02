# NEXT_TASK

**Status:** `approved`  
**Last updated:** 2026-11-02  
**Approved by:** Project Owner (explicit TH-202 approval)

## Task ID and title

**TH-202** — Retries and Timeouts — documented and tested

## Objective

Document and test Variant A Temporal activity retry/timeout policy; verify interaction with saga compensation, idempotency, and UNKNOWN payment resolution. **Do not** change D-201 semantics, protocol, invariants, or oracle without explicit Owner approval.

## Phase

**Investigation complete** (this cycle). **Implementation deferred** until Owner reviews the proposed plan in `coordination/TH-202_INVESTIGATION.md` / `HANDOFF_TO_CHATGPT.md`.

## Scope (locked for investigation)

- Inspect `ActivityOptions` / retry policies / timeouts on `main`
- Analyze retries vs business errors vs compensation vs UNKNOWN
- Propose minimal impl + tests + docs + acceptance criteria
- No application code changes in this phase
- No F0–F3 fault-injection / experiment runner work (TH-400+)
- Preserve `stash@{0}`; no unrelated console changes

## Reference

- `coordination/TH-202_INVESTIGATION.md`
- `docs/architecture/VARIANT_A_HAPPY_PATH.md`
- D-201 / D-012 / D-011 in `coordination/DECISION_LOG.md`

## Out of scope

- Reopening TH-201 / TH-206
- Protocol / invariant / oracle edits
- Arbitrary retune of retry numbers without rationale + Owner OK

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-02 | **approved** (investigation first) | Project Owner |
| 2026-11-02 | investigation complete; awaiting Owner plan review | Cursor |
