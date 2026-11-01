# NEXT_TASK

**Status:** `proposed` — awaiting Owner approval  
**Last updated:** 2026-11-01

> **TH-101.1 completed.** Do not start TH-102 until Owner sets `approved`.

## Task ID and title

**TH-102** — Inventory service (reserve / release / consume)

## Objective

Implement inventory domain persistence and HTTP APIs per hardened `:contracts` / OpenAPI (`openapi-inventory.yaml`): idempotent `reserve`, `release`, `consume` (`HELD→CONSUMED` once), stock I1/I2, `Idempotency-Key` header semantics.

## Why it matters

First concrete service shared by A and C; unblocks payment/shipping and Temporal activities.

## Acceptance criteria (from backlog)

- Unit tests + idempotency key behavior
- Consistent with TH-011 / TH-101 / TH-101.1 contracts

## Dependencies

- TH-101, TH-101.1 (**done**)

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-01 | proposed | Cursor (after TH-101) |
| 2026-11-01 | still proposed | Cursor (after TH-101.1; ready to start when approved) |
