# NEXT_TASK

**Status:** `approved`  
**Last updated:** 2026-11-02  
**Approved by:** Project Owner (explicit TH-206 approval)

## Task ID and title

**TH-206** — Temporal E2E in CI — no silent skip

## Objective

Ensure `ConsoleRealTemporalE2EIT` **executes** in CI with fail-hard behavior when Temporal/PostgreSQL are unavailable or the test would be skipped/disabled. Preserve local optional-infra `@EnabledIf` for non-CI development. Do **not** duplicate Compose startup in CI.

## Scope (locked)

- Fail hard in CI if Temporal or Postgres missing
- Fail hard in CI if the required E2E test is skipped/disabled
- Keep inventory/payment/shipping local optional-Postgres behavior
- No protocol / invariants / oracle / application behavior changes
- No `stash@{0}` apply/drop; no console UI/CORS

## Reference

`coordination/TH-206_PROPOSAL.md`

## Out of scope

- Expanding TH-205 console features
- TH-201 reopen
- Marking TH-206 Done before merge + post-merge verification

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-01 | proposed | Cursor |
| 2026-11-02 | **approved** | Project Owner |
