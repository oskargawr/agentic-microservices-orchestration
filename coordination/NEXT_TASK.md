# NEXT_TASK

**Status:** `approved`  
**Last updated:** 2026-11-02  
**Approved by:** Project Owner (**D-AUTH** — phase-1 only)

## Active task

**TH-402 phase-1** — bounded F0 × Variant A Runner skeleton

| Field | Value |
| --- | --- |
| Ticket | TH-402 · Taiga **#37** (phase-1 ≠ full Done) |
| Decisions | D-402 · D-403 · **D-AUTH approved (phase-1 exclusive)** |
| Sequence | S1 step 1 |

### In scope

- Library + CLI (no Spring Boot HTTP)
- Lifecycle: `COMPLETED` \| `EXCLUDED` \| `ABORTED_EXPERIMENT` (`TECHNICAL_FAILURE` = exclusion category)
- Serial trials + exclusive env lock
- File-first JSON + CSV ResultStore; persist-fail retry (no re-run; no fabricate COMPLETED)
- StateReset + fail-closed JDBC allowlist
- Variant A Temporal adapter (existing workflow)
- Independent Oracle exactly once after R6/R14 for eligible trials
- R6 Temporal **terminate** (not cancel); R14 quiesce → full `T_observe=30s` → Oracle
- Quiesce wait configurable (default 5000 ms)
- FaultController + **F0 no-op**

### Out of scope

TH-400 / F1-P / F2 / mandatory F3 · Variant C · TH-303 · Postgres results · Toxiproxy · confirmatory · OWN-1 · dashboard · protocol/design changes

### Phase-1 acceptance (summary)

F0×A trial runs with allowlist, lock, reset, lifecycle, R6/R14, Oracle-once, durable results, tests. **≠** full TH-402 / #37 Done.

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-02 | D-403 approved; D-AUTH open | Owner |
| 2026-11-02 | Accidental false D-AUTH corrected | Owner / Cursor |
| 2026-11-02 | **D-AUTH APPROVED — TH-402 phase-1 only** | Project Owner |
