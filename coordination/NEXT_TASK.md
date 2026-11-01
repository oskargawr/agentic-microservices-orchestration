# NEXT_TASK

**Status:** `proposed` — awaiting Owner approval  
**Last updated:** 2026-11-01

> **TH-101 completed** on branch `feature/th-101-domain-contracts`. Do not start TH-102 until Owner sets `approved`.

## Task ID and title

**TH-102** — Inventory service (reserve / release / consume)

## Objective

Implement inventory domain persistence and HTTP APIs per `:contracts` / OpenAPI: idempotent `reserve`, `release`, `consume` (`HELD→CONSUMED` without double stock decrement), stock accounting I1/I2.

## Why it matters

First concrete service shared by A and C; unblocks payment/shipping and Temporal activities.

## Acceptance criteria (from backlog)

- Unit tests + idempotency key behavior
- Consistent with TH-011 / TH-101 contracts

## Dependencies

- TH-101 (**done** on feature branch)

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-01 | proposed | Cursor (after TH-101) |
