# PROJECT_STATE

**Last updated:** 2026-11-02  
**Updated by:** Cursor Agent (TH-400 S2 F2 implementation under D-AUTH-S2)  
**MODE this session:** IMPLEMENTATION

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Runner** | TH-402 phase-1 + phase-1.1 on `main` (PR #11) + **S2 F2 local** on branch |
| **TH-400 S0** | **Owner-ratified (D-404)** — on `main` via PR #12 lineage |
| **TH-400 S1 (F1-P)** | **Merged** — PR [#12](https://github.com/oskargawr/agentic-microservices-orchestration/pull/12) · merge `c732558` · head `4ed5d57` |
| **TH-400 S2 (F2)** | **Implemented locally** under **D-AUTH-S2** — branch `feature/th-400-s2-f2` · **not** on `main` · **no** push/PR |
| **TH-402 overall** | Phase-1 complete on main · ticket **NOT Done** |
| **Not authorized** | Push/PR/merge S2 · S3–S4 · Variant C · Taiga #35/#37 Done |

## Engineering

| | |
| --- | --- |
| **`origin/main`** | `c73255842d771126153dba94d6bcf1031b1d072b` |
| **S2 branch** | `feature/th-400-s2-f2` (base `c732558`) |
| **Worktree** | `worktrees/agentic-orchestration-thesis-th400-s1` |
| **Tests (mandatory modules)** | contracts + runner + shipping + variant-a: **133 passed · 0 failed · 0 skipped · 0 errors** |
| **Payment smoke** | `PaymentIntegrationTest.confirmedFailure` PASS |

## S2 on branch (summary)

- `ErrorCode.ARRANGE_REJECTED`; ShippingFault + `thesis.shipping.fault-injection-enabled`
- Arrange: idempotency-first; F2 failure row HTTP 409; `noRollbackFor(ShippingException)` so failure commits
- Orchestration: `arrangeFaultHint` forward + recovery
- Runner: `F2FaultController`, shipping preflight (409+code), adapter F2 → arrange hint; F0/F1-P unchanged
- No Oracle classifier / Payment production / F3 / Variant C changes

### Known limitations (S2)

- Same preflight forge-URL class as S1 (endpoint identity not authenticated)
- Probe arrange-reject rows remain until StateReset (no shipment on F2 success path)
- No live Temporal + Shipping F2 E2E in this session
- CLI does not exit non-zero solely on missing `CONFIRMED_FAILURE`

## Next three actions

1. Owner: authorize **push + PR** for S2 (or request audit first)  
2. After merge: separate **D-AUTH-S3** before F3  
3. Do **not** mark Taiga #35 / #37 Done  
