# NEXT_TASK

**Status:** `approved` (implementation complete on branch — awaiting Owner audit)  
**Last updated:** 2026-11-03 (D-AUTH-S3 impl done locally)  
**Authorized by:** Owner — **D-AUTH-S3** (TH-400 S3 F3-Recovered + F3-Unresolved)  
**Push / PR / merge:** **NOT authorized**  
**Branch:** `feature/th-400-s3-f3`

## Scope (approved)

Implement TH-400 **S3** per Owner D-AUTH-S3-1…5 and `coordination/TH_400_S3_READINESS.md`:

| Slice | Content | SP |
| --- | --- | --- |
| S3-A | Payment `RESOLVE_TRANSPORT_OPAQUE` + IT | 2 |
| S3-B | Variant A `getPayment` hint + CompIT | 2 |
| S3-C | Runner F3 controllers / adapter / CLI | 3 |
| S3-D | Tests, harness gate, docs, regressions | 2 |
| **Total mandatory** | | **9** |
| S3-E | Optional live Temporal E2E lab | 1 (optional; not claimed unless done) |

## Binding Owner decisions (D-AUTH-S3)

1. Dual profiles `F3-Recovered` + `F3-Unresolved` (no Recovered-only final scope).  
2. Oracle API/classifier **unchanged**; harness acceptance gate for Unresolved.  
3. Header-presence `RESOLVE_TRANSPORT_OPAQUE` → HTTP 503; no DB mutation; no shared counter.  
4. Propagate fault hint on resolve **and** fallback GET.  
5. Compensation safety: no unhinted GET defeating experimental opacity; preserve D-201; leave CAPTURED+HELD for Unresolved cell.

## Out of scope

Variant C / TH-303 · Oracle changes · protocol semantic rewrite · Java cleanup (#68) · push/PR/merge · Taiga #35/#37 Done

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-03 | S3 readiness discovery; `awaiting_owner` | Cursor |
| 2026-11-03 | **D-AUTH-S3 APPROVED** → `approved` | Owner |
